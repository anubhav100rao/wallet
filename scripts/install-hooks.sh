#!/usr/bin/env bash
# Install git hooks for the banking-wallet project

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "📦 Installing git hooks..."

cp "$SCRIPT_DIR/pre-commit" "$PROJECT_ROOT/.git/hooks/pre-commit"
chmod +x "$PROJECT_ROOT/.git/hooks/pre-commit"

echo "✅ Git hooks installed."
