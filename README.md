# Banking Wallet Platform

A production-grade digital wallet and payment platform built with Spring Boot 3.x, Java 21, and a microservices architecture. The system implements double-entry bookkeeping, saga-based distributed transactions, and a full observability stack. Designed as a deep-dive learning project covering modular monolith → microservices evolution, event-driven architecture, and operational excellence.

📄 **[Product Specification →](docs/product.md)**
📋 **[Build Checklist →](docs/todo.md)**

## Architecture

| Service | Responsibility |
|---------|---------------|
| `identity-service` | Users, credentials, KYC, JWT issuance & refresh |
| `transaction-service` | Saga orchestrator for transfers, deposits, withdrawals |
| `wallet-service` | Per-account balances, holds, and settlements |
| `ledger-service` | Append-only double-entry bookkeeping (source of truth) |
| `notification-service` | Email & push notifications via Kafka consumers |
| `reconciliation-service` | Nightly batch reconciliation (Spring Batch) |
| `audit-service` | Full event audit trail with Elasticsearch |

## Tech Stack

- **Runtime:** Java 21 (virtual threads), Spring Boot 3.4.x
- **Data:** PostgreSQL 16, Redis 7, Kafka (Redpanda)
- **Observability:** Prometheus, Grafana, Tempo, Loki, OpenTelemetry
- **Build:** Gradle (Kotlin DSL), Spotless, Testcontainers

## Quick Start

```bash
# Start local infrastructure: Postgres, Redis, Redpanda, Grafana, Prometheus, etc.
./scripts/dev-up.sh

# Build and test everything
./gradlew build

# Run the wallet monolith against the local Docker stack
./gradlew :apps:wallet-monolith:bootRun --args='--spring.profiles.active=local'

# Stop infrastructure
./scripts/dev-down.sh
```

The application runs on:

```text
http://localhost:8081
```

The `local` profile automatically loads demo seed data from:

```text
apps/wallet-monolith/src/main/resources/db/seed/local-dev-seed.sql
```

Disable local seeding with:

```bash
./gradlew :apps:wallet-monolith:bootRun --args='--spring.profiles.active=local --wallet.seed.enabled=false'
```

Useful local URLs:

| Tool | URL |
|------|-----|
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |
| Metrics | http://localhost:8081/actuator/metrics |
| Prometheus scrape endpoint | http://localhost:8081/actuator/prometheus |
| Grafana | http://localhost:3000 (`admin`/`admin`) |
| Prometheus | http://localhost:9090 |
| Mailhog | http://localhost:8025 |
| pgAdmin | http://localhost:5050 (`admin@wallet.dev`/`admin`) |
| Redpanda Console | http://localhost:8080 |

## API Surface

Public endpoints:

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /.well-known/jwks.json
GET  /v3/api-docs
GET  /swagger-ui.html
GET  /actuator/health
```

Authenticated endpoints:

```text
POST /kyc/submit
GET  /api/wallets/{id}
GET  /api/wallets/{id}/holds/active
POST /api/transfers
POST /api/deposits
POST /api/withdrawals
```

For authenticated endpoints, send:

```text
Authorization: Bearer <accessToken>
```

For money-moving endpoints, also send:

```text
Idempotency-Key: <unique-request-id>
```

## Manual API Testing

The local seed creates these demo users. All use password `password123`.

| User | KYC status |
|------|------------|
| `alice@wallet.local` | `VERIFIED` |
| `bob@wallet.local` | `VERIFIED` |
| `charlie@wallet.local` | `PENDING` |

Login as Alice and capture tokens:

```bash
curl -s -X POST http://localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@wallet.local","password":"password123"}'
```

Set the access token returned by the previous call:

```bash
ACCESS_TOKEN='paste-access-token-here'
REFRESH_TOKEN='paste-refresh-token-here'
```

Register a new user if you want to test registration:

```bash
curl -s -X POST http://localhost:8081/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@wallet.com","password":"password123"}'
```

Refresh tokens:

```bash
curl -s -X POST http://localhost:8081/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Logout by revoking the current refresh token:

```bash
curl -i -X POST http://localhost:8081/auth/logout \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Submit KYC:

```bash
curl -s -X POST http://localhost:8081/kyc/submit \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Seeded Wallets For Manual Money Movement

The app currently has read APIs for wallets, but no public wallet creation API. The local seed creates these wallets and matching ledger accounts automatically:

```text
Alice USD source wallet:      10000000-0000-0000-0000-000000000001
Bob USD destination wallet:   10000000-0000-0000-0000-000000000002
Charlie INR wallet:           10000000-0000-0000-0000-000000000003
```

Read a wallet:

```bash
curl -s http://localhost:8081/api/wallets/10000000-0000-0000-0000-000000000001 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Create a transfer:

```bash
curl -s -X POST http://localhost:8081/api/transfers \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{
    "fromWalletId":"10000000-0000-0000-0000-000000000001",
    "toWalletId":"10000000-0000-0000-0000-000000000002",
    "amount":25,
    "currency":"USD"
  }'
```

Create a deposit:

```bash
curl -s -X POST http://localhost:8081/api/deposits \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{
    "toWalletId":"10000000-0000-0000-0000-000000000002",
    "amount":50,
    "currency":"USD"
  }'
```

Create a withdrawal:

```bash
curl -s -X POST http://localhost:8081/api/withdrawals \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{
    "fromWalletId":"10000000-0000-0000-0000-000000000001",
    "amount":10,
    "currency":"USD"
  }'
```

Check active holds:

```bash
curl -s http://localhost:8081/api/wallets/10000000-0000-0000-0000-000000000001/holds/active \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Inspect persisted data:

```bash
docker exec -it bw-postgres psql -U wallet_admin -d wallet_dev
```

Useful SQL while inside `psql`:

```sql
SELECT id, user_id, currency, total_balance, available_balance FROM wallet.wallets;
SELECT id, type, state, amount, currency FROM transaction.transactions ORDER BY created_at DESC;
SELECT transaction_id, account_id, amount, currency FROM ledger.journal_entries ORDER BY posted_at DESC;
SELECT key_id, response_status, created_at FROM shared.idempotency_keys ORDER BY created_at DESC;
SELECT event_type, aggregate_id, published_at, attempts FROM shared.outbox_events ORDER BY created_at DESC;
```

## Automated Testing

Run all tests:

```bash
./gradlew test
```

Run the full build:

```bash
./gradlew build
```

Run only the monolith tests:

```bash
./gradlew :apps:wallet-monolith:test
```

Run only the money library tests:

```bash
./gradlew :libs:money:test
```

Run one test class:

```bash
./gradlew :apps:wallet-monolith:test --tests 'com.wallet.identity.AuthIntegrationTest'
```

The monolith test profile uses Testcontainers with PostgreSQL. Keep Docker running before starting integration tests.

### Component Test Matrix

| Component | What to run |
|-----------|-------------|
| Public API contract and OpenAPI | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.contract.PublicApiContractTest'` |
| Identity, login, JWT, refresh tokens, KYC | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.identity.AuthIntegrationTest'` |
| Idempotency filter and replay behavior | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.shared.idempotency.IdempotencyIntegrationTest'` |
| Transfer happy path | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.TransferIntegrationTest'` |
| Transfer compensation paths | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.TransferCompensationTest'` |
| Deposit saga | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.DepositIntegrationTest'` |
| Withdrawal saga | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.WithdrawalIntegrationTest'` |
| Request validation | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.RequestValidationTest'` |
| Transaction state machine | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.transaction.domain.TransactionStateMachineTest'` |
| Wallet invariants | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.wallet.WalletServiceInvariantTest'` |
| Wallet concurrency | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.wallet.WalletConcurrencyTest'` |
| Wallet repositories | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.wallet.repository.*'` |
| Ledger invariants and fuzzing | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.ledger.LedgerFuzzTest'` |
| Ledger repositories | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.ledger.repository.*'` |
| Error mapping / ProblemDetail responses | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.shared.config.ExceptionMappingTest'` |
| Money value object | `./gradlew :libs:money:test --tests 'com.wallet.shared.money.MoneyTest'` |
| Money JSON serialization | `./gradlew :libs:money:test --tests 'com.wallet.shared.money.MoneyJsonTest'` |
| Money property tests | `./gradlew :libs:money:test --tests 'com.wallet.shared.money.MoneyPropertyTest'` |
| Architecture boundaries | `./gradlew :apps:wallet-monolith:test --tests 'com.wallet.ArchitectureTest'` |

Optional mutation testing for selected wallet and ledger classes:

```bash
./gradlew :apps:wallet-monolith:pitest
```

## Project Structure

```
apps/       # Spring Boot services
libs/       # Shared libraries (money, events, idempotency)
infra/      # Docker Compose, Grafana dashboards, configs
docs/       # Product spec, ADRs, documentation
scripts/    # Dev scripts, seed data
tests/      # Manual test collections
```

## License

Private — learning project.
