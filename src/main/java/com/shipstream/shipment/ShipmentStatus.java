package com.shipstream.shipment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Shipment lifecycle. {@link #CREATED} is the only entry point; {@link #DELIVERED} is terminal.
 * IN_TRANSIT and ARRIVED_AT_HUB may loop (multi-leg journeys through several hubs) before the
 * shipment is finally handed to OUT_FOR_DELIVERY.
 */
public enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVED_AT_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(PICKED_UP),
            PICKED_UP, EnumSet.of(IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(ARRIVED_AT_HUB),
            ARRIVED_AT_HUB, EnumSet.of(IN_TRANSIT, OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(ShipmentStatus.class));

    public boolean canTransitionTo(ShipmentStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
