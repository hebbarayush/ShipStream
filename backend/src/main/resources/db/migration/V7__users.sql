-- Single role today (OPS); the column exists so adding ADMIN/DISPATCHER later is a data change,
-- not a schema change.
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'OPS',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
