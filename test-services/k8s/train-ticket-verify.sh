#!/usr/bin/env bash
# train-ticket-verify.sh — Train-Ticket proxy-agent 验证脚本
#
# 验证 proxy-agent 对 train-ticket 真实 API 的录制/重放/拦截能力。
# 测试策略：
#   - 部署 proxy-agent，target 指向 train-ticket 的真实服务 pod IP
#   - 通过 proxy pod IP 直接发送真实 API 请求（带 auth token）
#   - 验证录制、重放、拦截在真实 Java 微服务 API 上正常工作
#   - 验证 selector swap 方式的 ClusterIP 劫持可用
#
# Usage:
#   export KUBECONFIG=~/.kube/coroot-config
#   bash train-ticket-verify.sh

set -euo pipefail

NS="train-ticket"
REG="1.94.151.57:85/test"
CURL_IMG="$REG/curl-debug:latest"
PROXY_IMG="$REG/proxy-agent:latest"

TT_HOST="116.63.51.45:32677"
LOGIN_URL="http://${TT_HOST}/api/v1/users/login"
LOGIN_BODY='{"username":"fdse_microservice","password":"111111","verificationCode":"1234"}'

# Route service API path (called by ts-travel-service)
ROUTE_PATH="/api/v1/routeservice/routes"
# Food service API path (full chain test)
FOOD_PATH="/api/v1/foodservice/foods/2026-03-23/shanghai/suzhou/D1345"

PASS=0
FAIL=0
TOTAL=0
TOKEN=""
DEPLOYED_PROXIES=""
HIJACKED_SVC=""  # name of service with swapped selector (at most 1 at a time)

# ─── Helpers ────────────────────────────────────────────────────────────────

log_step() { echo ""; echo "================================================================"; echo "  STEP: $1"; echo "================================================================"; }
log_info() { echo "  [INFO] $1"; }

assert_eq() {
    local name="$1" expected="$2" actual="$3"; TOTAL=$((TOTAL + 1))
    if [[ "$expected" == "$actual" ]]; then echo "  [PASS] $name"; PASS=$((PASS + 1))
    else echo "  [FAIL] $name"; echo "         expected: $expected"; echo "         actual:   $actual"; FAIL=$((FAIL + 1)); fi
}
assert_contains() {
    local name="$1" needle="$2" haystack="$3"; TOTAL=$((TOTAL + 1))
    if echo "$haystack" | grep -q "$needle"; then echo "  [PASS] $name"; PASS=$((PASS + 1))
    else echo "  [FAIL] $name"; echo "         expected to contain: $needle"; echo "         actual: $haystack"; FAIL=$((FAIL + 1)); fi
}
assert_ge() {
    local name="$1" threshold="$2" actual="$3"; TOTAL=$((TOTAL + 1))
    if [[ "$actual" -ge "$threshold" ]] 2>/dev/null; then echo "  [PASS] $name"; PASS=$((PASS + 1))
    else echo "  [FAIL] $name"; echo "         expected >= $threshold, got $actual"; FAIL=$((FAIL + 1)); fi
}

kexec() { kubectl -n "$NS" exec curl-debug -- "$@" 2>/dev/null || echo "EXEC_ERROR"; }

get_token() {
    TOKEN=$(curl -s -X POST "$LOGIN_URL" -H "Content-Type: application/json" -d "$LOGIN_BODY" 2>/dev/null \
        | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null || echo "")
    if [[ -z "$TOKEN" ]]; then echo "  [ERROR] Failed to get token"; return 1; fi
    log_info "Token: ${TOKEN:0:20}..."
}

# Call proxy-agent via pod IP with auth
proxy_call() {
    local proxy_ip="$1" port="$2" path="$3"
    kexec curl -s --connect-timeout 10 --no-keepalive \
        -H "Authorization: Bearer $TOKEN" "http://${proxy_ip}:${port}${path}"
}

# Control API
ctrl() {
    local proxy_ip="$1" method="$2" path="$3" body="${4:-}"
    if [[ -n "$body" ]]; then
        kexec curl -s --connect-timeout 5 -X "$method" "http://${proxy_ip}:9090${path}" \
            -H "Content-Type: application/json" -d "$body"
    else
        kexec curl -s --connect-timeout 5 -X "$method" "http://${proxy_ip}:9090${path}"
    fi
}

get_stat() {
    local proxy_ip="$1" field="$2"
    ctrl "$proxy_ip" GET "/control/stats" | python3 -c "import sys,json; print(json.load(sys.stdin).get('$field',0))" 2>/dev/null || echo "0"
}

deploy_proxy() {
    local name="$1" target_ip="$2" port="$3" mode="${4:-record}"
    log_info "Deploying $name → http://$target_ip:$port (mode=$mode)"
    cat <<EOF | kubectl -n "$NS" apply -f -
apiVersion: apps/v1
kind: Deployment
metadata: { name: $name }
spec:
  replicas: 1
  selector: { matchLabels: { app: $name } }
  template:
    metadata: { labels: { app: $name } }
    spec:
      containers:
      - name: proxy-agent
        image: $PROXY_IMG
        imagePullPolicy: Always
        ports: [{ containerPort: $port }, { containerPort: 9090 }]
        env:
        - { name: PROXY_TARGET, value: "http://${target_ip}:${port}" }
        - { name: PROXY_PORT, value: "${port}" }
        - { name: CONTROL_PORT, value: "9090" }
        - { name: INITIAL_MODE, value: "$mode" }
EOF
    kubectl -n "$NS" wait --for=condition=ready pod -l "app=$name" --timeout=120s
    DEPLOYED_PROXIES="${DEPLOYED_PROXIES:+$DEPLOYED_PROXIES }$name"
}

get_proxy_ip() {
    kubectl -n "$NS" get pod -l "app=$1" -o jsonpath='{.items[0].status.podIP}'
}

recreate_curl_pod() {
    kubectl -n "$NS" delete pod curl-debug --ignore-not-found=true 2>/dev/null || true
    kubectl -n "$NS" run curl-debug --image="$CURL_IMG" --restart=Never 2>/dev/null || true
    kubectl -n "$NS" wait --for=condition=ready pod/curl-debug --timeout=60s
}

cleanup() {
    echo ""; echo "================================================================"; echo "  CLEANUP"; echo "================================================================"
    if [[ -n "${HIJACKED_SVC:-}" ]]; then
        log_info "Restoring $HIJACKED_SVC selector..."
        kubectl -n "$NS" patch svc "$HIJACKED_SVC" --type='merge' \
            -p="{\"spec\":{\"selector\":{\"app\":\"$HIJACKED_SVC\"}}}" 2>/dev/null || true
        sleep 5
    fi
    if [[ -n "${DEPLOYED_PROXIES:-}" ]]; then
        for p in $DEPLOYED_PROXIES; do
            kubectl -n "$NS" delete deploy "$p" --ignore-not-found=true 2>/dev/null || true
        done
    fi
    kubectl -n "$NS" delete pod curl-debug --ignore-not-found=true 2>/dev/null || true
    log_info "Cleaned up."
}
trap cleanup EXIT INT TERM

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 0: BASELINE
# ═══════════════════════════════════════════════════════════════════════════

log_step "0. Baseline"

recreate_curl_pod
get_token

BASELINE=$(curl -s --connect-timeout 10 -H "Authorization: Bearer $TOKEN" \
    "http://${TT_HOST}${FOOD_PATH}" 2>/dev/null)
BL_STATUS=$(echo "$BASELINE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',0))" 2>/dev/null || echo "0")
assert_eq "Food API baseline" "1" "$BL_STATUS"

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 1: RECORD/REPLAY — ts-route-service
# ═══════════════════════════════════════════════════════════════════════════

log_step "1. Record/Replay — ts-route-service (via proxy pod IP)"

ROUTE_ORIG_IP=$(kubectl -n "$NS" get endpoints ts-route-service -o jsonpath='{.subsets[0].addresses[0].ip}')
log_info "ts-route-service pod IP: $ROUTE_ORIG_IP"

deploy_proxy "proxy-route" "$ROUTE_ORIG_IP" 11178 "record"
PROXY_ROUTE_IP=$(get_proxy_ip "proxy-route")
log_info "proxy-route pod IP: $PROXY_ROUTE_IP"

# Health check
HEALTH=$(ctrl "$PROXY_ROUTE_IP" GET "/control/health")
assert_contains "proxy-route healthy" '"status":"ok"' "$HEALTH"

# ── Record: send real train-ticket API through proxy ──
log_info "Recording: GET $ROUTE_PATH/some-route-id through proxy..."
REC_RESP=$(proxy_call "$PROXY_ROUTE_IP" 11178 "$ROUTE_PATH/some-route-id")
log_info "Record response: $(echo "$REC_RESP" | head -c 100)"

sleep 1
REC_COUNT=$(get_stat "$PROXY_ROUTE_IP" "requests_recorded")
assert_ge "Route: recorded" 1 "$REC_COUNT"

SNAP_COUNT=$(get_stat "$PROXY_ROUTE_IP" "snapshot_count")
assert_ge "Route: snapshots" 1 "$SNAP_COUNT"

# ── Replay ──
log_info "Switching to replay..."
ctrl "$PROXY_ROUTE_IP" PUT "/control/mode" '{"mode":"replay"}' > /dev/null

REP_RESP=$(proxy_call "$PROXY_ROUTE_IP" 11178 "$ROUTE_PATH/some-route-id")
log_info "Replay response: $(echo "$REP_RESP" | head -c 100)"

REP_COUNT=$(get_stat "$PROXY_ROUTE_IP" "requests_replayed")
assert_ge "Route: replayed" 1 "$REP_COUNT"

# Compare record vs replay
assert_eq "Route: replay body matches record" "$REC_RESP" "$REP_RESP"

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 2: RECORD/REPLAY — ts-food-service (full chain entry point)
# ═══════════════════════════════════════════════════════════════════════════

log_step "2. Record/Replay — ts-food-service (full chain via proxy pod IP)"

FOOD_ORIG_IP=$(kubectl -n "$NS" get endpoints ts-food-service -o jsonpath='{.subsets[0].addresses[0].ip}')
log_info "ts-food-service pod IP: $FOOD_ORIG_IP"

deploy_proxy "proxy-food" "$FOOD_ORIG_IP" 18856 "record"
PROXY_FOOD_IP=$(get_proxy_ip "proxy-food")
log_info "proxy-food pod IP: $PROXY_FOOD_IP"

# ── Record food API ──
log_info "Recording: food API through proxy..."
FOOD_REC=$(proxy_call "$PROXY_FOOD_IP" 18856 "$FOOD_PATH")
FOOD_REC_STATUS=$(echo "$FOOD_REC" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',0))" 2>/dev/null || echo "0")
assert_eq "Food record: status=1" "1" "$FOOD_REC_STATUS"

sleep 1
FOOD_REC_COUNT=$(get_stat "$PROXY_FOOD_IP" "requests_recorded")
assert_ge "Food: recorded" 1 "$FOOD_REC_COUNT"

# ── Replay food API ──
log_info "Switching to replay..."
ctrl "$PROXY_FOOD_IP" PUT "/control/mode" '{"mode":"replay"}' > /dev/null

FOOD_REP=$(proxy_call "$PROXY_FOOD_IP" 18856 "$FOOD_PATH")
FOOD_REP_STATUS=$(echo "$FOOD_REP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',0))" 2>/dev/null || echo "0")
assert_eq "Food replay: status=1" "1" "$FOOD_REP_STATUS"

FOOD_REP_COUNT=$(get_stat "$PROXY_FOOD_IP" "requests_replayed")
assert_ge "Food: replayed" 1 "$FOOD_REP_COUNT"

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 3: INTERCEPT — inject fault via proxy
# ═══════════════════════════════════════════════════════════════════════════

log_step "3. Intercept — inject fault on food-service"

ctrl "$PROXY_FOOD_IP" PUT "/control/mode" '{"mode":"intercept"}' > /dev/null
ctrl "$PROXY_FOOD_IP" POST "/control/rules" \
    '{"path_match":"/api/v1/foodservice","method":"GET","response":{"status_code":503,"body":"{\"status\":0,\"msg\":\"injected fault\",\"data\":null}"}}' > /dev/null

INTERCEPT_RESP=$(proxy_call "$PROXY_FOOD_IP" 18856 "$FOOD_PATH")
log_info "Intercept response: $(echo "$INTERCEPT_RESP" | head -c 100)"
assert_contains "Intercept: injected fault" '"injected fault"' "$INTERCEPT_RESP"

INT_COUNT=$(get_stat "$PROXY_FOOD_IP" "requests_intercepted")
assert_ge "Food: intercepted" 1 "$INT_COUNT"

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 4: SELECTOR SWAP — verify ClusterIP hijack works
# ═══════════════════════════════════════════════════════════════════════════

log_step "4. Selector swap — verify ClusterIP hijack (ts-route-service)"

# Clear proxy-route snapshots and switch to record
ctrl "$PROXY_ROUTE_IP" DELETE "/control/snapshots" > /dev/null
ctrl "$PROXY_ROUTE_IP" PUT "/control/mode" '{"mode":"record"}' > /dev/null

# Swap selector
log_info "Swapping ts-route-service selector → app=proxy-route"
kubectl -n "$NS" patch svc ts-route-service --type='merge' -p='{"spec":{"selector":{"app":"proxy-route"}}}'
HIJACKED_SVC="ts-route-service"

sleep 15
log_info "Endpoints after swap:"
kubectl -n "$NS" get endpoints ts-route-service

# Recreate curl-debug for fresh conntrack
recreate_curl_pod

# Call via ClusterIP
log_info "Calling ts-route-service via ClusterIP..."
SWAP_RESP=$(kexec curl -s --connect-timeout 10 --no-keepalive \
    "http://ts-route-service:11178${ROUTE_PATH}/test-swap")
log_info "Response: $(echo "$SWAP_RESP" | head -c 100)"

SWAP_REC=$(get_stat "$PROXY_ROUTE_IP" "requests_recorded")
assert_ge "Selector swap: ClusterIP routed to proxy" 1 "$SWAP_REC"

# Restore
log_info "Restoring selector..."
kubectl -n "$NS" patch svc ts-route-service --type='merge' -p='{"spec":{"selector":{"app":"ts-route-service"}}}'
HIJACKED_SVC=""
sleep 8

# ═══════════════════════════════════════════════════════════════════════════
#  STEP 5: VERIFY SYSTEM RESTORED
# ═══════════════════════════════════════════════════════════════════════════

log_step "5. Verify system fully restored"

get_token
FINAL=$(curl -s --connect-timeout 10 -H "Authorization: Bearer $TOKEN" \
    "http://${TT_HOST}${FOOD_PATH}" 2>/dev/null)
FINAL_STATUS=$(echo "$FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',0))" 2>/dev/null || echo "0")
assert_eq "Final: food API recovered" "1" "$FINAL_STATUS"

# ═══════════════════════════════════════════════════════════════════════════
#  SUMMARY
# ═══════════════════════════════════════════════════════════════════════════

echo ""
echo "================================================================"
echo "  TRAIN-TICKET PROXY-AGENT VERIFICATION REPORT"
echo "================================================================"
echo ""
printf "  %-50s %s\n" "Step" "Result"
printf "  %-50s %s\n" "----" "------"
printf "  %-50s %s\n" "0: Baseline" "DONE"
printf "  %-50s %s\n" "1: Record/Replay (ts-route-service)" "DONE"
printf "  %-50s %s\n" "2: Record/Replay (ts-food-service, full chain)" "DONE"
printf "  %-50s %s\n" "3: Intercept (inject fault)" "DONE"
printf "  %-50s %s\n" "4: Selector swap (ClusterIP hijack)" "DONE"
printf "  %-50s %s\n" "5: System restored" "DONE"
echo ""
echo "  Total: $TOTAL  Passed: $PASS  Failed: $FAIL"
echo ""
if [[ $FAIL -gt 0 ]]; then
    echo "  ❌ SOME TESTS FAILED"
    exit 1
else
    echo "  ✅ ALL TESTS PASSED"
    exit 0
fi
