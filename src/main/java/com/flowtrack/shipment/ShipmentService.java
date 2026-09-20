package com.flowtrack.shipment;

import com.flowtrack.common.NotFoundException;
import com.flowtrack.hub.Hub;
import com.flowtrack.hub.HubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final HubRepository hubs;
    private final TrackingIdGenerator idGenerator;

    ShipmentService(ShipmentRepository shipments, HubRepository hubs, TrackingIdGenerator idGenerator) {
        this.shipments = shipments;
        this.hubs = hubs;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Shipment create(ShipmentDtos.CreateShipmentRequest req) {
        Hub origin = req.originHubId() == null ? null : hubs.findById(req.originHubId())
                .orElseThrow(() -> new NotFoundException("Hub " + req.originHubId() + " not found"));
        // tracking_id is UNIQUE; a (vanishingly unlikely) collision surfaces as 409 rather than an overwrite
        return shipments.save(new Shipment(idGenerator.next(), req.senderAddress(), req.receiverAddress(), origin));
    }

    @Transactional(readOnly = true)
    public Shipment get(Long id) {
        return shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Shipment getByTrackingId(String trackingId) {
        return shipments.findByTrackingId(trackingId)
                .orElseThrow(() -> new NotFoundException("Shipment " + trackingId + " not found"));
    }
}
