package com.shipstream.shipment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByTrackingId(String trackingId);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    /** Used by the delay-detection job: shipments still moving whose ETA has already passed. */
    List<Shipment> findByStatusInAndEstimatedDeliveryAtBefore(Collection<ShipmentStatus> statuses, Instant cutoff);

    /**
     * Used by the GPS simulator, outside any transaction of its own: eagerly fetches the (otherwise
     * lazy) hub associations so {@code currentHub}/{@code destinationHub} are safe to read on a
     * detached entity.
     */
    @EntityGraph(attributePaths = {"currentHub", "destinationHub"})
    List<Shipment> findByStatus(ShipmentStatus status);
}
