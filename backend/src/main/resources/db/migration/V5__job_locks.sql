CREATE TABLE job_locks (
    job_name     VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMPTZ NOT NULL,
    locked_by    VARCHAR(128) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
