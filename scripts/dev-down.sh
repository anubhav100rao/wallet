#!/usr/bin/env bash
# Stop the local development infrastructure stack.
# Usage: ./scripts/dev-down.sh [--clean]
#
# Options:
#   --clean    Remove volumes (all data will be lost)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infra"

if [[ "${1:-}" == "--clean" ]]; then
    echo "🧹 Stopping and removing all containers + volumes..."
    docker compose -f "$INFRA_DIR/docker-compose.yml" down -v
    echo "✅ Infrastructure stopped and volumes removed."
else
    echo "🛑 Stopping infrastructure..."
    docker compose -f "$INFRA_DIR/docker-compose.yml" down
    echo "✅ Infrastructure stopped. Volumes preserved."
    echo "   Use './scripts/dev-down.sh --clean' to also remove volumes."
fi
