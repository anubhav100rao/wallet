# ADR-0009: Outbox Poller vs Debezium CDC

Status: Accepted

## Context

Domain events must be persisted atomically with the state changes that produce them. Publishing
directly to Kafka inside a database transaction creates a dual-write failure mode: the database can
commit while the broker write fails, or the broker write can succeed while the database rolls back.

Phase 1 has no Kafka broker in the business flow, but it should still practice the outbox discipline
so Phase 2 can replace local logging with real publishing.

## Decision

Use a `shared.outbox_events` table in Phase 1 and write outbox rows from
`@TransactionalEventListener(phase = BEFORE_COMMIT)` handlers. A scheduled in-process poller reads
unpublished rows, logs them, and marks them published.

In Phase 2, the same table shape can be retained while the poller publishes to Kafka. In Phase 4,
Debezium CDC may replace the poller if lower latency and operational realism are worth the added
Kafka Connect and replication-slot complexity.

## Consequences

- Event rows are committed in the same database transaction as the state transition.
- The Phase 1 implementation is simple to run locally and easy to inspect in Postgres.
- Polling has higher latency than CDC and needs careful batching/locking once multiple app
  instances publish concurrently.
- Consumers must remain idempotent because the system still provides at-least-once delivery, not
  magic exactly-once effects.
