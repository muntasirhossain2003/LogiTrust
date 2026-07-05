package com.logitrust.dto;

import com.logitrust.domain.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Courier checkpoint log (FR-2.4): status target plus the location and
 * optional condition reading at that checkpoint. Target must be IN_TRANSIT
 * or AT_CHECKPOINT; the service enforces it.
 */
public record TransitUpdateRequest(
        @NotNull ShipmentStatus status,
        @NotBlank String location,
        ConditionData conditionData) {
}
