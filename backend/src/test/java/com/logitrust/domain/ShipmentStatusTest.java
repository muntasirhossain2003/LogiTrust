package com.logitrust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShipmentStatusTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.ASSIGNED)).isTrue();
        assertThat(ShipmentStatus.ASSIGNED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.AT_CHECKPOINT)).isTrue();
        assertThat(ShipmentStatus.AT_CHECKPOINT.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.AT_CHECKPOINT.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @Test
    void deliveredCanOnlyBeDisputed() {
        assertThat(ShipmentStatus.DELIVERED.legalTargets())
                .containsExactly(ShipmentStatus.DISPUTED);
    }

    @Test
    void srsExampleIllegalTransition_deliveredToInTransit_isRejected() {
        // FR-2.3 names this exact case.
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
    }

    @Test
    void disputedIsTerminal() {
        assertThat(ShipmentStatus.DISPUTED.legalTargets()).isEmpty();
    }

    @Test
    void noStateSkipsStraightFromCreatedToTransitOrDelivery() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
        assertThat(ShipmentStatus.ASSIGNED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
    }
}
