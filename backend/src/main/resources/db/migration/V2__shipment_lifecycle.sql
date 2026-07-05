CREATE TABLE shipments (
    id                   UUID PRIMARY KEY,
    manufacturer_id      UUID         NOT NULL REFERENCES users(id),
    current_courier_id   UUID         REFERENCES users(id),
    destination_party_id UUID         REFERENCES users(id),
    tracking_code        VARCHAR(20)  NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    origin_label         VARCHAR(255) NOT NULL,
    destination_label    VARCHAR(255) NOT NULL,
    risk_score           INT          NOT NULL DEFAULT 0,
    frozen               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_shipments_tracking_code UNIQUE (tracking_code)
);

CREATE TABLE shipment_items (
    id               UUID PRIMARY KEY,
    shipment_id      UUID         NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    serial_number    VARCHAR(255) NOT NULL,
    qr_code          VARCHAR(255) NOT NULL,
    product_name     VARCHAR(255) NOT NULL,
    product_category VARCHAR(32)  NOT NULL,
    CONSTRAINT uq_shipment_items_serial UNIQUE (serial_number),
    CONSTRAINT uq_shipment_items_qr UNIQUE (qr_code)
);

CREATE INDEX idx_shipments_manufacturer ON shipments(manufacturer_id);
CREATE INDEX idx_shipments_status ON shipments(status);
CREATE INDEX idx_shipment_items_shipment ON shipment_items(shipment_id);
