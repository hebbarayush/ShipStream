CREATE TABLE shipment_events (
    id          BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT      NOT NULL REFERENCES shipments (id),
    event_type  VARCHAR(64) NOT NULL,
    payload     JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shipment_events_shipment ON shipment_events (shipment_id);
