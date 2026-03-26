#!/usr/bin/env bash
# verify-grpc.sh — gRPC proxy verification
#
# Tests proxy-agent's gRPC transparent proxy with cmux:
#   1. Passthrough baseline (gRPC works through proxy)
#   2. gRPC recording (capture inventory-svc responses)
#   3. gRPC replay (return recorded responses)
#   4. gRPC interception (inject gRPC error)
#   5. HTTP regression (HTTP still works through same port)
#
# Architecture:
#   gateway (:8080) → order-svc (:8081) → proxy-agent (:8082, cmux) → inventory-svc (:8082)
#   order-svc calls inventory-svc via gRPC through the proxy.
#
# Usage:
#   ./verify-grpc.sh

set -euo pipefail

GATEWAY="http://localhost:8080"
CONTROL="http://localhost:19090"
COMPOSE_FILE="docker-compose.grpc-verify.yml"

PASS=0
FAIL=0
TOTAL=0

# ─── Helpers ────────────────────────────────────────────────────────────────

log_section() {
    echo ""
    echo "================================================================"
    echo "  $1"
    echo "================================================================"
}

assert_eq() {
    local name="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if [[ "$expected" == "$actual" ]]; then
        echo "  [PASS] $name"; PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name"; echo "         expected: $expected"; echo "         actual:   $actual"; FAIL=$((FAIL + 1))
    fi
}

assert_contains() {
    local name="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$actual" | grep -q "$expected"; then
        echo "  [PASS] $name"; PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name"; echo "         expected to contain: $expected"; echo "         actual: $actual"; FAIL=$((FAIL + 1))
    fi
}

assert_ge() {
    local name="$1" threshold="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if [[ "$actual" -ge "$threshold" ]] 2>/dev/null; then
        echo "  [PASS] $name"; PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name"; echo "         expected >= $threshold, got $actual"; FAIL=$((FAIL + 1))
    fi
}

http_body() { curl -s "$@"; }

set_mode() {
    local mode="$1"
    curl -s -X PUT "$CONTROL/control/mode" \
        -H "Content-Type: application/json" \
        -d "{\"mode\":\"$mode\"}" > /dev/null
    echo "  → Mode set to: $mode"
}

get_stat() {
    local field="$1"
    curl -s "$CONTROL/control/stats" | python3 -c "import sys,json; print(json.load(sys.stdin).get('$field',0))" 2>/dev/null || echo "0"
}

wait_for_services() {
    echo "Waiting for services to be ready..."
    local max_retries=30
    local retry=0
    while true; do
        if curl -s "$GATEWAY/health" > /dev/null 2>&1 && \
           curl -s "$CONTROL/control/health" > /dev/null 2>&1; then
            echo "  All services ready."
            return 0
        fi
        retry=$((retry + 1))
        if [[ $retry -ge $max_retries ]]; then
            echo "  [ERROR] Services did not become ready."
            return 1
        fi
        sleep 2
    done
}

# ─── Lifecycle ──────────────────────────────────────────────────────────────

cleanup() {
    log_section "Cleaning up"
    docker-compose -f "$COMPOSE_FILE" down --volumes --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

log_section "Building and starting services"
cleanup
docker-compose -f "$COMPOSE_FILE" up --build -d 2>&1 | tail -5
wait_for_services

# ═══════════════════════════════════════════════════════════════════════════
#  Scenario 0: Passthrough baseline
# ═══════════════════════════════════════════════════════════════════════════

log_section "Scenario 0: Passthrough baseline"

MODE=$(curl -s "$CONTROL/control/mode" | python3 -c "import sys,json; print(json.load(sys.stdin)['mode'])" 2>/dev/null)
assert_eq "Mode is passthrough" "passthrough" "$MODE"

# Create order for product-001 (stock=100, should succeed)
# This triggers: order-svc → (gRPC) → proxy-agent → inventory-svc
RESP=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":1}')
assert_contains "Passthrough: order confirmed" '"status":"confirmed"' "$RESP"

# Create order for product-003 (stock=0, should be rejected)
RESP2=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-003","quantity":1}')
assert_contains "Passthrough: out-of-stock rejected" '"status":"rejected"' "$RESP2"

# ═══════════════════════════════════════════════════════════════════════════
#  Scenario 1: gRPC Recording
# ═══════════════════════════════════════════════════════════════════════════

log_section "Scenario 1: gRPC Recording"

curl -s -X DELETE "$CONTROL/control/snapshots" > /dev/null
set_mode "record"

# Send an order — gRPC calls to inventory-svc get recorded
echo "  → Creating order (gRPC calls will be recorded)..."
REC_RESP=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":2}')
assert_contains "Record: order confirmed" '"status":"confirmed"' "$REC_RESP"

sleep 1

# Verify snapshots were recorded
SNAP_COUNT=$(get_stat "snapshot_count")
assert_ge "Snapshots recorded" 1 "$SNAP_COUNT"

RECORDED=$(get_stat "requests_recorded")
assert_ge "Requests recorded" 1 "$RECORDED"

# Check that snapshots have gRPC method paths
SNAP_METHODS=$(curl -s "$CONTROL/control/snapshots" | \
    python3 -c "import sys,json; snaps=json.load(sys.stdin); print(' '.join(s['signature']['path'] for s in snaps))" 2>/dev/null || echo "")
echo "  → Recorded methods: $SNAP_METHODS"
assert_contains "Recorded gRPC methods include inventory" "InventoryService" "$SNAP_METHODS"

# ═══════════════════════════════════════════════════════════════════════════
#  Scenario 2: gRPC Replay
# ═══════════════════════════════════════════════════════════════════════════

log_section "Scenario 2: gRPC Replay"

set_mode "replay"

# Send the same order — should replay the recorded gRPC responses
echo "  → Creating same order (gRPC should replay)..."
REP_RESP=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":2}')
assert_contains "Replay: order confirmed" '"status":"confirmed"' "$REP_RESP"

REPLAYED=$(get_stat "requests_replayed")
assert_ge "Requests replayed" 1 "$REPLAYED"

# ═══════════════════════════════════════════════════════════════════════════
#  Scenario 3: gRPC Interception
# ═══════════════════════════════════════════════════════════════════════════

log_section "Scenario 3: gRPC Interception"

set_mode "intercept"

# Add rule: intercept CheckStock with UNAVAILABLE (code 14)
echo "  → Adding intercept rule for CheckStock → UNAVAILABLE"
curl -s -X POST "$CONTROL/control/rules" \
    -H "Content-Type: application/json" \
    -d '{
        "path_match": "/inventorypb.InventoryService/CheckStock",
        "method": "GRPC",
        "response": {
            "status_code": 14,
            "body": "inventory service unavailable (injected)"
        }
    }' > /dev/null

# Send order — CheckStock should fail, order-svc should return error
echo "  → Creating order (gRPC should be intercepted)..."
INT_RESP=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":1}')
echo "  → Response: $INT_RESP"
# order-svc should report an error since CheckStock fails
assert_contains "Intercept: gRPC error propagated" '"error"' "$INT_RESP"

INTERCEPTED=$(get_stat "requests_intercepted")
assert_ge "Requests intercepted" 1 "$INTERCEPTED"

# ═══════════════════════════════════════════════════════════════════════════
#  Scenario 4: HTTP still works (regression)
# ═══════════════════════════════════════════════════════════════════════════

log_section "Scenario 4: HTTP regression"

# Switch to passthrough, clear rules
set_mode "passthrough"
curl -s -X DELETE "$CONTROL/control/snapshots" > /dev/null

# Health check through gateway → order-svc (HTTP, not gRPC)
HEALTH=$(http_body "$GATEWAY/health")
assert_contains "HTTP health check works" '"status":"ok"' "$HEALTH"

# Full order flow still works
FINAL_RESP=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-002","quantity":1}')
assert_contains "HTTP order flow works" '"status":"confirmed"' "$FINAL_RESP"

# ═══════════════════════════════════════════════════════════════════════════
#  Summary
# ═══════════════════════════════════════════════════════════════════════════

log_section "Results"
echo ""
echo "  Total:  $TOTAL"
echo "  Passed: $PASS"
echo "  Failed: $FAIL"
echo ""
if [[ $FAIL -gt 0 ]]; then
    echo "  ❌ Some tests FAILED"
    exit 1
else
    echo "  ✅ All tests PASSED — gRPC proxy verified"
    exit 0
fi
