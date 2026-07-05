package com.logitrust.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Shipment lifecycle states (FR-2.2). The legal-transition map lives with
 * the enum so there is exactly one place that defines the state machine;
 * services consult {@link #canTransitionTo} and never mutate status
 * directly.
 */
public enum ShipmentStatus {
    CREATED,
    ASSIGNED,
    IN_TRANSIT,
    AT_CHECKPOINT,
    DELIVERED,
    DISPUTED;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> LEGAL_TRANSITIONS = Map.of(
            CREATED, EnumSet.of(ASSIGNED),
            ASSIGNED, EnumSet.of(IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(AT_CHECKPOINT, DELIVERED, DISPUTED),
            AT_CHECKPOINT, EnumSet.of(IN_TRANSIT, DELIVERED, DISPUTED),
            DELIVERED, EnumSet.of(DISPUTED),
            DISPUTED, EnumSet.noneOf(ShipmentStatus.class));

    public boolean canTransitionTo(ShipmentStatus target) {
        return LEGAL_TRANSITIONS.get(this).contains(target);
    }

    public Set<ShipmentStatus> legalTargets() {
        return LEGAL_TRANSITIONS.get(this);
    }
}
