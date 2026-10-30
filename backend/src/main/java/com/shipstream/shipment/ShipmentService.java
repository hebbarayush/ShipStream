package com.shipstream.shipment;

import com.shipstream.common.NotFoundException;
import com.shipstream.hub.Hub;
import com.shipstream.hub.HubRepository;
import com.shipstream.outbox.OutboxWriter;
import com.shipstream.tracking.ShipmentTrackingRepository;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final HubRepository hubs;
    private final ShipmentEventRepository events;
    private final ShipmentTrackingRepository tracking;
    private final OutboxWriter outbox;
    private final TrackingIdGenerator idGenerator;
    private final EtaService eta;
    private final ObjectMapper objectMapper;

    ShipmentService(ShipmentRepository shipments, HubRepository hubs, ShipmentEventRepository events,
            ShipmentTrackingRepository tracking, OutboxWriter outbox, TrackingIdGenerator idGenerator,
            EtaService eta, ObjectMapper objectMapper) {
        this.shipments = shipments;
        this.hubs = hubs;
        this.events = events;
        this.tracking = tracking;
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

    /**
     * Same as {@link #create}, but with a caller-supplied tracking ID instead of a generated one, and
     * idempotent (returns the existing row rather than erroring if that ID is already seeded). Exists
     * only for {@code DemoDataSeeder}: real shipments always get a system-generated ID.
     */
    @Transactional
    public Shipment createSeedShipment(String fixedTrackingId, ShipmentDtos.CreateShipmentRequest req) {
        return shipments.findByTrackingId(fixedTrackingId).orElseGet(() -> {
            Hub origin = hub(req.originHubId());
            Hub destination = hub(req.destinationHubId());
            Shipment shipment = shipments.save(
                    new Shipment(fixedTrackingId, req.senderAddress(), req.receiverAddress(), origin, destination));
            recordEvent(shipment, "SHIPMENT_CREATED", Map.of("status", shipment.getStatus()));
            return shipment;
        });
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

    @Transactional(readOnly = true)
    public Page<Shipment> list(ShipmentStatus status, Pageable pageable) {
        return status == null ? shipments.findAll(pageable) : shipments.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public List<ShipmentEvent> events(Long shipmentId) {
        get(shipmentId); // 404s if the shipment doesn't exist, rather than returning an empty list
        return events.findByShipmentIdOrderByIdAsc(shipmentId);
    }

    /**
     * Admin cleanup, not a domain operation: a real carrier never hard-deletes a shipment record, it
     * cancels one ({@link ShipmentStatus#CANCELLED}) and keeps the history. This exists purely so test/
     * demo clutter - a hub stuck because some throwaway shipment still references it, say - can
     * actually be cleared out from the ops UI, hence why it lives behind the same OPS-login-only
     * surface as everything else rather than being exposed as a "cancel" alternative.
     */
    @CacheEvict(value = "shipments", key = "#id")
    @Transactional
    public void deleteShipment(Long id) {
        get(id); // 404s if it doesn't exist
        tracking.deleteByShipmentId(id);
        events.deleteByShipmentId(id);
        shipments.deleteById(id);
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
