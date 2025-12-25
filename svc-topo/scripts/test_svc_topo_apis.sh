#!/bin/bash

# svc-topo API 测试脚本
# 用于测试所有 RESTful API 端点

BASE_URL="http://localhost:8106"

echo "========================================="
echo "svc-topo API 测试脚本"
echo "基础URL: ${BASE_URL}"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
test_api() {
    local name=$1
    local method=$2
    local endpoint=$3
    local data=$4
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -e "${BLUE}[测试 ${TOTAL_TESTS}] ${name}${NC}"
    echo "请求: ${method} ${endpoint}"
    
    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${endpoint}" 2>&1)
    else
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "${data}" 2>&1)
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ 成功 (HTTP ${http_code})${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        # 显示响应的前200个字符
        if [ ${#body} -gt 200 ]; then
            echo "响应: ${body:0:200}..."
        else
            echo "响应: ${body}"
        fi
    else
        echo -e "${RED}✗ 失败 (HTTP ${http_code})${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo "响应: ${body}"
    fi
    echo ""
    echo "-----------------------------------------"
    echo ""
    sleep 0.5
}

# 开始测试
echo -e "${YELLOW}开始测试...${NC}"
echo ""

# ==================== 1. 健康检查 ====================
echo -e "${YELLOW}=== 1. 健康检查 ===${NC}"
echo ""

test_api "Actuator 健康检查" "GET" "/actuator/health"
test_api "Actuator 信息" "GET" "/actuator/info"

# ==================== 2. Prometheus 指标查询 ====================
echo -e "${YELLOW}=== 2. Prometheus 指标查询 ===${NC}"
echo ""

test_api "查询 Pod 指标 (default/test-pod)" "GET" "/api/metrics/pod/default/test-pod"
test_api "查询 Pod 指标 (default/frontend-pod)" "GET" "/api/metrics/pod/default/frontend-pod"
test_api "查询 Node 指标 (worker-node-1)" "GET" "/api/metrics/node/worker-node-1"
test_api "查询 Node 指标 (test-node)" "GET" "/api/metrics/node/test-node"
test_api "查询 Container 指标" "GET" "/api/metrics/container/default/test-pod/app-container"
test_api "查询 Container 指标 (frontend)" "GET" "/api/metrics/container/default/frontend-pod/app"

# ==================== 3. XFlow 拓扑可视化 ====================
echo -e "${YELLOW}=== 3. XFlow 拓扑可视化 ===${NC}"
echo ""

test_api "获取拓扑数据" "GET" "/api/xflow/topology"
test_api "获取自动刷新状态" "GET" "/api/xflow/auto-refresh/status"
test_api "手动触发刷新" "POST" "/api/xflow/auto-refresh/trigger"

# 测试节点详情（使用实际的节点ID）
# 注意：这里使用一个可能存在的节点ID，实际测试时需要替换
# test_api "获取节点详情" "GET" "/api/xflow/nodes/node-1"

# 测试布局算法
LAYOUT_DATA='{
  "algorithm": "dagre",
  "direction": "TB",
  "options": {
    "rankSep": 50,
    "nodeSep": 30
  }
}'
test_api "应用布局算法" "POST" "/api/xflow/layout" "$LAYOUT_DATA"

# 测试 Jaeger 配置更新
JAEGER_CONFIG='{
  "host": "localhost",
  "port": 16685,
  "serviceName": "frontend",
  "operationName": "all",
  "timeRangeMinutes": 15
}'
test_api "更新 Jaeger 配置" "POST" "/api/xflow/auto-refresh/config" "$JAEGER_CONFIG"

# ==================== 4. 拓扑缓存管理 ====================
echo -e "${YELLOW}=== 4. 拓扑缓存管理 ===${NC}"
echo ""

test_api "获取缓存统计" "GET" "/v1/cache/stats"
test_api "查询时间索引缓存 (index=5)" "GET" "/v1/cache/time-index/5"
test_api "查询时间索引缓存 (index=0)" "GET" "/v1/cache/time-index/0"

# 注意：清空缓存会影响其他测试，所以放在最后
# test_api "清空缓存" "DELETE" "/v1/cache/clear"

# ==================== 5. API 查询 ====================
echo -e "${YELLOW}=== 5. API 查询 ===${NC}"
echo ""

test_api "查询命名空间列表" "GET" "/api/namespaces"
test_api "查询系统 API 列表 (systemId=1)" "GET" "/api/topology/1/apis"
test_api "查询根 API 列表 (systemId=1)" "GET" "/api/topology/1/apis/root"
test_api "获取 API 请求负载" "GET" "/api/topology/api-requests"

# ==================== 6. 自动刷新管理 ====================
echo -e "${YELLOW}=== 6. 自动刷新管理 ===${NC}"
echo ""

test_api "启用自动刷新" "POST" "/api/xflow/auto-refresh/enable"
test_api "获取自动刷新状态（启用后）" "GET" "/api/xflow/auto-refresh/status"
test_api "禁用自动刷新" "POST" "/api/xflow/auto-refresh/disable"
test_api "获取自动刷新状态（禁用后）" "GET" "/api/xflow/auto-refresh/status"

# ==================== 测试总结 ====================
echo ""
echo "========================================="
echo -e "${YELLOW}测试总结${NC}"
echo "========================================="
echo -e "总测试数: ${TOTAL_TESTS}"
echo -e "${GREEN}通过: ${PASSED_TESTS}${NC}"
echo -e "${RED}失败: ${FAILED_TESTS}${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！✓${NC}"
    exit 0
else
    echo -e "${RED}部分测试失败！✗${NC}"
    exit 1
fi

