package com.shipstream.shipment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    List<ShipmentEvent> findByShipmentIdOrderByIdAsc(Long shipmentId);
}
