package com.shipstream.shipment;

import com.shipstream.common.NotFoundException;
import com.shipstream.hub.Hub;
import com.shipstream.hub.HubRepository;
import com.shipstream.outbox.OutboxWriter;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final HubRepository hubs;
    private final ShipmentEventRepository events;
    private final OutboxWriter outbox;
    private final TrackingIdGenerator idGenerator;
    private final EtaService eta;
    private final ObjectMapper objectMapper;

    ShipmentService(ShipmentRepository shipments, HubRepository hubs, ShipmentEventRepository events,
            OutboxWriter outbox, TrackingIdGenerator idGenerator, EtaService eta, ObjectMapper objectMapper) {
        this.shipments = shipments;
        this.hubs = hubs;
        this.events = events;
        this.outbox = outbox;
        this.idGenerator = idGenerator;
        this.eta = eta;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Shipment create(ShipmentDtos.CreateShipmentRequest req) {
        Hub origin = hub(req.originHubId());
        Hub destination = hub(req.destinationHubId());
        // tracking_id is UNIQUE; a (vanishingly unlikely) collision surfaces as 409 rather than an overwrite
        Shipment shipment = shipments.save(
                new Shipment(idGenerator.next(), req.senderAddress(), req.receiverAddress(), origin, destination));
        recordEvent(shipment, "SHIPMENT_CREATED", Map.of("status", shipment.getStatus()));
        return shipment;
    }

    @Cacheable(value = "shipments", key = "#id")
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
     * an audit event + outbox event in the same transaction. Relies on the entity's {@code @Version}
     * column: two concurrent transitions on the same shipment race on the UPDATE's
     * {@code WHERE version = ?} and the loser gets an {@code ObjectOptimisticLockingFailureException}
     * (mapped to 409 by the client).
     *
     * <p>Entering {@code IN_TRANSIT} (including resuming from {@code DELAYED}) refreshes the ETA off
     * the shipment's current leg; reaching a terminal status clears it.
     */
    @CacheEvict(value = "shipments", key = "#id")
    @Transactional
    public Shipment transition(Long id, ShipmentDtos.StatusUpdateRequest req) {
        Shipment shipment = get(id);
        ShipmentStatus from = shipment.getStatus();
        shipment.transitionTo(req.status());
        if (req.hubId() != null) {
            shipment.moveToHub(hub(req.hubId()));
        }
        refreshEta(shipment);
        recordEvent(shipment, "STATUS_CHANGED", Map.of("from", from, "to", req.status()));
        return shipment;
    }

    private void refreshEta(Shipment shipment) {
        if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
            shipment.setEstimatedDeliveryAt(
                    eta.estimate(shipment.getCurrentHub(), shipment.getDestinationHub()).orElse(null));
        } else if (shipment.getStatus().isTerminal()) {
            shipment.setEstimatedDeliveryAt(null);
        }
    }

    private Hub hub(Long id) {
        return id == null ? null
                : hubs.findById(id).orElseThrow(() -> new NotFoundException("Hub " + id + " not found"));
    }

    private void recordEvent(Shipment shipment, String type, Map<String, Object> payload) {
        try {
            events.save(new ShipmentEvent(shipment.getId(), type, objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shipment event payload", e);
        }
        outbox.write(type, withRouting(shipment, type, payload));
    }

    private Map<String, Object> withRouting(Shipment shipment, String type, Map<String, Object> payload) {
        return Map.of(
                "eventType", type,
                "shipmentId", shipment.getId(),
                "trackingId", shipment.getTrackingId(),
                "data", payload);
    }
}
