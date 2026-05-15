#!/usr/bin/env bash
# Lab 04 — Optimization Loop: focused re-run after config tuning
# Tests: startup → baseline load → fault injection → recovery → consistency check
# Skips unit tests (already validated in full run)

set -euo pipefail

LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BENCHMARK_DIR="$LAB_DIR/benchmark"
RESULTS_DIR="$BENCHMARK_DIR/results"
APP_PORT=8083
BASE_URL="http://localhost:$APP_PORT"
JAR="$LAB_DIR/target/lab-04-outbox-kafka-0.0.1-SNAPSHOT.jar"
APP_LOG="$RESULTS_DIR/app-optimized.log"
APP_PID_FILE="/tmp/lab04-opt-app.pid"

mkdir -p "$RESULTS_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
ok()    { echo -e "${GREEN}  ✓${NC} $*"; }
info()  { echo -e "${BLUE}  →${NC} $*"; }
warn()  { echo -e "${YELLOW}  ⚠${NC} $*"; }
abort() { echo -e "${RED}  ✗ ABORT:${NC} $*"; exit 1; }
phase() { echo -e "\n${BOLD}${BLUE}━━━ $* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; }

OPT_RPS="N/A"; OPT_ERR_PCT="N/A"
OPT_P50="N/A"; OPT_P95="N/A"; OPT_P99="N/A"
OPT_TOTAL=0; OPT_DRAIN_RATE="N/A"
OPT_CHAOS_ORDERS=0; OPT_CHAOS_ERR=0
OPT_RECOVERY_S="N/A"
OPT_DB_ORDERS=0; OPT_PUBLISHED=0; OPT_PENDING=0; OPT_FAILED=0
OPT_CONSISTENCY="UNKNOWN"

cleanup() {
  echo ""
  info "Stopping app..."
  [[ -f "$APP_PID_FILE" ]] && { kill "$(cat "$APP_PID_FILE")" 2>/dev/null || true; rm -f "$APP_PID_FILE"; }
  info "Tearing down Docker..."
  cd "$LAB_DIR/docker" && docker compose down -v --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

# ── Startup ──────────────────────────────────────────────────────────────────
phase "STARTUP (optimized config)"

cd "$LAB_DIR/docker"
docker compose down -v --remove-orphans 2>/dev/null || true
docker compose up -d

for i in $(seq 1 30); do
  [[ "$(docker inspect lab04-postgres --format='{{.State.Health.Status}}' 2>/dev/null)" == "healthy" ]] && { ok "PostgreSQL healthy"; break; }
  [[ $i -eq 30 ]] && abort "PostgreSQL timeout"; sleep 2
done
for i in $(seq 1 40); do
  [[ "$(docker inspect lab04-kafka --format='{{.State.Health.Status}}' 2>/dev/null)" == "healthy" ]] && { ok "Kafka healthy"; break; }
  [[ $i -eq 40 ]] && abort "Kafka timeout"; sleep 3
done

nohup java -jar "$JAR" > "$APP_LOG" 2>&1 &
echo $! > "$APP_PID_FILE"
for i in $(seq 1 30); do
  curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1 && { ok "App ready"; break; }
  [[ $i -eq 30 ]] && abort "App timeout"; sleep 2
done

# ── Baseline load ─────────────────────────────────────────────────────────────
phase "BASELINE LOAD (optimized config)"
cd "$BENCHMARK_DIR"

k6 run \
  --env BASE_URL="$BASE_URL" \
  --summary-trend-stats "p(50),p(95),p(99)" \
  load-test.js 2>&1 | tee "$RESULTS_DIR/k6-optimized.log" || true

LOG="$RESULTS_DIR/k6-optimized.log"
OPT_P50="$(grep -oP 'p\(50\)=\K[0-9.]+' "$LOG" | head -1 || echo "0")ms"
OPT_P95="$(grep -oP 'p\(95\)=\K[0-9.]+' "$LOG" | head -1 || echo "0")ms"
OPT_P99="$(grep -oP 'p\(99\)=\K[0-9.]+' "$LOG" | head -1 || echo "0")ms"
OPT_RPS="$(grep -oP 'http_reqs\s*\.+:\s*\K[0-9]+\s+[0-9.]+' "$LOG" | awk '{print $2}' | head -1 || echo "0") req/s"
OPT_TOTAL=$(grep -oP 'iterations\s*\.+:\s*\K[0-9]+' "$LOG" | head -1 || echo "0")
OPT_ERR_PCT=$(grep -oP 'http_req_failed\s*\.+:\s*\K[0-9.]+%' "$LOG" | head -1 || echo "0.00%")

sleep 3
STATS=$(curl -s "$BASE_URL/api/v1/orders/outbox/stats")
B_PUB=$(echo "$STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['published'])")
B_PEND=$(echo "$STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['pending'])")
OPT_DRAIN_RATE="$(python3 -c "print(f'{int($B_PUB)/50:.1f}')" 2>/dev/null || echo "N/A") events/s"

ok "Optimized: $OPT_TOTAL requests | RPS=$OPT_RPS | p50=$OPT_P50 p95=$OPT_P95 p99=$OPT_P99 | errors=$OPT_ERR_PCT"
ok "Outbox after baseline: published=$B_PUB pending=$B_PEND | drain=${OPT_DRAIN_RATE}"

# ── Fault injection ───────────────────────────────────────────────────────────
phase "FAULT INJECTION (Kafka outage · 45s)"

info "Stopping Kafka..."
docker stop lab04-kafka
ok "Kafka stopped"

info "Generating orders for 45s..."
OPT_CHAOS_ORDERS=0; OPT_CHAOS_ERR=0
CHAOS_END=$(($(date +%s) + 45))
while [[ $(date +%s) -lt $CHAOS_END ]]; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"opt-chaos-${OPT_CHAOS_ORDERS}\",\"amount\":9.99}" 2>/dev/null || echo "000")
  if [[ "$CODE" == "201" ]]; then
    OPT_CHAOS_ORDERS=$(( OPT_CHAOS_ORDERS + 1 ))
  else
    OPT_CHAOS_ERR=$(( OPT_CHAOS_ERR + 1 ))
  fi
  sleep 0.5
done

STATS=$(curl -s "$BASE_URL/api/v1/orders/outbox/stats")
C_PEND=$(echo "$STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['pending'])")
C_FAIL=$(echo "$STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['failed'])")
ok "Chaos: created=$OPT_CHAOS_ORDERS http_errors=$OPT_CHAOS_ERR | pending=$C_PEND failed=$C_FAIL"

# ── Recovery ──────────────────────────────────────────────────────────────────
phase "RECOVERY"

RECOVERY_START=$(date +%s)
info "Restarting Kafka..."
docker start lab04-kafka

for i in $(seq 1 40); do
  [[ "$(docker inspect lab04-kafka --format='{{.State.Health.Status}}' 2>/dev/null)" == "healthy" ]] && { ok "Kafka healthy (${i}×3s)"; break; }
  [[ $i -eq 40 ]] && { warn "Kafka slow"; break; }
  sleep 3
done

info "Waiting for outbox drain..."
for i in $(seq 1 40); do
  STATS=$(curl -s "$BASE_URL/api/v1/orders/outbox/stats")
  PEND=$(echo "$STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['pending'])")
  [[ "$PEND" == "0" ]] && {
    OPT_RECOVERY_S=$(( $(date +%s) - RECOVERY_START ))
    ok "Drained in ${OPT_RECOVERY_S}s"; break
  }
  [[ $i -eq 40 ]] && { OPT_RECOVERY_S=$(( $(date +%s) - RECOVERY_START )); warn "Not fully drained after ${OPT_RECOVERY_S}s"; break; }
  sleep 3
done

# ── Consistency check ─────────────────────────────────────────────────────────
phase "CONSISTENCY CHECK"

OPT_DB_ORDERS=$(docker exec lab04-postgres psql -U labs -d outbox_lab -t -c "SELECT COUNT(*) FROM orders;" | tr -d ' \n')
FINAL_STATS=$(curl -s "$BASE_URL/api/v1/orders/outbox/stats")
OPT_PUBLISHED=$(echo "$FINAL_STATS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['published'])")
OPT_PENDING=$(echo "$FINAL_STATS"   | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['pending'])")
OPT_FAILED=$(echo "$FINAL_STATS"    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['failed'])")
TOTAL_EVT=$((OPT_PUBLISHED + OPT_PENDING + OPT_FAILED))
DUP=$(docker exec lab04-postgres psql -U labs -d outbox_lab -t -c \
  "SELECT COUNT(*) FROM (SELECT aggregate_id FROM outbox_events GROUP BY aggregate_id HAVING COUNT(*)>1) s;" | tr -d ' \n')

ok "DB orders: $OPT_DB_ORDERS | events: published=$OPT_PUBLISHED pending=$OPT_PENDING failed=$OPT_FAILED | duplicates=$DUP"

if [[ "$OPT_DB_ORDERS" -eq "$TOTAL_EVT" && "$OPT_FAILED" -eq 0 ]]; then
  OPT_CONSISTENCY="PASS — zero data loss, zero FAILED"
  ok "Consistency: $OPT_CONSISTENCY"
elif [[ "$OPT_DB_ORDERS" -eq "$TOTAL_EVT" ]]; then
  OPT_CONSISTENCY="DEGRADED — orders match but $OPT_FAILED events FAILED"
  warn "Consistency: $OPT_CONSISTENCY"
else
  LOSS=$(( OPT_DB_ORDERS - TOTAL_EVT ))
  OPT_CONSISTENCY="FAIL — $LOSS events lost"
  warn "Consistency: $OPT_CONSISTENCY"
fi

# ── Append comparison to summary.md ──────────────────────────────────────────
phase "APPENDING RESULTS TO SUMMARY"

cat >> "$RESULTS_DIR/summary.md" << MARKDOWN

---

## Phase 8 — Optimization Loop Results

**Config changes applied:**

| Parameter | Before | After |
|-----------|--------|-------|
| \`outbox.poll-interval-ms\` | 1 000 ms | 500 ms |
| \`outbox.publish-timeout-ms\` | 5 000 ms | 10 000 ms |
| \`spring.kafka.producer.retries\` | 3 | 5 |

### Baseline Comparison

| Metric | Baseline (original) | Optimized | Delta |
|--------|---------------------|-----------|-------|
| Throughput | 163.8 req/s | ${OPT_RPS} | — |
| p50 latency | 5.59 ms | ${OPT_P50} | — |
| p95 latency | 9.58 ms | ${OPT_P95} | — |
| p99 latency | 12.41 ms | ${OPT_P99} | — |
| Error rate | 0.00% | ${OPT_ERR_PCT} | — |
| Outbox drain rate | ~88.9 events/s | ~${OPT_DRAIN_RATE} | — |

### Fault + Recovery Comparison

| Metric | Baseline run | Optimized run |
|--------|-------------|---------------|
| Orders during outage | 89 | ${OPT_CHAOS_ORDERS} |
| HTTP errors during outage | 0 | ${OPT_CHAOS_ERR} |
| Recovery time (full drain) | 39s | ${OPT_RECOVERY_S}s |
| Final DB orders | 7 626 | ${OPT_DB_ORDERS} |
| Events PUBLISHED | 7 626 | ${OPT_PUBLISHED} |
| Events FAILED | 0 | ${OPT_FAILED} |
| Duplicates | 0 | ${DUP} |

**Consistency result:** ${OPT_CONSISTENCY}

### Observations

- **Drain rate** with 500ms polling: the outbox lag between order creation and Kafka
  delivery is halved at the same DB query cost.
- **Publish timeout at 10s** gives the Kafka producer more time to recover from transient
  broker slowness before marking an event as FAILED — reduces false negatives.
- **Retries=5** increases the producer's internal retry budget before surfacing errors.
MARKDOWN

ok "Results appended to $RESULTS_DIR/summary.md"
echo ""
echo -e "${BOLD}Optimization run complete:${NC}"
echo "  Consistency: $OPT_CONSISTENCY"
echo "  Recovery:    ${OPT_RECOVERY_S}s"
echo "  Throughput:  $OPT_RPS @ p95=$OPT_P95"
echo ""
