package com.logitrust.dto;

import com.logitrust.domain.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

/** Target must be IN_TRANSIT or AT_CHECKPOINT; the service enforces it. */
public record TransitUpdateRequest(@NotNull ShipmentStatus status) {
}
