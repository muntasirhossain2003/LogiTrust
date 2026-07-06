package com.logitrust.dto;

import com.logitrust.domain.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Courier checkpoint log (FR-2.4): status target plus the location and
 * optional condition reading at that checkpoint. Target must be IN_TRANSIT
 * or AT_CHECKPOINT; the service enforces it. incidentNote is free text (e.g.
 * "box was wet, seal looked broken") fed into the fraud engine's Hugging
 * Face risk classifier as a sixth scoring factor. scannedSerials feeds the
 * identity-reuse factor.
 */
public record TransitUpdateRequest(
        @NotNull ShipmentStatus status,
        @NotBlank String location,
        ConditionData conditionData,
        String incidentNote,
        List<String> scannedSerials) {
}
