-- V7__create_outbox_events.sql
-- Transactional outbox for Phase 1. The poller only logs rows locally; Phase 2 publishes them.

CREATE TABLE shared.outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_events_unpublished
    ON shared.outbox_events(published_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_events_aggregate
    ON shared.outbox_events(aggregate_type, aggregate_id, created_at);
