package com.logitrust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton row of fraud-scoring weights and thresholds (SRS 6.2:
 * "configurable weights stored in the database, not hardcoded, so
 * thresholds can be tuned without redeploying"). Always addressed by
 * {@link #SINGLETON_ID}, seeded in V4__fraud_scoring.sql.
 */
@Entity
@Table(name = "scoring_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoringConfig {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "route_deviation_weight", nullable = false)
    private double routeDeviationWeight;

    @Column(name = "timing_anomaly_weight", nullable = false)
    private double timingAnomalyWeight;

    @Column(name = "condition_breach_weight", nullable = false)
    private double conditionBreachWeight;

    @Column(name = "quantity_mismatch_weight", nullable = false)
    private double quantityMismatchWeight;

    @Column(name = "identity_reuse_weight", nullable = false)
    private double identityReuseWeight;

    /** Weight for the Hugging Face zero-shot risk score on free-text incident notes. */
    @Column(name = "incident_text_risk_weight", nullable = false)
    private double incidentTextRiskWeight;

    @Column(name = "flag_threshold", nullable = false)
    private int flagThreshold;

    @Column(name = "freeze_threshold", nullable = false)
    private int freezeThreshold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
