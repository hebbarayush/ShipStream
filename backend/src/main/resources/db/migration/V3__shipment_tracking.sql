CREATE TABLE shipment_tracking (
    shipment_id  BIGINT PRIMARY KEY REFERENCES shipments (id),
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now()
);
