CREATE TABLE hubs (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    location   VARCHAR(255) NOT NULL,
    capacity   INT          NOT NULL CHECK (capacity > 0)
);

CREATE TABLE routes (
    id              BIGSERIAL PRIMARY KEY,
    source_hub_id   BIGINT NOT NULL REFERENCES hubs (id),
    destination_hub_id BIGINT NOT NULL REFERENCES hubs (id),
    distance_km     INT    NOT NULL CHECK (distance_km > 0),
    CONSTRAINT uq_route UNIQUE (source_hub_id, destination_hub_id),
    CONSTRAINT chk_route_distinct CHECK (source_hub_id <> destination_hub_id)
);

CREATE TABLE shipments (
    id               BIGSERIAL PRIMARY KEY,
    tracking_id      VARCHAR(32) NOT NULL UNIQUE,
    sender_address   TEXT        NOT NULL,
    receiver_address TEXT        NOT NULL,
    status           VARCHAR(32) NOT NULL,
    current_hub_id   BIGINT REFERENCES hubs (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_shipment_status CHECK (status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED_AT_HUB', 'OUT_FOR_DELIVERY', 'DELIVERED'))
);

CREATE INDEX idx_shipments_status ON shipments (status);
CREATE INDEX idx_shipments_current_hub ON shipments (current_hub_id);
