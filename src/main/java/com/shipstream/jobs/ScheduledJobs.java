package com.shipstream.jobs;

import com.shipstream.outbox.OutboxEventRepository;
import com.shipstream.outbox.OutboxStatus;
import com.shipstream.shipment.ShipmentDtos.StatusUpdateRequest;
import com.shipstream.shipment.ShipmentRepository;
import com.shipstream.shipment.ShipmentService;
import com.shipstream.shipment.ShipmentStatus;
import com.shipstream.tracking.ShipmentTrackingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background jobs, each wrapped so only one instance across the fleet runs it at a time
 * ({@link JobLockService}).
 */
@Component
class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private static final EnumSet<ShipmentStatus> OVERDUE_CANDIDATE_STATUSES =
            EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.ARRIVED_AT_HUB, ShipmentStatus.OUT_FOR_DELIVERY);

    private final JobLockService locks;
    private final ShipmentTrackingRepository tracking;
    private final OutboxEventRepository outbox;
    private final ShipmentRepository shipments;
    private final ShipmentService shipmentService;

    ScheduledJobs(JobLockService locks, ShipmentTrackingRepository tracking, OutboxEventRepository outbox,
            ShipmentRepository shipments, ShipmentService shipmentService) {
        this.locks = locks;
        this.tracking = tracking;
        this.outbox = outbox;
        this.shipments = shipments;
        this.shipmentService = shipmentService;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    void cleanupStaleTracking() {
        runWithLock("cleanup-stale-tracking", Duration.ofMinutes(2), () -> {
            Instant cutoff = Instant.now().minus(Duration.ofHours(1));
            long removed = tracking.deleteByLastUpdatedBefore(cutoff);
            if (removed > 0) {
                log.info("Removed {} stale tracking row(s)", removed);
            }
        });
    }

    @Scheduled(fixedDelay = 120_000)
    void reportDeadLetteredOutboxEvents() {
        runWithLock("outbox-dead-letter-report", Duration.ofMinutes(2), () -> {
            long failed = outbox.countByStatus(OutboxStatus.FAILED);
            if (failed > 0) {
                log.warn("{} outbox event(s) permanently failed and need attention", failed);
            }
        });
    }

    /** Drops any shipment still moving past its ETA into DELAYED. Resuming progress clears it again. */
    @Scheduled(fixedDelay = 30_000)
    void detectDelayedShipments() {
        runWithLock("detect-delayed-shipments", Duration.ofMinutes(2), () -> {
            var overdue = shipments.findByStatusInAndEstimatedDeliveryAtBefore(
                    OVERDUE_CANDIDATE_STATUSES, Instant.now());
            for (var shipment : overdue) {
                try {
                    shipmentService.transition(shipment.getId(), new StatusUpdateRequest(ShipmentStatus.DELAYED, null));
                } catch (Exception e) {
                    // Another instance/request may have already moved this shipment on; skip and retry next tick.
                    log.debug("Could not mark shipment {} DELAYED this tick", shipment.getId(), e);
                }
            }
            if (!overdue.isEmpty()) {
                log.info("Marked {} overdue shipment(s) DELAYED", overdue.size());
            }
        });
    }

    private void runWithLock(String jobName, Duration lease, Runnable job) {
        if (!locks.tryAcquire(jobName, lease)) {
            log.debug("Skipping '{}' - lease held by another instance", jobName);
            return;
        }
        try {
            job.run();
        } finally {
            locks.release(jobName);
        }
    }
}
