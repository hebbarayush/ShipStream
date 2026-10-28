-- Hub coordinates: needed to compute ETA (route distance) and to simulate GPS movement between hubs.
ALTER TABLE hubs ADD COLUMN latitude DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE hubs ADD COLUMN longitude DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE hubs ALTER COLUMN latitude DROP DEFAULT;
ALTER TABLE hubs ALTER COLUMN longitude DROP DEFAULT;

-- A shipment now tracks where it's going, not just where it is, so ETA/GPS simulation has a target.
ALTER TABLE shipments ADD COLUMN destination_hub_id BIGINT REFERENCES hubs (id);
ALTER TABLE shipments ADD COLUMN estimated_delivery_at TIMESTAMPTZ;

-- New terminal/exception states: DELAYED (recoverable), LOST and CANCELLED (terminal).
ALTER TABLE shipments DROP CONSTRAINT chk_shipment_status;
ALTER TABLE shipments ADD CONSTRAINT chk_shipment_status CHECK (status IN
    ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED_AT_HUB', 'OUT_FOR_DELIVERY', 'DELIVERED',
     'DELAYED', 'LOST', 'CANCELLED'));

CREATE INDEX idx_shipments_eta ON shipments (estimated_delivery_at) WHERE estimated_delivery_at IS NOT NULL;
