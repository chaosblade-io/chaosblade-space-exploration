#!/usr/bin/env bash
# test.sh — Verify the test-services call chain via the gateway.
# Usage: ./test.sh [gateway_base_url]
#   Default gateway URL: http://localhost:8080

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

PASS=0
FAIL=0

# Helper: run a test case and check the HTTP status code and optional body match.
# Arguments: test_name  method  url  expected_status  [body_match]  [post_data]
run_test() {
    local name="$1"
    local method="$2"
    local url="$3"
    local expected_status="$4"
    local body_match="${5:-}"
    local post_data="${6:-}"

    local curl_args=(-s -w "\n%{http_code}" -X "$method")
    if [[ -n "$post_data" ]]; then
        curl_args+=(-H "Content-Type: application/json" -d "$post_data")
    fi

    local output
    output=$(curl "${curl_args[@]}" "$url" 2>/dev/null) || true

    local status_code
    status_code=$(echo "$output" | tail -1)
    local body
    body=$(echo "$output" | sed '$d')

    local result="PASS"
    local reason=""

    if [[ "$status_code" != "$expected_status" ]]; then
        result="FAIL"
        reason="expected status $expected_status, got $status_code"
    elif [[ -n "$body_match" ]] && ! echo "$body" | grep -q "$body_match"; then
        result="FAIL"
        reason="body does not contain '$body_match'"
    fi

    if [[ "$result" == "PASS" ]]; then
        echo "  [PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "  [FAIL] $name — $reason"
        echo "         Response: $body"
        FAIL=$((FAIL + 1))
    fi

    # Export body for subsequent tests that need to parse it.
    LAST_BODY="$body"
}

echo "============================================"
echo "  Test Services — Integration Tests"
echo "  Gateway: $BASE_URL"
echo "============================================"
echo ""

# ---------- Health checks ----------
echo "--- Health Checks ---"
run_test "Gateway health" GET "$BASE_URL/health" 200 '"status":"ok"'

echo ""

# ---------- Order: product-001 (should succeed, stock=100) ----------
echo "--- Create Order: product-001 (in stock) ---"
run_test "Create order for product-001" \
    POST "$BASE_URL/api/orders" 201 '"status":"confirmed"' \
    '{"product_id":"product-001","quantity":2}'

# Extract the order ID from the response.
ORDER_ID=$(echo "$LAST_BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "         Created order ID: $ORDER_ID"

echo ""

# ---------- Order: product-003 (should fail, stock=0) ----------
echo "--- Create Order: product-003 (out of stock) ---"
run_test "Create order for product-003 (should be rejected)" \
    POST "$BASE_URL/api/orders" 409 '"status":"rejected"' \
    '{"product_id":"product-003","quantity":1}'

echo ""

# ---------- Query the created order ----------
echo "--- Query Order ---"
if [[ -n "${ORDER_ID:-}" ]]; then
    run_test "Get order $ORDER_ID" \
        GET "$BASE_URL/api/orders/$ORDER_ID" 200 '"status":"confirmed"'
else
    echo "  [SKIP] No order ID to query (previous create may have failed)"
fi

echo ""

# ---------- Query non-existent order ----------
echo "--- Query Non-existent Order ---"
run_test "Get non-existent order" \
    GET "$BASE_URL/api/orders/order-999" 404 '"error"'

echo ""

# ---------- Summary ----------
TOTAL=$((PASS + FAIL))
echo "============================================"
echo "  Results: $PASS/$TOTAL passed"
if [[ $FAIL -gt 0 ]]; then
    echo "  $FAIL test(s) FAILED"
    exit 1
else
    echo "  All tests PASSED"
    exit 0
fi
