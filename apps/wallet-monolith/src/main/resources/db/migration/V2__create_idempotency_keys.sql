-- V2__create_idempotency_keys.sql
-- Idempotency keys are stored in the shared schema.

CREATE TABLE shared.idempotency_keys (
    key_id VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT,
    response_body JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for the TTL purge job
CREATE INDEX idx_idempotency_keys_created_at ON shared.idempotency_keys(created_at);
