package com.shipstream.jobs;

import com.shipstream.outbox.OutboxEventRepository;
import com.shipstream.outbox.OutboxStatus;
import com.shipstream.tracking.ShipmentTrackingRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background jobs, each wrapped so only one instance across the fleet runs it at a time
 * ({@link JobLockService}). Real "delay detection" needs the DELAYED status (Phase 8); for now these
 * demonstrate the coordination pattern with housekeeping that's useful regardless.
 */
@Component
class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final JobLockService locks;
    private final ShipmentTrackingRepository tracking;
    private final OutboxEventRepository outbox;

    ScheduledJobs(JobLockService locks, ShipmentTrackingRepository tracking, OutboxEventRepository outbox) {
        this.locks = locks;
        this.tracking = tracking;
        this.outbox = outbox;
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
