package com.shipstream.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShipmentStatusTest {

    private static final Set<ShipmentStatus> NON_TERMINAL =
            EnumSet.complementOf(EnumSet.of(ShipmentStatus.DELIVERED));

    @ParameterizedTest
    @EnumSource(ShipmentStatus.class)
    void everyNonTerminalStatusHasAtLeastOneValidNextStep(ShipmentStatus status) {
        boolean hasNext = java.util.Arrays.stream(ShipmentStatus.values()).anyMatch(status::canTransitionTo);
        assertThat(hasNext).isEqualTo(NON_TERMINAL.contains(status));
    }

    @ParameterizedTest
    @EnumSource(ShipmentStatus.class)
    void noStatusTransitionsToItself(ShipmentStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void happyPathIsFullyConnected() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.PICKED_UP)).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.ARRIVED_AT_HUB)).isTrue();
        assertThat(ShipmentStatus.ARRIVED_AT_HUB.canTransitionTo(ShipmentStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void allowsMultiLegLoopBetweenInTransitAndArrivedAtHub() {
        assertThat(ShipmentStatus.ARRIVED_AT_HUB.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void blocksSkippingStages() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void deliveredIsTerminal() {
        assertThat(ShipmentStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.CREATED)).isFalse();
    }
}
