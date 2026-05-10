# 3. Database Schema Strategy

Date: 2026-05-10

## Status

Accepted

## Context

The banking wallet has multiple bounded contexts (identity, wallet, ledger, transaction). In Phase 1, all contexts live in a single Spring Boot application. In Phase 2, each context is extracted into its own service.

We must choose how to partition data storage so that:
1. Contexts are isolated from the start (no accidental cross-context joins).
2. Phase 2 extraction is mechanical, not a data migration.
3. Local development is simple (one Postgres container).

Options considered:
- **A: Single database, single schema** — simplest, but no isolation.
- **B: Single database, schema-per-context** — isolation via Postgres schemas, single connection pool.
- **C: Separate databases per context** — strongest isolation, but multiple connection pools in Phase 1.

## Decision

**Phase 1: Option B — single database (`wallet_dev`), one Postgres schema per bounded context.**

Schemas: `identity`, `wallet`, `ledger`, `transaction`, `shared`.

Flyway manages migrations per schema using a prefix convention:
- `V1__` through `V9__` for shared concerns
- `V10__` through `V19__` for identity
- `V20__` through `V29__` for ledger
- `V30__` through `V39__` for wallet
- `V40__` through `V49__` for transaction

All migrations run from a single Flyway instance in the monolith.

**Phase 2:** Each schema migrates to its own logical database (already created by `seed-postgres.sql`: `identity_db`, `wallet_db`, `ledger_db`, `transaction_db`). Each service gets its own Flyway instance and connection pool.

## Consequences

- **Positive:** ArchUnit enforces package boundaries in code; schema boundaries enforce them at the data level. Together, they guarantee no cross-context coupling.
- **Positive:** Phase 2 migration is mechanical: move the schema's tables into the pre-created database, point the new service's datasource at it.
- **Negative:** Single connection pool in Phase 1 means one misbehaving context could exhaust connections. Acceptable for a monolith; Phase 2 resolves this.
- **Negative:** Flyway migration numbering convention requires discipline. Mitigated by CI checks.
