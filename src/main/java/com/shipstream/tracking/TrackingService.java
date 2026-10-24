package com.shipstream.tracking;

import com.shipstream.common.NotFoundException;
import com.shipstream.shipment.ShipmentRepository;
import com.shipstream.tracking.TrackingDtos.UpdateLocationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {

    private final ShipmentTrackingRepository tracking;
    private final ShipmentRepository shipments;

    TrackingService(ShipmentTrackingRepository tracking, ShipmentRepository shipments) {
        this.tracking = tracking;
        this.shipments = shipments;
    }

    @Transactional
    public ShipmentTracking updateLocation(UpdateLocationRequest req) {
        if (!shipments.existsById(req.shipmentId())) {
            throw new NotFoundException("Shipment " + req.shipmentId() + " not found");
        }
        return tracking.findById(req.shipmentId())
                .map(existing -> {
                    existing.update(req.latitude(), req.longitude());
                    return existing;
                })
                .orElseGet(() -> tracking.save(
                        new ShipmentTracking(req.shipmentId(), req.latitude(), req.longitude())));
    }

    @Transactional(readOnly = true)
    public ShipmentTracking get(Long shipmentId) {
        return tracking.findById(shipmentId)
                .orElseThrow(() -> new NotFoundException("No tracking data for shipment " + shipmentId));
    }
}
