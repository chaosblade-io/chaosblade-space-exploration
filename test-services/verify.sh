#!/usr/bin/env bash
# verify.sh — Integration verification for proxy-agent with test-services.
#
# Starts all services via docker-compose (test-services + proxy-agent),
# then verifies 4 scenarios:
#   1. HTTP Recording
#   2. HTTP Replay
#   3. HTTP Interception
#   4. Comparison (real vs replayed)
#
# Usage:
#   chmod +x verify.sh
#   ./verify.sh
#
# Prerequisites: docker, docker-compose, curl, jq

set -euo pipefail

GATEWAY="http://localhost:8080"
CONTROL="http://localhost:19090"
COMPOSE_FILE="docker-compose.verify.yml"

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
        echo "  [PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name"
        echo "         expected: $expected"
        echo "         actual:   $actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_contains() {
    local name="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$actual" | grep -q "$expected"; then
        echo "  [PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name"
        echo "         expected to contain: $expected"
        echo "         actual: $actual"
        FAIL=$((FAIL + 1))
    fi
}

http_status() {
    curl -s -o /dev/null -w "%{http_code}" "$@"
}

http_body() {
    curl -s "$@"
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
            echo "  [ERROR] Services did not become ready in time."
            return 1
        fi
        sleep 2
    done
}

set_mode() {
    local mode="$1"
    curl -s -X PUT "$CONTROL/control/mode" \
        -H "Content-Type: application/json" \
        -d "{\"mode\":\"$mode\"}" > /dev/null
    echo "  → Mode set to: $mode"
}

get_mode() {
    curl -s "$CONTROL/control/mode" | jq -r '.mode'
}

# ─── Lifecycle ──────────────────────────────────────────────────────────────

cleanup() {
    log_section "Cleaning up"
    docker-compose -f "$COMPOSE_FILE" down --volumes --remove-orphans 2>/dev/null || true
}

startup() {
    log_section "Starting services"
    docker-compose -f "$COMPOSE_FILE" up --build -d
    wait_for_services
}

# Ensure cleanup on exit
trap cleanup EXIT

# ─── Main ───────────────────────────────────────────────────────────────────

log_section "Building and starting services"
cleanup
startup

# ─── Scenario 0: Verify passthrough works ───────────────────────────────────

log_section "Scenario 0: Passthrough (baseline)"

assert_eq "Proxy mode is passthrough" "passthrough" "$(get_mode)"

STATUS=$(http_status -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":1}')
assert_eq "Create order via passthrough returns 201" "201" "$STATUS"

# ─── Scenario 1: HTTP Recording ────────────────────────────────────────────

log_section "Scenario 1: HTTP Recording"

# Clear any existing snapshots
curl -s -X DELETE "$CONTROL/control/snapshots" > /dev/null

# Switch to record mode
set_mode "record"
assert_eq "Mode is record" "record" "$(get_mode)"

# Send a request through the proxy (gateway → proxy-agent → order-svc)
echo "  → Sending POST /api/orders through proxy (record mode)..."
RECORD_BODY=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-002","quantity":1}')
RECORD_STATUS=$(echo "$RECORD_BODY" | jq -r '.status // empty')
assert_eq "Order created successfully during recording" "confirmed" "$RECORD_STATUS"

# Wait a moment for snapshot to be stored
sleep 1

# Verify snapshot was recorded
SNAP_COUNT=$(curl -s "$CONTROL/control/snapshots" | jq 'length')
assert_eq "Snapshot count is at least 1" "true" "$([ "$SNAP_COUNT" -ge 1 ] && echo true || echo false)"

SNAP_STATUS=$(curl -s "$CONTROL/control/snapshots" | jq -r '.[0].response.status_code')
assert_eq "Recorded snapshot has status code 201" "201" "$SNAP_STATUS"

SNAP_METHOD=$(curl -s "$CONTROL/control/snapshots" | jq -r '.[0].signature.method')
assert_eq "Recorded snapshot method is POST" "POST" "$SNAP_METHOD"

# Check stats
STATS_RECORDED=$(curl -s "$CONTROL/control/stats" | jq '.requests_recorded')
assert_eq "Stats show recorded requests >= 1" "true" "$([ "$STATS_RECORDED" -ge 1 ] && echo true || echo false)"

# ─── Scenario 2: HTTP Replay ───────────────────────────────────────────────

log_section "Scenario 2: HTTP Replay"

# Switch to replay mode
set_mode "replay"
assert_eq "Mode is replay" "replay" "$(get_mode)"

# Send the SAME request — should get the recorded response
echo "  → Sending same POST /api/orders through proxy (replay mode)..."
REPLAY_BODY=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-002","quantity":1}')

# The replayed response should have status "confirmed" from the recording
REPLAY_STATUS=$(echo "$REPLAY_BODY" | jq -r '.status // empty')
assert_eq "Replayed response has status confirmed" "confirmed" "$REPLAY_STATUS"

# Check stats
STATS_REPLAYED=$(curl -s "$CONTROL/control/stats" | jq '.requests_replayed')
assert_eq "Stats show replayed requests >= 1" "true" "$([ "$STATS_REPLAYED" -ge 1 ] && echo true || echo false)"

# ─── Scenario 3: HTTP Interception ─────────────────────────────────────────

log_section "Scenario 3: HTTP Interception"

# Switch to intercept mode
set_mode "intercept"
assert_eq "Mode is intercept" "intercept" "$(get_mode)"

# Add an intercept rule: /api/orders returns 503
echo "  → Adding intercept rule: POST /api/orders → 503"
RULE_RESP=$(curl -s -X POST "$CONTROL/control/rules" \
    -H "Content-Type: application/json" \
    -d '{
        "path_match": "/api/orders",
        "method": "POST",
        "response": {
            "status_code": 503,
            "headers": {"Content-Type": "application/json"},
            "body": "{\"error\":\"service unavailable\",\"injected\":true}"
        }
    }')
RULE_ID=$(echo "$RULE_RESP" | jq -r '.id')
echo "  → Rule created with ID: $RULE_ID"

# Send a request — should get the mock 503 response
echo "  → Sending POST /api/orders through proxy (intercept mode)..."
INTERCEPT_STATUS=$(http_status -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":1}')
assert_eq "Intercepted request returns 503" "503" "$INTERCEPT_STATUS"

INTERCEPT_BODY=$(http_body -X POST "$GATEWAY/api/orders" \
    -H "Content-Type: application/json" \
    -d '{"product_id":"product-001","quantity":1}')
assert_contains "Intercepted response contains injected flag" '"injected":true' "$INTERCEPT_BODY"

# Verify unmatched requests still pass through (GET /health should work)
HEALTH_STATUS=$(http_status "$GATEWAY/health")
assert_eq "Unmatched GET /health still passes through" "200" "$HEALTH_STATUS"

# Check stats
STATS_INTERCEPTED=$(curl -s "$CONTROL/control/stats" | jq '.requests_intercepted')
assert_eq "Stats show intercepted requests >= 1" "true" "$([ "$STATS_INTERCEPTED" -ge 1 ] && echo true || echo false)"

# Clean up the rule
curl -s -X DELETE "$CONTROL/control/rules/$RULE_ID" > /dev/null

# ─── Scenario 4: Comparison (real vs replayed) ─────────────────────────────

log_section "Scenario 4: Comparison — real vs replayed"

# Clear snapshots and record a fresh request
curl -s -X DELETE "$CONTROL/control/snapshots" > /dev/null
set_mode "record"

echo "  → Recording a GET /health request..."
REAL_BODY=$(http_body "$GATEWAY/health")
REAL_STATUS=$(echo "$REAL_BODY" | jq -r '.status // empty')

sleep 1

# Switch to replay and send the same request
set_mode "replay"
echo "  → Replaying the same GET /health request..."
REPLAY_BODY_2=$(http_body "$GATEWAY/health")
REPLAY_STATUS_2=$(echo "$REPLAY_BODY_2" | jq -r '.status // empty')

assert_eq "Real response status matches replayed" "$REAL_STATUS" "$REPLAY_STATUS_2"

# Compare body content (strip whitespace differences)
REAL_NORMALIZED=$(echo "$REAL_BODY" | jq -cS '.')
REPLAY_NORMALIZED=$(echo "$REPLAY_BODY_2" | jq -cS '.')
assert_eq "Real response body matches replayed body" "$REAL_NORMALIZED" "$REPLAY_NORMALIZED"

# ─── Summary ───────────────────────────────────────────────────────────────

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
    echo "  ✅ All tests PASSED"
    exit 0
fi
