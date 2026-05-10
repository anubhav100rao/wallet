Each service is its own Spring Boot app with its own Postgres schema (database-per-service, no cross-service joins). What each owns and which Spring surface it exercises:

`identity-service` — users, credentials, KYC state, JWT issuance and refresh. Teaches Spring Security, OAuth2 resource server, password hashing, JWT signing keys, refresh-token rotation. Stores users + roles; emits `user.registered`, `user.kyc_verified`.

`transaction-service` — the saga orchestrator. Exposes `POST /transfers`, `POST /deposits`, `POST /withdrawals` and owns the saga state machine (`PENDING → HELD → POSTED → SETTLED`, or compensated). Persists a transaction record with idempotency key. Teaches state machines (Spring Statemachine optional), idempotency, the saga pattern, distributed transactions without 2PC. Owns no money — it only coordinates.

`wallet-service` — per-account balances and holds. Two numbers per wallet: `total_balance` and `available_balance` (total minus active holds). Mutating ops are `placeHold`, `releaseHold`, `capture`. Teaches `@Transactional`, isolation levels (you'll want `READ_COMMITTED` with `SELECT … FOR UPDATE` or optimistic locking via `@Version`), the difference between optimistic and pessimistic when contention is real. Wallet is a *cache* of the ledger — the ledger is truth.

`ledger-service` — append-only double-entry bookkeeping. Every business event writes ≥2 entries that sum to zero across debit/credit columns; balance per account is `SUM(entries)`. Once written, never updated. Teaches immutability as a design tool, the outbox pattern (every ledger write also inserts into `outbox_events` in the same Postgres transaction; a Kafka publisher tails it), why "the database is the queue" works.

`notification-service` — pure Kafka consumer. Listens on transfer/wallet topics, sends email via Mailhog locally / SES in prod, push via FCM. Teaches Spring Kafka consumers, retry topics, dead-letter handling, `@KafkaListener` containers, manual ack vs auto.

`reconciliation-service` — Spring Batch. Nightly job that walks ledger entries for the day, sums per-account, compares against wallet balances, writes a reconciliation report, alerts on drift. Teaches `Job`, `Step`, `ItemReader`/`ItemWriter`, chunk-oriented processing, restartability.

`audit-service` — fan-out consumer of every domain event. Writes to Postgres + indexes into Elasticsearch for search. Teaches consumer groups, schema evolution (Avro/Protobuf with a schema registry), compliance use cases.

## Canonical flow: $100 transfer from Alice to Bob

1. Client POSTs `/transfers {from, to, amount, idempotency_key}` through the gateway.
2. `transaction-service` checks `idempotency_keys` table. Miss → insert `transactions` row in `PENDING`, write `transfer.requested` to its outbox (single Postgres tx — atomic).
3. Outbox poller publishes to Kafka. `wallet-service` consumes `transfer.requested`, takes a Redis lock on the source wallet (ShedLock or Redisson), runs `placeHold` in a Postgres tx with `SELECT … FOR UPDATE` on the source row, emits `wallet.hold_placed`.
4. `transaction-service` consumes `wallet.hold_placed`, advances saga to `HELD`, emits `ledger.post_requested` carrying the entries.
5. `ledger-service` writes the journal: `(Alice cash, -100), (Bob cash, +100)` in one tx with the outbox row. Emits `ledger.posted`.
6. `transaction-service` consumes `ledger.posted`, asks wallet to `capture` the hold (debit source, credit destination), advances to `POSTED`.
7. `notification-service` and `audit-service` consume the chain independently.

If any step fails: emit a compensating event (`hold.released`, `transfer.failed`), saga transitions to `COMPENSATED`. The ledger is never rolled back — you post a *reversing* entry. That's the discipline accountants enforce on themselves and it's the whole reason double-entry works in distributed systems.

## Cross-cutting decisions worth getting right early

**Money types.** Never `double` or `float`. `BigDecimal` with explicit scale, or store minor units as `BIGINT` (paise/cents) and never let a `Double` near it. Wrap in a value object — `Money(amount, currency)` — with `add`/`subtract`/`negate` and currency mismatch as a hard exception.

**Idempotency.** Every mutating endpoint takes an `Idempotency-Key` header; persist `(key, request_hash, response)` and replay on duplicate. This is non-negotiable for transfers — clients retry, gateways double-deliver.

**Outbox pattern over direct Kafka publishing.** Direct `kafkaTemplate.send()` from inside a Postgres tx will eventually lose messages (commit succeeds, broker call fails) or double-send (broker succeeds, commit fails). Outbox table + poller (Debezium CDC for fancy mode, scheduled poller for simple mode) is the only correct pattern.

**Service-to-service auth.** Internal services validate the user's JWT (passed through), plus a service-identity token via mTLS or a separate service token. Don't run open services on a "trusted network" — the network is never trusted.

**Distributed locks via Redis** (Redisson) for cross-service coordination, not as a primary correctness mechanism. Postgres row locks remain the source of truth; Redis locks are about reducing wasted work, and you must handle the case where the lock expires before the work completes.

**Schema-per-service, no shared DB.** Tempting to let `reconciliation-service` read `ledger-service`'s tables directly — don't. Use a read-replica, materialized view exposed as an API, or replicate via Kafka. The ban on cross-service joins is what forces you to actually understand event-driven design.

## Tech stack

`Spring Boot 3.x` (Java 21, virtual threads), `Spring Cloud Gateway`, `Spring Cloud Config`, Eureka or Consul, `Spring Security` + `oauth2-resource-server`, `Spring Data JPA` + Hibernate, `Spring Kafka`, `Spring Batch`, `Spring Statemachine` (optional), Resilience4j, ShedLock or Redisson, Flyway for migrations, Testcontainers for integration tests (Postgres + Kafka + Redis spun up real). Postgres 16, Kafka (Confluent or Redpanda locally), Redis 7. Observability: Micrometer → Prometheus, Grafana, Tempo for tracing (OpenTelemetry), Loki for logs. Docker Compose for local, then Kubernetes manifests once you want to add HPA, ConfigMaps, and learn Helm.