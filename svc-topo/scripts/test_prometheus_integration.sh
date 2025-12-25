#!/bin/bash

# Prometheus 集成测试脚本
# 专门测试 Prometheus 和 Kubernetes 集成功能

BASE_URL="http://localhost:8106"

echo "========================================="
echo "Prometheus 集成功能测试"
echo "基础URL: ${BASE_URL}"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查 jq 是否安装
if ! command -v jq &> /dev/null; then
    echo -e "${RED}错误: 需要安装 jq 工具${NC}"
    echo "请运行: sudo apt-get install jq 或 brew install jq"
    exit 1
fi

# ==================== 1. 测试 Pod 指标查询 ====================
echo -e "${BLUE}1. 测试 Pod 指标查询${NC}"
echo "-----------------------------------------"

POD_RESPONSE=$(curl -s "${BASE_URL}/api/metrics/pod/default/test-pod")

if echo "$POD_RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Pod 指标查询成功${NC}"
    echo ""
    echo "Pod 指标摘要:"
    echo "$POD_RESPONSE" | jq '{
      success: .success,
      namespace: .namespace,
      podName: .podName,
      metrics: {
        cpu: {
          usagePercent: .metrics.resources.cpuUsagePercent,
          usageCores: .metrics.resources.cpuUsageCores,
          limitCores: .metrics.resources.cpuLimitCores
        },
        memory: {
          usageMB: .metrics.resources.memoryUsageMB,
          limitMB: .metrics.resources.memoryLimitMB,
          usagePercent: .metrics.resources.memoryUsagePercent
        },
        health: {
          status: .metrics.health.status,
          restartCount: .metrics.health.restartCount
        }
      }
    }'
else
    echo -e "${RED}✗ Pod 指标查询失败${NC}"
    echo "响应: $POD_RESPONSE"
fi

echo ""
echo ""

# ==================== 2. 测试 Node 指标查询 ====================
echo -e "${BLUE}2. 测试 Node 指标查询${NC}"
echo "-----------------------------------------"

NODE_RESPONSE=$(curl -s "${BASE_URL}/api/metrics/node/worker-node-1")

if echo "$NODE_RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Node 指标查询成功${NC}"
    echo ""
    echo "Node 指标摘要:"
    echo "$NODE_RESPONSE" | jq '{
      success: .success,
      nodeName: .nodeName,
      metrics: {
        cpu: {
          usagePercent: .metrics.resources.cpuUsagePercent,
          usageCores: .metrics.resources.cpuUsageCores,
          limitCores: .metrics.resources.cpuLimitCores
        },
        memory: {
          usageMB: .metrics.resources.memoryUsageMB,
          limitMB: .metrics.resources.memoryLimitMB,
          usagePercent: .metrics.resources.memoryUsagePercent
        },
        network: {
          receiveBytesPerSec: .metrics.resources.networkReceiveBytesPerSec,
          transmitBytesPerSec: .metrics.resources.networkTransmitBytesPerSec
        },
        health: {
          status: .metrics.health.status
        }
      }
    }'
else
    echo -e "${RED}✗ Node 指标查询失败${NC}"
    echo "响应: $NODE_RESPONSE"
fi

echo ""
echo ""

# ==================== 3. 测试 Container 指标查询 ====================
echo -e "${BLUE}3. 测试 Container 指标查询${NC}"
echo "-----------------------------------------"

CONTAINER_RESPONSE=$(curl -s "${BASE_URL}/api/metrics/container/default/test-pod/app-container")

if echo "$CONTAINER_RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Container 指标查询成功${NC}"
    echo ""
    echo "Container 指标摘要:"
    echo "$CONTAINER_RESPONSE" | jq '{
      success: .success,
      namespace: .namespace,
      podName: .podName,
      containerName: .containerName,
      metrics: {
        cpu: {
          usagePercent: .metrics.resources.cpuUsagePercent,
          usageCores: .metrics.resources.cpuUsageCores
        },
        memory: {
          usageMB: .metrics.resources.memoryUsageMB,
          usagePercent: .metrics.resources.memoryUsagePercent
        },
        health: {
          status: .metrics.health.status,
          restartCount: .metrics.health.restartCount
        }
      }
    }'
else
    echo -e "${RED}✗ Container 指标查询失败${NC}"
    echo "响应: $CONTAINER_RESPONSE"
fi

echo ""
echo ""

# ==================== 4. 测试拓扑数据中的 Prometheus 指标 ====================
echo -e "${BLUE}4. 测试拓扑数据中的 Prometheus 指标${NC}"
echo "-----------------------------------------"

TOPOLOGY_RESPONSE=$(curl -s "${BASE_URL}/api/xflow/topology")

if echo "$TOPOLOGY_RESPONSE" | jq -e '.nodes' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 拓扑数据获取成功${NC}"
    echo ""
    
    # 统计节点数量
    TOTAL_NODES=$(echo "$TOPOLOGY_RESPONSE" | jq '.nodes | length')
    NODES_WITH_PROMETHEUS=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.attributes.prometheus != null)] | length')
    NODES_WITH_KUBERNETES=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.attributes.kubernetes != null)] | length')
    
    echo "拓扑统计:"
    echo "  总节点数: $TOTAL_NODES"
    echo "  包含 Prometheus 指标的节点: $NODES_WITH_PROMETHEUS"
    echo "  包含 Kubernetes 元数据的节点: $NODES_WITH_KUBERNETES"
    echo ""
    
    # 显示包含 Prometheus 指标的节点（最多显示5个）
    echo "包含 Prometheus 指标的节点示例（最多5个）:"
    echo "$TOPOLOGY_RESPONSE" | jq -r '.nodes[] | select(.data.attributes.prometheus != null) | {
      id: .id,
      label: .label,
      entityType: .data.entityType,
      cpuUsage: .data.attributes.prometheus.resources.cpuUsagePercent,
      memoryUsage: .data.attributes.prometheus.resources.memoryUsageMB,
      healthStatus: .data.attributes.prometheus.health.status
    }' | head -30
    
    echo ""
    
    # 显示 NODE 类型的节点
    NODE_COUNT=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.entityType == "NODE")] | length')
    echo "NODE 类型节点数量: $NODE_COUNT"
    
    if [ "$NODE_COUNT" -gt 0 ]; then
        echo ""
        echo "NODE 类型节点详情:"
        echo "$TOPOLOGY_RESPONSE" | jq -r '.nodes[] | select(.data.entityType == "NODE") | {
          id: .id,
          label: .label,
          entityType: .data.entityType,
          level: .data.level,
          hasPrometheus: (.data.attributes.prometheus != null),
          hasKubernetes: (.data.attributes.kubernetes != null)
        }'
    fi
else
    echo -e "${RED}✗ 拓扑数据获取失败${NC}"
    echo "响应: $TOPOLOGY_RESPONSE"
fi

echo ""
echo ""

# ==================== 5. 测试数据源编排 ====================
echo -e "${BLUE}5. 测试数据源编排（Jaeger + Prometheus + Kubernetes）${NC}"
echo "-----------------------------------------"

# 触发手动刷新以确保数据是最新的
echo "触发拓扑数据刷新..."
REFRESH_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/xflow/auto-refresh/trigger")

if echo "$REFRESH_RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 拓扑刷新成功${NC}"
    echo ""
    
    # 等待刷新完成
    sleep 2
    
    # 重新获取拓扑数据
    TOPOLOGY_RESPONSE=$(curl -s "${BASE_URL}/api/xflow/topology")
    
    # 检查数据源集成情况
    echo "数据源集成检查:"
    
    # 检查是否有 RED 指标（来自 Jaeger）
    NODES_WITH_RED=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.attributes.red != null)] | length')
    echo "  包含 RED 指标的节点（Jaeger）: $NODES_WITH_RED"
    
    # 检查是否有 Prometheus 指标
    NODES_WITH_PROMETHEUS=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.attributes.prometheus != null)] | length')
    echo "  包含 Prometheus 指标的节点: $NODES_WITH_PROMETHEUS"
    
    # 检查是否有 Kubernetes 元数据
    NODES_WITH_KUBERNETES=$(echo "$TOPOLOGY_RESPONSE" | jq '[.nodes[] | select(.data.attributes.kubernetes != null)] | length')
    echo "  包含 Kubernetes 元数据的节点: $NODES_WITH_KUBERNETES"
    
    echo ""
    
    # 显示一个完整集成的节点示例
    echo "完整集成节点示例（同时包含 RED、Prometheus、Kubernetes 数据）:"
    echo "$TOPOLOGY_RESPONSE" | jq -r '.nodes[] | select(
      .data.attributes.red != null and 
      .data.attributes.prometheus != null and 
      .data.attributes.kubernetes != null
    ) | {
      id: .id,
      label: .label,
      entityType: .data.entityType,
      red: .data.attributes.red,
      prometheus: .data.attributes.prometheus,
      kubernetes: .data.attributes.kubernetes
    }' | head -50
    
else
    echo -e "${RED}✗ 拓扑刷新失败${NC}"
    echo "响应: $REFRESH_RESPONSE"
fi

echo ""
echo ""

# ==================== 测试总结 ====================
echo "========================================="
echo -e "${YELLOW}Prometheus 集成测试总结${NC}"
echo "========================================="
echo ""
echo "测试项目:"
echo "  1. Pod 指标查询"
echo "  2. Node 指标查询"
echo "  3. Container 指标查询"
echo "  4. 拓扑数据中的 Prometheus 指标"
echo "  5. 数据源编排（Jaeger + Prometheus + Kubernetes）"
echo ""
echo -e "${GREEN}测试完成！${NC}"
echo ""
echo "注意事项:"
echo "  - 如果看到连接错误，请确保应用在 Kubernetes 集群内运行"
echo "  - 或者使用端口转发: kubectl port-forward -n monitoring svc/prometheus 9090:9090"
echo "  - 检查 application.yml 中的 Prometheus 和 Kubernetes 配置"
echo ""

