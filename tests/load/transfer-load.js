// ──────────────────────────────────────────────────────────────────────────────
// Banking Wallet — Load test: sustained 1k tps transfer
// ──────────────────────────────────────────────────────────────────────────────
//
// Goal: 1,000 transfers/sec sustained for 5 minutes against the local monolith,
// p99 < 500ms, zero errors, ledger sum stays at zero.
//
// How to run (assumes the docker stack is up and the app is on :8081):
//
//   1. Bring up infra:        ./scripts/dev-up.sh
//   2. Boot the monolith:     ./gradlew :apps:wallet-monolith:bootRun --args=--spring.profiles.active=local
//   3. Seed wallets + funds:  ./scripts/seed-load-test.sh   (creates SOURCE_WALLET_ID + DEST_WALLET_IDS)
//   4. Run k6:                k6 run -e SOURCE_WALLET_ID=… -e DEST_WALLETS_CSV=… tests/load/transfer-load.js
//
// The seed script (TODO) must:
//   * register a load-test user
//   * fund SOURCE_WALLET_ID with at least 5_000_000.0000 USD via /api/deposits
//   * create DEST_WALLET_COUNT destination wallets and fund them with 0
//
// Why this lives here (vs Gatling): k6 is single-binary, JS-driven, and emits
// Prometheus-compatible metrics that the Grafana dashboard already scrapes via
// the k6 exporter. Gatling is also fine — translate the scenario shape and the
// thresholds map.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const SOURCE_WALLET_ID = __ENV.SOURCE_WALLET_ID || '';
const DEST_WALLETS = (__ENV.DEST_WALLETS_CSV || '').split(',').filter(Boolean);

if (!SOURCE_WALLET_ID || DEST_WALLETS.length === 0) {
  fail('Set SOURCE_WALLET_ID and DEST_WALLETS_CSV before running.');
}

// Custom metrics surfaced in the k6 summary AND scrapeable via the Prometheus
// remote-write output (`k6 run --out experimental-prometheus-rw …`).
const transferLatency = new Trend('banking_transfer_duration_ms', true);
const sagaSettled = new Counter('banking_transfers_settled');
const sagaFailed = new Counter('banking_transfers_failed');

export const options = {
  // Ramping-arrival-rate decouples request-rate from VU count, which is what we
  // want for "tps" testing — k6 spins up VUs as needed to maintain the rate.
  scenarios: {
    transfers: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { duration: '30s', target: 1000 },   // ramp up
        { duration: '5m',  target: 1000 },   // soak
        { duration: '15s', target: 0 },      // wind down
      ],
    },
  },
  thresholds: {
    // Hard gates — k6 exits non-zero if violated.
    http_req_duration: ['p(99)<500'],
    http_req_failed:   ['rate<0.001'],          // <0.1% errors
    banking_transfers_failed: ['count<10'],
    checks:            ['rate>0.999'],
  },
  // Tag every request so panels can split by scenario.
  tags: { service: 'wallet-monolith', test: 'transfer-load' },
};

export default function transferOnce() {
  const dest = DEST_WALLETS[Math.floor(Math.random() * DEST_WALLETS.length)];
  const body = JSON.stringify({
    fromWalletId: SOURCE_WALLET_ID,
    toWalletId:   dest,
    amount:       '1.0000',
    currency:     'USD',
  });
  const headers = {
    'Content-Type': 'application/json',
    'Idempotency-Key': uuidv4(),  // unique per request — load test, not retry test
  };

  const res = http.post(`${BASE_URL}/api/transfers`, body, { headers });
  transferLatency.add(res.timings.duration);

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has transactionId': (r) => r.json('transactionId') !== undefined,
  });
  if (ok) {
    sagaSettled.add(1);
  } else {
    sagaFailed.add(1);
  }
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: '  ', enableColors: true }),
    'tests/load/last-run-summary.json': JSON.stringify(data, null, 2),
  };
}

// Inline textSummary — k6 ships it but importing from the network is unreliable in CI.
function textSummary(data, opts) {
  const t = data.metrics.http_req_duration?.values || {};
  const lines = [
    '─── Banking Wallet load test summary ───',
    `requests:    ${data.metrics.http_reqs?.values?.count ?? 'n/a'}`,
    `errors:      ${(data.metrics.http_req_failed?.values?.rate ?? 0).toFixed(4)}`,
    `p50:         ${(t['p(50)'] ?? 0).toFixed(1)} ms`,
    `p95:         ${(t['p(95)'] ?? 0).toFixed(1)} ms`,
    `p99:         ${(t['p(99)'] ?? 0).toFixed(1)} ms`,
    `transfers settled: ${data.metrics.banking_transfers_settled?.values?.count ?? 0}`,
    `transfers failed:  ${data.metrics.banking_transfers_failed?.values?.count ?? 0}`,
  ];
  return lines.join('\n') + '\n';
}
