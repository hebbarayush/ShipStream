package com.shipstream.tracking;

import com.shipstream.common.NotFoundException;
import com.shipstream.outbox.OutboxWriter;
import com.shipstream.shipment.Shipment;
import com.shipstream.shipment.ShipmentRepository;
import com.shipstream.tracking.TrackingDtos.UpdateLocationRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {

    private final ShipmentTrackingRepository tracking;
    private final ShipmentRepository shipments;
    private final OutboxWriter outbox;

    TrackingService(ShipmentTrackingRepository tracking, ShipmentRepository shipments, OutboxWriter outbox) {
        this.tracking = tracking;
        this.shipments = shipments;
        this.outbox = outbox;
    }

    @Transactional
    public ShipmentTracking updateLocation(UpdateLocationRequest req) {
        Shipment shipment = shipments.findById(req.shipmentId())
                .orElseThrow(() -> new NotFoundException("Shipment " + req.shipmentId() + " not found"));

        ShipmentTracking updated = tracking.findById(req.shipmentId())
                .map(existing -> {
                    existing.update(req.latitude(), req.longitude());
                    return existing;
                })
                .orElseGet(() -> tracking.save(
                        new ShipmentTracking(req.shipmentId(), req.latitude(), req.longitude())));

        outbox.write("LOCATION_UPDATED", Map.of(
                "eventType", "LOCATION_UPDATED",
                "shipmentId", shipment.getId(),
                "trackingId", shipment.getTrackingId(),
                "data", Map.of("latitude", req.latitude(), "longitude", req.longitude())));

        return updated;
    }

    @Transactional(readOnly = true)
    public ShipmentTracking get(Long shipmentId) {
        return tracking.findById(shipmentId)
                .orElseThrow(() -> new NotFoundException("No tracking data for shipment " + shipmentId));
    }
}
