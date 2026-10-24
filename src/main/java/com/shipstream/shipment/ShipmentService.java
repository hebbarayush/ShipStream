package com.shipstream.shipment;

import com.shipstream.common.NotFoundException;
import com.shipstream.hub.Hub;
import com.shipstream.hub.HubRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final HubRepository hubs;
    private final ShipmentEventRepository events;
    private final TrackingIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    ShipmentService(ShipmentRepository shipments, HubRepository hubs, ShipmentEventRepository events,
            TrackingIdGenerator idGenerator, ObjectMapper objectMapper) {
        this.shipments = shipments;
        this.hubs = hubs;
        this.events = events;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Shipment create(ShipmentDtos.CreateShipmentRequest req) {
        Hub origin = req.originHubId() == null ? null : hubs.findById(req.originHubId())
                .orElseThrow(() -> new NotFoundException("Hub " + req.originHubId() + " not found"));
        // tracking_id is UNIQUE; a (vanishingly unlikely) collision surfaces as 409 rather than an overwrite
        Shipment shipment = shipments.save(new Shipment(idGenerator.next(), req.senderAddress(), req.receiverAddress(), origin));
        recordEvent(shipment, "SHIPMENT_CREATED", Map.of("status", shipment.getStatus()));
        return shipment;
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

    /**
     * Validates and applies a status transition, moving the shipment to a hub if given, and records
     * an audit event in the same transaction. Relies on the entity's {@code @Version} column: two
     * concurrent transitions on the same shipment race on the UPDATE's {@code WHERE version = ?} and
     * the loser gets an {@code ObjectOptimisticLockingFailureException} (mapped to 409 by the client).
     */
    @Transactional
    public Shipment transition(Long id, ShipmentDtos.StatusUpdateRequest req) {
        Shipment shipment = get(id);
        ShipmentStatus from = shipment.getStatus();
        shipment.transitionTo(req.status());
        if (req.hubId() != null) {
            Hub hub = hubs.findById(req.hubId())
                    .orElseThrow(() -> new NotFoundException("Hub " + req.hubId() + " not found"));
            shipment.moveToHub(hub);
        }
        recordEvent(shipment, "STATUS_CHANGED", Map.of("from", from, "to", req.status()));
        return shipment;
    }

    private void recordEvent(Shipment shipment, String type, Map<String, Object> payload) {
        try {
            events.save(new ShipmentEvent(shipment.getId(), type, objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shipment event payload", e);
        }
    }
}
