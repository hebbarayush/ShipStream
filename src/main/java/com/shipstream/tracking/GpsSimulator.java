package com.shipstream.tracking;

import com.shipstream.hub.Hub;
import com.shipstream.shipment.Shipment;
import com.shipstream.shipment.ShipmentRepository;
import com.shipstream.shipment.ShipmentStatus;
import com.shipstream.tracking.TrackingDtos.UpdateLocationRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stands in for an agent's phone GPS: for every shipment currently IN_TRANSIT with a known origin,
 * destination and ETA, nudges its tracked position a little further along the straight line between
 * the two hubs each tick. Linear and straight-line on purpose (no road network) - this is a demo feed
 * for the real-time pipeline (outbox -> NOTIFY -> WebSocket), not a routing engine.
 *
 * <p>Progress is approximated from {@code updatedAt} (set whenever the shipment last transitioned,
 * including into IN_TRANSIT) to {@code estimatedDeliveryAt}: good enough to produce a believable
 * moving dot without a dedicated "leg started at" column.
 */
@Component
class GpsSimulator {

    private static final Logger log = LoggerFactory.getLogger(GpsSimulator.class);

    private final ShipmentRepository shipments;
    private final TrackingService tracking;

    GpsSimulator(ShipmentRepository shipments, TrackingService tracking) {
        this.shipments = shipments;
        this.tracking = tracking;
    }

    // Each repository call and tracking.updateLocation below manages its own transaction; the loop
    // itself doesn't need one since there's no cross-shipment atomicity to preserve.
    @Scheduled(fixedDelay = 5000)
    void tick() {
        Instant now = Instant.now();
        for (Shipment shipment : shipments.findByStatus(ShipmentStatus.IN_TRANSIT)) {
            Hub from = shipment.getCurrentHub();
            Hub to = shipment.getDestinationHub();
            Instant eta = shipment.getEstimatedDeliveryAt();
            if (from == null || to == null || eta == null) {
                continue; // nothing to interpolate between (e.g. no direct route - see EtaService)
            }
            double fraction = progress(shipment.getUpdatedAt(), eta, now);
            double lat = from.getLatitude() + (to.getLatitude() - from.getLatitude()) * fraction;
            double lon = from.getLongitude() + (to.getLongitude() - from.getLongitude()) * fraction;
            try {
                tracking.updateLocation(new UpdateLocationRequest(shipment.getId(), lat, lon));
            } catch (Exception e) {
                log.warn("GPS simulation tick failed for shipment {}", shipment.getId(), e);
            }
        }
    }

    private static double progress(Instant start, Instant eta, Instant now) {
        long total = eta.toEpochMilli() - start.toEpochMilli();
        if (total <= 0) {
            return 1.0; // already overdue; sit at the destination until the delay job catches it
        }
        double fraction = (now.toEpochMilli() - start.toEpochMilli()) / (double) total;
        return Math.max(0.0, Math.min(1.0, fraction));
    }
}
