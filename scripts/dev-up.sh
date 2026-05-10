#!/usr/bin/env bash
# Start the local development infrastructure stack.
# Usage: ./scripts/dev-up.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infra"

echo "🚀 Starting banking-wallet infrastructure..."
echo ""

docker compose -f "$INFRA_DIR/docker-compose.yml" up -d

echo ""
echo "⏳ Waiting for services to become healthy..."

# Wait for critical services
MAX_WAIT=120
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $MAX_WAIT ]; do
    UNHEALTHY=$(docker compose -f "$INFRA_DIR/docker-compose.yml" ps --format json 2>/dev/null | \
        python3 -c "
import sys, json
lines = sys.stdin.read().strip().split('\n')
unhealthy = 0
for line in lines:
    if not line: continue
    try:
        svc = json.loads(line)
        health = svc.get('Health', '')
        if health and health != 'healthy':
            unhealthy += 1
    except: pass
print(unhealthy)
" 2>/dev/null || echo "unknown")

    if [ "$UNHEALTHY" = "0" ]; then
        break
    fi

    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
    echo "  ... waiting ($ELAPSED/${MAX_WAIT}s) — $UNHEALTHY services not yet healthy"
done

echo ""
docker compose -f "$INFRA_DIR/docker-compose.yml" ps
echo ""

# Check if all healthy
FINAL_UNHEALTHY=$(docker compose -f "$INFRA_DIR/docker-compose.yml" ps --format json 2>/dev/null | \
    python3 -c "
import sys, json
lines = sys.stdin.read().strip().split('\n')
unhealthy = 0
for line in lines:
    if not line: continue
    try:
        svc = json.loads(line)
        health = svc.get('Health', '')
        if health and health != 'healthy':
            unhealthy += 1
    except: pass
print(unhealthy)
" 2>/dev/null || echo "unknown")

if [ "$FINAL_UNHEALTHY" = "0" ]; then
    echo "✅ All services are healthy!"
    echo ""
    echo "📊 Access points:"
    echo "  Grafana:           http://localhost:3000  (admin/admin)"
    echo "  Prometheus:        http://localhost:9090"
    echo "  pgAdmin:           http://localhost:5050  (admin@wallet.local/admin)"
    echo "  Redpanda Console:  http://localhost:8080"
    echo "  Mailhog:           http://localhost:8025"
    echo "  Postgres:          localhost:5432         (wallet_admin/wallet_secret)"
    echo "  Redis:             localhost:6379"
    echo "  Kafka (Redpanda):  localhost:19092"
    echo "  Tempo:             http://localhost:3200"
    echo "  Loki:              http://localhost:3100"
else
    echo "⚠️  Some services may not be fully healthy yet."
    echo "   Run 'docker compose -f infra/docker-compose.yml ps' to check."
fi
