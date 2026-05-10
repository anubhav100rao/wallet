# Load tests

`transfer-load.js` — sustained 1,000 tps transfer scenario for k6. Acceptance gates: p99 < 500ms,
error rate < 0.1%, ledger sum stays at zero post-run.

## Run

```bash
# 1. Bring up infra and the app
./scripts/dev-up.sh
./gradlew :apps:wallet-monolith:bootRun --args='--spring.profiles.active=local' &

# 2. Seed source + 100 destination wallets (TODO: scripts/seed-load-test.sh)
SOURCE=...    # capture from seed output
DESTS=uuid1,uuid2,...

# 3. Hammer
k6 run -e SOURCE_WALLET_ID=$SOURCE -e DEST_WALLETS_CSV=$DESTS tests/load/transfer-load.js
```

## Pre-seed requirement

The current API has no public wallet-creation endpoint. Until one exists, the seed script must
insert wallet rows directly via `psql` and use `/api/deposits` to fund the source. A real Phase 2
project would expose `POST /api/wallets` and seed via the API exclusively.

## Reading the report

Live: Grafana → "Banking Wallet — Overview" while k6 is running. The dashboard's HTTP TPS,
latency, and saga-failure panels show real-time effect. Persisted summary lands at
`tests/load/last-run-summary.json`.

## What "passed" means

- `http_req_duration p99 < 500ms`
- `http_req_failed rate < 0.001`
- `banking_transfers_failed count < 10`
- `checks rate > 0.999`

If any gate is violated, k6 exits non-zero — wire that into CI when you're ready to enforce.
