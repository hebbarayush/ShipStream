package com.shipstream.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShipmentStatusTest {

    private static final Set<ShipmentStatus> NON_TERMINAL = EnumSet.complementOf(
            EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.LOST, ShipmentStatus.CANCELLED));

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
    void deliveredLostAndCancelledAreTerminal() {
        assertThat(ShipmentStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(ShipmentStatus.LOST.isTerminal()).isTrue();
        assertThat(ShipmentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.CREATED)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void cancellationOnlyBeforePickup() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.canTransitionTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.CANCELLED)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void lostIsReachableOnceInTransitButNotBeforePickup() {
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.LOST)).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canTransitionTo(ShipmentStatus.LOST)).isTrue();
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.LOST)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void delayedResumesTheFlowItStalledFrom() {
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELAYED)).isTrue();
        assertThat(ShipmentStatus.DELAYED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.DELAYED.canTransitionTo(ShipmentStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ShipmentStatus.DELAYED.canTransitionTo(ShipmentStatus.CANCELLED)).isFalse();
    }
}
