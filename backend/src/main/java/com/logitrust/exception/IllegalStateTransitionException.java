package com.logitrust.exception;

import com.logitrust.domain.ShipmentStatus;

/** Rejected server-side per FR-2.3; maps to HTTP 409. */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(ShipmentStatus from, ShipmentStatus to) {
        super("Illegal shipment state transition " + from + " -> " + to
                + ". Legal targets from " + from + ": " + from.legalTargets());
    }

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
