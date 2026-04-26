#!/usr/bin/env bash
# Lab 07 — Chaos: Query without index (simulate missing index)
#
# Drops the partial index to simulate what happens when the index
# is missing (e.g., accidentally dropped in a migration).

set -euo pipefail

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5434}"

echo "================================================"
echo " Lab 07 — Chaos: Drop Partial Index"
echo "================================================"

echo "[1] Dropping idx_events_pending..."
docker exec lab07-postgres psql -U labs -d pgtuning_lab -c "DROP INDEX IF EXISTS idx_events_pending;" || \
  echo "Run manually: psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U labs -d pgtuning_lab -c 'DROP INDEX IF EXISTS idx_events_pending;'"

echo ""
echo "[2] Query plan without index (should show Seq Scan):"
curl -s "http://localhost:8086/api/v1/postgres/explain?query=pending_no_index"

echo ""
echo "[3] Recreating index..."
docker exec lab07-postgres psql -U labs -d pgtuning_lab -c \
  "CREATE INDEX idx_events_pending ON events(occurred_at ASC) WHERE status = 'PENDING';" || \
  echo "Recreate manually"

echo ""
echo "Recovery complete. Index restored."
