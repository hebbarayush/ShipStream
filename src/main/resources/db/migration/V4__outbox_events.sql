CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    retry_count  INT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Partial index: the processor only ever scans PENDING rows.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
