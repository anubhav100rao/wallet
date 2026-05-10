# Infrastructure — Local Development Stack

The `infra/` directory contains the complete local development environment for the banking wallet platform, managed via Docker Compose.

## Quick Start

```bash
# Start everything
./scripts/dev-up.sh

# Stop (preserve data)
./scripts/dev-down.sh

# Stop and wipe all data
./scripts/dev-down.sh --clean
```

## Services & Ports

| Service | Container | Port(s) | URL / Connection |
|---------|-----------|---------|------------------|
| **PostgreSQL 16** | `bw-postgres` | `5432` | `postgresql://wallet_admin:wallet_secret@localhost:5432/wallet_dev` |
| **Redis 7** | `bw-redis` | `6379` | `redis://localhost:6379` |
| **Redpanda** (Kafka) | `bw-redpanda` | `19092` (Kafka), `18081` (Schema Registry), `9644` (Admin) | `localhost:19092` |
| **Mailhog** | `bw-mailhog` | `1025` (SMTP), `8025` (Web UI) | http://localhost:8025 |
| **Prometheus** | `bw-prometheus` | `9090` | http://localhost:9090 |
| **Grafana** | `bw-grafana` | `3000` | http://localhost:3000 (`admin`/`admin`) |
| **Tempo** | `bw-tempo` | `3200` (HTTP), `4317` (OTLP gRPC), `4318` (OTLP HTTP) | http://localhost:3200 |
| **Loki** | `bw-loki` | `3100` | http://localhost:3100 |
| **OTEL Collector** | `bw-otel-collector` | `14317` (gRPC), `14318` (HTTP) | `localhost:14317` |
| **pgAdmin** | `bw-pgadmin` | `5050` | http://localhost:5050 (`admin@wallet.local`/`admin`) |
| **Redpanda Console** | `bw-redpanda-console` | `8080` | http://localhost:8080 |

## Databases

The seed script (`scripts/seed-postgres.sql`) creates separate databases for each planned service:

| Database | Service |
|----------|---------|
| `wallet_dev` | Default / Phase 1 monolith |
| `identity_db` | Identity service |
| `wallet_db` | Wallet service |
| `ledger_db` | Ledger service |
| `transaction_db` | Transaction service |
| `notification_db` | Notification service |
| `reconciliation_db` | Reconciliation service |
| `audit_db` | Audit service |

## Telemetry Pipeline

```
Spring Boot App
    │
    ├──→ OTLP (traces/metrics/logs) ──→ OTEL Collector ──→ Tempo (traces)
    │                                                   ──→ Prometheus (metrics)
    │                                                   ──→ Loki (logs)
    │
    └──→ /actuator/prometheus ──→ Prometheus (scrape)
                                        │
                                        └──→ Grafana (dashboards)
```

Apps can send telemetry either:
- **Directly** to Tempo (`localhost:4317`) for traces
- **Via OTEL Collector** (`localhost:14317`) for traces + metrics + logs in one pipeline

## Customization

Copy the override example and modify:

```bash
cp infra/docker-compose.override.yml.example infra/docker-compose.override.yml
# Edit ports, resource limits, etc.
```

The override file is gitignored — your local changes won't affect others.

## Troubleshooting

**Port conflict:** Check what's using a port:
```bash
lsof -i :5432  # Example for postgres port
```

**Reset everything:**
```bash
./scripts/dev-down.sh --clean
./scripts/dev-up.sh
```

**Check service logs:**
```bash
docker compose -f infra/docker-compose.yml logs -f postgres
docker compose -f infra/docker-compose.yml logs -f redpanda
```

**Verify databases were seeded:**
```bash
docker exec -it bw-postgres psql -U wallet_admin -c '\l'
```
