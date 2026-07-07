package com.logitrust.dto;

import com.logitrust.domain.FraudFlagStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin queue view of a fraud flag with its full explainable breakdown (FR-4.5, FR-7.1). */
public record FraudFlagResponse(
        UUID id,
        UUID shipmentId,
        String trackingCode,
        UUID custodyRecordId,
        int score,
        List<ScoringFactorResult> factors,
        FraudFlagStatus status,
        Instant createdAt) {
}
