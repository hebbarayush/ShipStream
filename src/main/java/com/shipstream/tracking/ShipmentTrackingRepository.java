package com.shipstream.tracking;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentTrackingRepository extends JpaRepository<ShipmentTracking, Long> {
    long deleteByLastUpdatedBefore(Instant cutoff);
}
