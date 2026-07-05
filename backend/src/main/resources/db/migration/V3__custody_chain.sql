CREATE TABLE custody_records (
    id                          UUID         PRIMARY KEY,
    shipment_id                 UUID         NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    sequence_number             INT          NOT NULL,
    from_party_id               UUID         REFERENCES users(id),
    to_party_id                 UUID         NOT NULL REFERENCES users(id),
    location                    VARCHAR(255) NOT NULL,
    event_type                  VARCHAR(32)  NOT NULL,
    condition_data_ciphertext   TEXT,
    previous_record_hash        VARCHAR(64)  NOT NULL,
    record_hash                 VARCHAR(64)  NOT NULL,
    timestamp                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_custody_records_shipment_sequence UNIQUE (shipment_id, sequence_number)
);

CREATE INDEX idx_custody_records_shipment ON custody_records(shipment_id);
