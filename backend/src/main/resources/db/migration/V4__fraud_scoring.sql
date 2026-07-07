ALTER TABLE shipments ADD COLUMN expected_route_json TEXT;
ALTER TABLE custody_records ADD COLUMN incident_note TEXT;

CREATE TABLE route_baselines (
    id                   UUID              PRIMARY KEY,
    origin_label         VARCHAR(255)      NOT NULL,
    destination_label    VARCHAR(255)      NOT NULL,
    sample_count         INT               NOT NULL DEFAULT 0,
    mean_transit_seconds DOUBLE PRECISION  NOT NULL DEFAULT 0,
    m2                   DOUBLE PRECISION  NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT uq_route_baselines_route UNIQUE (origin_label, destination_label)
);

CREATE TABLE scoring_config (
    id                        UUID              PRIMARY KEY,
    route_deviation_weight   DOUBLE PRECISION  NOT NULL,
    timing_anomaly_weight    DOUBLE PRECISION  NOT NULL,
    condition_breach_weight  DOUBLE PRECISION  NOT NULL,
    quantity_mismatch_weight DOUBLE PRECISION  NOT NULL,
    identity_reuse_weight    DOUBLE PRECISION  NOT NULL,
    incident_text_risk_weight DOUBLE PRECISION NOT NULL,
    flag_threshold            INT               NOT NULL,
    freeze_threshold          INT               NOT NULL,
    updated_at                TIMESTAMPTZ       NOT NULL DEFAULT now()
);

-- Weights sum to 1.0 so a 0-100 factor score maps directly to a 0-100 final
-- score. Tunable later via this row without a redeploy (SRS 6.2).
INSERT INTO scoring_config (
    id, route_deviation_weight, timing_anomaly_weight, condition_breach_weight,
    quantity_mismatch_weight, identity_reuse_weight, incident_text_risk_weight,
    flag_threshold, freeze_threshold
) VALUES (
    '00000000-0000-0000-0000-000000000001', 0.18, 0.18, 0.25, 0.13, 0.13, 0.13, 50, 80
);

CREATE TABLE fraud_flags (
    id                  UUID         PRIMARY KEY,
    shipment_id         UUID         NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    custody_record_id   UUID         REFERENCES custody_records(id),
    score               INT          NOT NULL,
    factors             TEXT         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolved_by_admin_id UUID        REFERENCES users(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_flags_shipment ON fraud_flags(shipment_id);
CREATE INDEX idx_fraud_flags_status ON fraud_flags(status);
