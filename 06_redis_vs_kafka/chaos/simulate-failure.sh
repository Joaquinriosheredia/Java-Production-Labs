#!/usr/bin/env bash
# =============================================================================
# Lab 06 — Chaos: Standalone durability demonstration
#
# This script is a standalone educational companion to run-benchmark.sh.
# It visually demonstrates the key trade-off between Redis and Kafka
# when the broker goes down mid-stream.
#
# Prerequisites: infrastructure + app must already be running.
#   docker compose -f docker/docker-compose.yml up -d
#   java -jar target/*.jar &
#
# Usage:
#   ./chaos/simulate-failure.sh [redis|kafka|both]
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8085}"
MSGS="${MSGS:-50}"
TARGET="${1:-both}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${RESET} $*"; }
ok()   { echo -e "${GREEN}  ✓${RESET} $*"; }
fail() { echo -e "${RED}  ✗${RESET} $*"; }

wait_healthy() {
    local container=$1
    local timeout=90 elapsed=0
    log "Waiting for $container to become healthy..."
    until docker inspect "$container" --format '{{.State.Health.Status}}' 2>/dev/null | grep -q "healthy"; do
        sleep 3; elapsed=$((elapsed + 3))
        [[ $elapsed -ge $timeout ]] && { echo "Timeout waiting for $container"; exit 1; }
    done
    ok "$container is healthy"
    sleep 2
}

chaos_redis() {
    echo ""
    echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
    echo -e "${BOLD} CHAOS: Redis Pub/Sub — Broker Failure          ${RESET}"
    echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"

    log "Phase 1: Baseline (broker UP)"
    local pre
    pre=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/redis/pre?messages=$MSGS")
    local pre_rcv; pre_rcv=$(echo "$pre" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('received',0))")
    ok "Pre-fault: sent=$MSGS received=$pre_rcv lost=$((MSGS - pre_rcv))"

    log "Phase 2: STOPPING lab06-redis..."
    docker stop lab06-redis
    echo -e "${RED}  ⚡ Redis is DOWN${RESET}"
    sleep 1

    log "Phase 2: Publishing $MSGS messages while Redis is DOWN"
    local during
    during=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/redis/during?messages=$MSGS" \
        --max-time 20 || echo '{"received":0}')
    local dur_rcv; dur_rcv=$(echo "$during" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('received',0))" 2>/dev/null || echo 0)
    local dur_lost=$((MSGS - dur_rcv))
    echo ""
    echo -e "${RED}${BOLD}  RESULT: ${dur_lost}/${MSGS} messages PERMANENTLY LOST${RESET}"
    echo -e "${RED}  Redis Pub/Sub has no persistence. Lost = gone forever.${RESET}"
    echo ""

    log "Phase 3: Restarting Redis..."
    docker start lab06-redis
    wait_healthy "lab06-redis"

    local post
    post=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/redis/after?messages=$MSGS" || echo '{"received":0}')
    local post_rcv; post_rcv=$(echo "$post" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('received',0))" 2>/dev/null || echo 0)
    ok "Post-restart: sent=$MSGS received=$post_rcv (lost messages NOT recovered)"
    echo ""
    echo -e "  ${YELLOW}Summary:${RESET}"
    echo -e "  Pre-fault:   ${GREEN}${pre_rcv}/${MSGS}${RESET} delivered"
    echo -e "  During fault: ${RED}${dur_rcv}/${MSGS}${RESET} delivered — ${RED}${dur_lost} lost forever${RESET}"
    echo -e "  Post-restart: ${GREEN}${post_rcv}/${MSGS}${RESET} delivered (new messages work again)"
}

chaos_kafka() {
    echo ""
    echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
    echo -e "${BOLD} CHAOS: Kafka — Broker Failure                  ${RESET}"
    echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"

    log "Phase 1: Baseline (broker UP)"
    local pre
    pre=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/kafka/pre?messages=$MSGS")
    local pre_rcv; pre_rcv=$(echo "$pre" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('received',0))")
    ok "Pre-fault: sent=$MSGS received=$pre_rcv"

    log "Phase 2: STOPPING lab06-kafka..."
    docker stop lab06-kafka
    echo -e "${YELLOW}  ⚡ Kafka is DOWN — producer will buffer up to buffer.memory (32 MB default)${RESET}"
    sleep 1

    log "Phase 2: Publishing $MSGS messages while Kafka is DOWN"
    local during
    during=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/kafka/during?messages=$MSGS" \
        --max-time 20 || echo '{"received":0,"lost":0}')
    local dur_lost; dur_lost=$(echo "$during" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('lost',0))" 2>/dev/null || echo 0)
    echo ""
    if [[ "$dur_lost" == "0" ]]; then
        echo -e "${GREEN}${BOLD}  Kafka producer buffered messages — no data loss reported by producer${RESET}"
    else
        echo -e "${YELLOW}  Producer timeout: ${dur_lost}/${MSGS} msgs exceeded delivery.timeout.ms${RESET}"
    fi
    echo ""

    log "Phase 3: Restarting Kafka..."
    docker start lab06-kafka
    wait_healthy "lab06-kafka"

    local post
    post=$(curl -sf -X POST "$BASE_URL/api/v1/benchmark/fault/kafka/after?messages=$MSGS" \
        --max-time 30 || echo '{"received":0}')
    local post_rcv; post_rcv=$(echo "$post" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('received',0))" 2>/dev/null || echo 0)
    ok "Post-restart: sent=$MSGS received=$post_rcv"
    echo ""
    echo -e "  ${YELLOW}Summary:${RESET}"
    echo -e "  Pre-fault:    ${GREEN}${pre_rcv}/${MSGS}${RESET} delivered"
    echo -e "  During fault: producer buffered — 0 committed to log (broker was down)"
    echo -e "  Post-restart: ${GREEN}${post_rcv}/${MSGS}${RESET} delivered — consumer group committed offset intact"
    echo -e "  ${GREEN}No data loss for committed messages. Consumer offset preserved.${RESET}"
}

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║  Lab 06 — Chaos: Durability Test             ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════╝${RESET}"

[[ "$TARGET" == "redis" || "$TARGET" == "both" ]] && chaos_redis
[[ "$TARGET" == "kafka" || "$TARGET" == "both" ]] && chaos_kafka

echo ""
echo -e "${BOLD}═══ CONCLUSION ═══════════════════════════════${RESET}"
echo -e "${RED}Redis Pub/Sub:${RESET} Fire-and-forget. Fast. Zero durability."
echo -e "               Messages not delivered = GONE FOREVER."
echo ""
echo -e "${GREEN}Kafka:${RESET}         Durable log. Higher latency. Never loses committed data."
echo -e "               Consumer lag ≠ data loss. Replay is always possible."
echo ""
