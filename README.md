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
# Start local infrastructure
./scripts/dev-up.sh

# Build the project
./gradlew build

# Stop infrastructure
./scripts/dev-down.sh
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
