package com.shipstream.shipment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Shipment lifecycle. {@link #CREATED} is the only entry point; {@link #DELIVERED}, {@link #LOST} and
 * {@link #CANCELLED} are terminal. IN_TRANSIT and ARRIVED_AT_HUB may loop (multi-leg journeys through
 * several hubs) before the shipment is finally handed to OUT_FOR_DELIVERY.
 *
 * <p>{@link #DELAYED} is recoverable: the delay-detection job (Phase 8) drops an overdue shipment into
 * it, and normal progress out of it resumes the flow from wherever it stalled. {@link #LOST} is reachable
 * from any state once the shipment has left the building, as an ops action; {@link #CANCELLED} only
 * before pickup, since you can't un-ship something already moving.
 */
public enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVED_AT_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELAYED,
    LOST,
    CANCELLED;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(PICKED_UP, CANCELLED),
            PICKED_UP, EnumSet.of(IN_TRANSIT, CANCELLED),
            IN_TRANSIT, EnumSet.of(ARRIVED_AT_HUB, DELAYED, LOST),
            ARRIVED_AT_HUB, EnumSet.of(IN_TRANSIT, OUT_FOR_DELIVERY, DELAYED, LOST),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, DELAYED, LOST),
            DELAYED, EnumSet.of(IN_TRANSIT, ARRIVED_AT_HUB, OUT_FOR_DELIVERY, LOST),
            DELIVERED, EnumSet.noneOf(ShipmentStatus.class),
            LOST, EnumSet.noneOf(ShipmentStatus.class),
            CANCELLED, EnumSet.noneOf(ShipmentStatus.class));

    public boolean canTransitionTo(ShipmentStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
