package com.shipstream.tracking;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShipmentTrackingRepository extends JpaRepository<ShipmentTracking, Long> {

    /**
     * Only for shipments that are actually done (terminal status) - an active shipment's tracking row
     * can go quiet for a long time (e.g. the whole app was offline) without that meaning it should be
     * garbage collected. Deleting it anyway raced GpsSimulator writing a fresh position for the same
     * still-IN_TRANSIT shipment: a benign, self-healing StaleStateException (caught, logged, the next
     * tick just re-inserts), but scoping the delete this way removes the race entirely rather than
     * just tolerating it.
     */
    @Modifying
    @Query(value = """
            DELETE FROM shipment_tracking t
            USING shipments s
            WHERE t.shipment_id = s.id
              AND t.last_updated < :cutoff
              AND s.status IN ('DELIVERED', 'LOST', 'CANCELLED')
            """, nativeQuery = true)
    int deleteStaleForFinishedShipments(Instant cutoff);

    /** Used when deleting a shipment outright: its live-position row goes with it. */
    long deleteByShipmentId(Long shipmentId);
}
