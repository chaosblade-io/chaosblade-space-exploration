# svc-topo RESTful API 测试指南

本文档提供了 svc-topo 服务所有 RESTful API 的完整测试示例，包括新实现的 Prometheus 和 Kubernetes 集成功能。

## 目录

- [服务信息](#服务信息)
- [1. Prometheus 指标查询 API](#1-prometheus-指标查询-api)
- [2. XFlow 拓扑可视化 API](#2-xflow-拓扑可视化-api)
- [3. 拓扑缓存管理 API](#3-拓扑缓存管理-api)
- [4. API 查询 API](#4-api-查询-api)
- [5. 健康检查 API](#5-健康检查-api)

---

## 服务信息

- **服务名称**: svc-topo (Topology Service)
- **默认端口**: 8106
- **基础URL**: `http://localhost:8106`
- **Context Path**: `/`

---

## 1. Prometheus 指标查询 API

### 1.1 查询 Pod 指标

**端点**: `GET /api/metrics/pod/{namespace}/{podName}`

**描述**: 查询指定命名空间和 Pod 名称的 Prometheus 指标数据

**路径参数**:
- `namespace` (string): Kubernetes 命名空间
- `podName` (string): Pod 名称

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/metrics/pod/default/test-pod"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/metrics/pod/default/test-pod" | jq .

# 使用 HTTPie
http GET "http://localhost:8106/api/metrics/pod/default/test-pod"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "namespace": "default",
  "podName": "test-pod",
  "metrics": {
    "resources": {
      "cpuUsagePercent": 45.5,
      "cpuUsageCores": 0.455,
      "cpuLimitCores": 1.0,
      "memoryUsageBytes": 536870912,
      "memoryUsageMB": 512.0,
      "memoryLimitBytes": 1073741824,
      "memoryLimitMB": 1024.0,
      "memoryUsagePercent": 50.0,
      "networkReceiveBytesPerSec": 1024000,
      "networkTransmitBytesPerSec": 2048000,
      "diskReadBytesPerSec": 512000,
      "diskWriteBytesPerSec": 1024000
    },
    "performance": {
      "requestRate": 100.5,
      "errorRate": 2.3,
      "errorPercent": 2.29,
      "latencyP50": 50.0,
      "latencyP90": 120.0,
      "latencyP95": 180.0,
      "latencyP99": 300.0,
      "concurrentRequests": 25
    },
    "health": {
      "status": "HEALTHY",
      "restartCount": 0
    },
    "custom": {},
    "timestamp": 1699876543210
  },
  "timestamp": 1699876543210
}
```

**错误响应示例** (HTTP 500):
```json
{
  "success": false,
  "error": "Failed to query Pod metrics",
  "message": "Connection refused: prometheus.monitoring.svc.cluster.local:9090"
}
```

---

### 1.2 查询 Node 指标

**端点**: `GET /api/metrics/node/{nodeName}`

**描述**: 查询指定 Kubernetes Node 的 Prometheus 指标数据

**路径参数**:
- `nodeName` (string): Node 名称

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/metrics/node/worker-node-1"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/metrics/node/worker-node-1" | jq .

# 使用 HTTPie
http GET "http://localhost:8106/api/metrics/node/worker-node-1"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "nodeName": "worker-node-1",
  "metrics": {
    "resources": {
      "cpuUsagePercent": 65.2,
      "cpuUsageCores": 2.608,
      "cpuLimitCores": 4.0,
      "memoryUsageBytes": 8589934592,
      "memoryUsageMB": 8192.0,
      "memoryLimitBytes": 17179869184,
      "memoryLimitMB": 16384.0,
      "memoryUsagePercent": 50.0,
      "networkReceiveBytesPerSec": 5120000,
      "networkTransmitBytesPerSec": 10240000,
      "diskReadBytesPerSec": 2048000,
      "diskWriteBytesPerSec": 4096000
    },
    "performance": null,
    "health": {
      "status": "HEALTHY",
      "restartCount": 0
    },
    "custom": {},
    "timestamp": 1699876543210
  },
  "timestamp": 1699876543210
}
```

---

### 1.3 查询 Container 指标

**端点**: `GET /api/metrics/container/{namespace}/{podName}/{containerName}`

**描述**: 查询指定容器的 Prometheus 指标数据

**路径参数**:
- `namespace` (string): Kubernetes 命名空间
- `podName` (string): Pod 名称
- `containerName` (string): 容器名称

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/metrics/container/default/test-pod/app-container"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/metrics/container/default/test-pod/app-container" | jq .

# 使用 HTTPie
http GET "http://localhost:8106/api/metrics/container/default/test-pod/app-container"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "namespace": "default",
  "podName": "test-pod",
  "containerName": "app-container",
  "metrics": {
    "resources": {
      "cpuUsagePercent": 35.8,
      "cpuUsageCores": 0.358,
      "cpuLimitCores": 1.0,
      "memoryUsageBytes": 268435456,
      "memoryUsageMB": 256.0,
      "memoryLimitBytes": 536870912,
      "memoryLimitMB": 512.0,
      "memoryUsagePercent": 50.0,
      "networkReceiveBytesPerSec": null,
      "networkTransmitBytesPerSec": null,
      "diskReadBytesPerSec": null,
      "diskWriteBytesPerSec": null
    },
    "performance": null,
    "health": {
      "status": "HEALTHY",
      "restartCount": 0
    },
    "custom": {},
    "timestamp": 1699876543210
  },
  "timestamp": 1699876543210
}
```

---

## 2. XFlow 拓扑可视化 API

### 2.1 获取当前拓扑数据

**端点**: `GET /api/xflow/topology`

**描述**: 获取当前拓扑的 XFlow 格式数据，用于前端可视化

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/xflow/topology"

# 使用 curl 并格式化输出（仅显示前100行）
curl -s "http://localhost:8106/api/xflow/topology" | jq . | head -100

# 使用 HTTPie
http GET "http://localhost:8106/api/xflow/topology"

# 保存到文件
curl -s "http://localhost:8106/api/xflow/topology" > topology.json
```

**成功响应示例** (HTTP 200):
```json
{
  "nodes": [
    {
      "id": "node-1",
      "shape": "custom-service",
      "label": "frontend",
      "data": {
        "entityType": "SERVICE",
        "displayName": "frontend",
        "namespace": "default",
        "level": 1,
        "attributes": {
          "red": {
            "requestRate": 150.5,
            "errorRate": 3.2,
            "duration": 85.3
          },
          "prometheus": {
            "resources": {
              "cpuUsagePercent": 45.5,
              "memoryUsageMB": 512.0
            },
            "health": {
              "status": "HEALTHY"
            }
          }
        }
      },
      "x": 100,
      "y": 100
    },
    {
      "id": "node-2",
      "shape": "custom-node",
      "label": "worker-node-1",
      "data": {
        "entityType": "NODE",
        "displayName": "worker-node-1",
        "level": 2,
        "attributes": {
          "kubernetes": {
            "labels": {
              "node-role.kubernetes.io/worker": "true"
            }
          },
          "prometheus": {
            "resources": {
              "cpuUsagePercent": 65.2,
              "memoryUsageMB": 8192.0
            }
          }
        }
      },
      "x": 200,
      "y": 200
    }
  ],
  "edges": [
    {
      "id": "edge-1",
      "source": "node-1",
      "target": "node-2",
      "label": "CALLS",
      "data": {
        "relationType": "CALLS",
        "weight": 5,
        "attributes": {
          "callCount": 1500,
          "avgLatency": 85.3
        }
      }
    }
  ]
}
```

**空数据响应** (HTTP 204):
```json
{
  "nodes": [],
  "edges": []
}
```

---

### 2.2 刷新拓扑数据

**端点**: `POST /api/xflow/refresh`

**描述**: 手动触发拓扑数据刷新，从 Jaeger 重新获取 trace 数据并重建拓扑

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/refresh"

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/refresh"
```

**成功响应示例** (HTTP 200):
```json
{
  "nodes": [...],
  "edges": [...]
}
```

---

### 2.3 获取节点详情

**端点**: `GET /api/xflow/nodes/{nodeId}`

**描述**: 获取指定节点的详细信息

**路径参数**:
- `nodeId` (string): 节点ID

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/xflow/nodes/node-1"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/xflow/nodes/node-1" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "id": "node-1",
  "entityType": "SERVICE",
  "displayName": "frontend",
  "namespace": "default",
  "level": 1,
  "attributes": {
    "red": {
      "requestRate": 150.5,
      "errorRate": 3.2,
      "duration": 85.3
    },
    "prometheus": {
      "resources": {
        "cpuUsagePercent": 45.5,
        "memoryUsageMB": 512.0
      }
    },
    "kubernetes": {
      "labels": {
        "app": "frontend",
        "version": "v1"
      }
    }
  }
}
```

**节点不存在响应** (HTTP 404):
```json
{
  "error": "节点不存在",
  "nodeId": "non-existent-node"
}
```

---

### 2.4 应用布局算法

**端点**: `POST /api/xflow/layout`

**描述**: 对拓扑图应用指定的布局算法

**请求体**:
```json
{
  "algorithm": "dagre",
  "direction": "TB",
  "options": {
    "rankSep": 50,
    "nodeSep": 30
  }
}
```

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/layout" \
  -H "Content-Type: application/json" \
  -d '{
    "algorithm": "dagre",
    "direction": "TB",
    "options": {
      "rankSep": 50,
      "nodeSep": 30
    }
  }'

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/layout" \
  algorithm=dagre \
  direction=TB \
  options:='{"rankSep": 50, "nodeSep": 30}'
```

**成功响应示例** (HTTP 200):
```json
{
  "nodes": [...],
  "edges": [...]
}
```

---

### 2.5 获取自动刷新状态

**端点**: `GET /api/xflow/auto-refresh/status`

**描述**: 获取拓扑自动刷新的当前状态

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/xflow/auto-refresh/status"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/xflow/auto-refresh/status" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "status": {
    "enabled": true,
    "intervalSeconds": 60,
    "lastRefreshTime": 1699876543210,
    "nextRefreshTime": 1699876603210,
    "refreshCount": 42,
    "jaegerConfig": {
      "host": "localhost",
      "port": 16685,
      "serviceName": "frontend",
      "operationName": "all",
      "timeRangeMinutes": 15
    }
  },
  "timestamp": 1699876543210
}
```

---

### 2.6 手动触发刷新

**端点**: `POST /api/xflow/auto-refresh/trigger`

**描述**: 手动触发一次拓扑数据刷新

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/auto-refresh/trigger"

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/auto-refresh/trigger"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "message": "手动刷新完成",
  "timestamp": 1699876543210
}
```

---

### 2.7 启用自动刷新

**端点**: `POST /api/xflow/auto-refresh/enable`

**描述**: 启用拓扑数据的自动刷新功能

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/auto-refresh/enable"

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/auto-refresh/enable"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "message": "自动刷新已启用",
  "timestamp": 1699876543210
}
```

---

### 2.8 禁用自动刷新

**端点**: `POST /api/xflow/auto-refresh/disable`

**描述**: 禁用拓扑数据的自动刷新功能

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/auto-refresh/disable"

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/auto-refresh/disable"
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "message": "自动刷新已禁用",
  "timestamp": 1699876543210
}
```

---

### 2.9 更新 Jaeger 配置

**端点**: `POST /api/xflow/auto-refresh/config`

**描述**: 更新 Jaeger 数据源的配置参数

**请求体**:
```json
{
  "host": "jaeger-query.monitoring.svc.cluster.local",
  "port": 16686,
  "serviceName": "frontend",
  "operationName": "all",
  "timeRangeMinutes": 30
}
```

**请求示例**:
```bash
# 使用 curl
curl -X POST "http://localhost:8106/api/xflow/auto-refresh/config" \
  -H "Content-Type: application/json" \
  -d '{
    "host": "jaeger-query.monitoring.svc.cluster.local",
    "port": 16686,
    "serviceName": "frontend",
    "operationName": "all",
    "timeRangeMinutes": 30
  }'

# 使用 HTTPie
http POST "http://localhost:8106/api/xflow/auto-refresh/config" \
  host=jaeger-query.monitoring.svc.cluster.local \
  port:=16686 \
  serviceName=frontend \
  operationName=all \
  timeRangeMinutes:=30
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "message": "Jaeger 配置已更新",
  "config": {
    "host": "jaeger-query.monitoring.svc.cluster.local",
    "port": 16686,
    "serviceName": "frontend",
    "operationName": "all",
    "timeRangeMinutes": 30
  },
  "timestamp": 1699876543210
}
```

---

## 3. 拓扑缓存管理 API

### 3.1 获取缓存统计信息

**端点**: `GET /v1/cache/stats`

**描述**: 获取拓扑缓存的统计信息

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/v1/cache/stats"

# 使用 HTTPie
http GET "http://localhost:8106/v1/cache/stats"
```

**成功响应示例** (HTTP 200):
```text
Cache Statistics:
Total entries: 15
Cache hit rate: 85.5%
Total hits: 342
Total misses: 58
Oldest entry: 2024-11-08 10:00:00
Newest entry: 2024-11-08 14:30:00
```

---

### 3.2 清空缓存

**端点**: `DELETE /v1/cache/clear`

**描述**: 清空所有拓扑缓存数据

**请求示例**:
```bash
# 使用 curl
curl -X DELETE "http://localhost:8106/v1/cache/clear"

# 使用 HTTPie
http DELETE "http://localhost:8106/v1/cache/clear"
```

**成功响应示例** (HTTP 200):
```text
缓存已清空
```

---

### 3.3 按时间索引查询缓存

**端点**: `GET /v1/cache/time-index/{timeIndex}`

**描述**: 按时间索引查询缓存项数量

**路径参数**:
- `timeIndex` (integer): 时间索引，范围 0-14

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/v1/cache/time-index/5"

# 使用 HTTPie
http GET "http://localhost:8106/v1/cache/time-index/5"
```

**成功响应示例** (HTTP 200):
```text
3
```

**错误响应示例** (HTTP 400):
```text
Bad Request
```

---

## 4. API 查询 API

### 4.1 查询命名空间列表

**端点**: `GET /api/namespaces`

**描述**: 获取当前拓扑中所有的命名空间列表

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/namespaces"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/namespaces" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "namespaces": ["default", "kube-system", "monitoring"],
    "total": 3
  }
}
```

---

### 4.2 查询系统 API 列表

**端点**: `GET /api/topology/{systemId}/apis`

**描述**: 获取指定系统ID的所有 API 列表

**路径参数**:
- `systemId` (long): 系统ID

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/topology/1/apis"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/topology/1/apis" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "apis": [
      {
        "id": 1,
        "systemId": 1,
        "k8sNamespace": "default",
        "operationId": "GET /api/users",
        "method": "GET",
        "path": "/api/users",
        "summary": "获取用户列表",
        "description": "返回所有用户的列表",
        "version": "v1",
        "tags": ["users", "api"],
        "requestBody": null,
        "responses": {
          "200": {
            "description": "成功",
            "content": "application/json"
          }
        }
      },
      {
        "id": 2,
        "systemId": 1,
        "k8sNamespace": "default",
        "operationId": "POST /api/orders",
        "method": "POST",
        "path": "/api/orders",
        "summary": "创建订单",
        "description": "创建新的订单",
        "version": "v1",
        "tags": ["orders", "api"],
        "requestBody": {
          "content": "application/json",
          "required": true
        },
        "responses": {
          "201": {
            "description": "创建成功",
            "content": "application/json"
          }
        }
      }
    ],
    "total": 2
  }
}
```

---

### 4.3 查询根 API 列表

**端点**: `GET /api/topology/{systemId}/apis/root`

**描述**: 获取指定系统ID的根 API 列表（入口 API）

**路径参数**:
- `systemId` (long): 系统ID

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/topology/1/apis/root"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/topology/1/apis/root" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "rootApis": [
      {
        "operationId": "GET /",
        "method": "GET",
        "path": "/",
        "summary": "首页",
        "tags": ["root"]
      }
    ],
    "total": 1
  }
}
```

---

### 4.4 获取 API 请求负载列表

**端点**: `GET /api/topology/api-requests`

**描述**: 获取所有 API 的请求负载配置

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/api/topology/api-requests"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/api/topology/api-requests" | jq .
```

**成功响应示例** (HTTP 200):
```json
[
  {
    "apiId": 1,
    "method": "GET",
    "path": "/api/users",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer token123"
    },
    "queryParams": {
      "page": "1",
      "size": "10"
    },
    "body": null
  },
  {
    "apiId": 2,
    "method": "POST",
    "path": "/api/orders",
    "headers": {
      "Content-Type": "application/json"
    },
    "queryParams": {},
    "body": {
      "productId": "12345",
      "quantity": 2,
      "customerId": "user-001"
    }
  }
]
```

---

## 5. 健康检查 API

### 5.1 Spring Boot Actuator 健康检查

**端点**: `GET /actuator/health`

**描述**: Spring Boot Actuator 提供的健康检查端点

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/actuator/health"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/actuator/health" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 250053931008,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

### 5.2 Actuator 信息端点

**端点**: `GET /actuator/info`

**描述**: 获取应用程序信息

**请求示例**:
```bash
# 使用 curl
curl -X GET "http://localhost:8106/actuator/info"

# 使用 curl 并格式化输出
curl -s "http://localhost:8106/actuator/info" | jq .
```

**成功响应示例** (HTTP 200):
```json
{
  "app": {
    "name": "chaosblade-topo",
    "version": "1.0.0",
    "description": "Topology Service with Prometheus and Kubernetes Integration"
  }
}
```

---

## 6. 完整测试脚本

### 6.1 基础功能测试脚本

创建一个 bash 脚本来测试所有基础功能：

```bash
#!/bin/bash

# svc-topo API 测试脚本
BASE_URL="http://localhost:8106"

echo "========================================="
echo "svc-topo API 测试脚本"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试函数
test_api() {
    local name=$1
    local method=$2
    local endpoint=$3
    local data=$4

    echo -e "${YELLOW}测试: ${name}${NC}"
    echo "请求: ${method} ${endpoint}"

    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${endpoint}")
    else
        response=$(curl -s -w "\n%{http_code}" -X ${method} "${BASE_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "${data}")
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ 成功 (HTTP ${http_code})${NC}"
        echo "响应: ${body}" | head -c 200
        echo "..."
    else
        echo -e "${RED}✗ 失败 (HTTP ${http_code})${NC}"
        echo "响应: ${body}"
    fi
    echo ""
    echo "-----------------------------------------"
    echo ""
}

# 1. 健康检查
test_api "健康检查" "GET" "/actuator/health"

# 2. Prometheus 指标查询
test_api "查询 Pod 指标" "GET" "/api/metrics/pod/default/test-pod"
test_api "查询 Node 指标" "GET" "/api/metrics/node/worker-node-1"
test_api "查询 Container 指标" "GET" "/api/metrics/container/default/test-pod/app-container"

# 3. XFlow 拓扑可视化
test_api "获取拓扑数据" "GET" "/api/xflow/topology"
test_api "获取自动刷新状态" "GET" "/api/xflow/auto-refresh/status"
test_api "手动触发刷新" "POST" "/api/xflow/auto-refresh/trigger"

# 4. 缓存管理
test_api "获取缓存统计" "GET" "/v1/cache/stats"
test_api "查询时间索引缓存" "GET" "/v1/cache/time-index/5"

# 5. API 查询
test_api "查询命名空间列表" "GET" "/api/namespaces"
test_api "查询系统 API 列表" "GET" "/api/topology/1/apis"
test_api "查询根 API 列表" "GET" "/api/topology/1/apis/root"
test_api "获取 API 请求负载" "GET" "/api/topology/api-requests"

echo "========================================="
echo "测试完成"
echo "========================================="
```

保存为 `test_svc_topo_apis.sh`，然后运行：

```bash
chmod +x test_svc_topo_apis.sh
./test_svc_topo_apis.sh
```

---

### 6.2 Prometheus 集成测试脚本

专门测试 Prometheus 集成功能的脚本：

```bash
#!/bin/bash

# Prometheus 集成测试脚本
BASE_URL="http://localhost:8106"

echo "========================================="
echo "Prometheus 集成功能测试"
echo "========================================="
echo ""

# 测试 Pod 指标
echo "1. 测试 Pod 指标查询"
echo "-----------------------------------------"
curl -s "${BASE_URL}/api/metrics/pod/default/frontend-pod" | jq '{
  success: .success,
  podName: .podName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsageMB,
  healthStatus: .metrics.health.status
}'
echo ""

# 测试 Node 指标
echo "2. 测试 Node 指标查询"
echo "-----------------------------------------"
curl -s "${BASE_URL}/api/metrics/node/worker-node-1" | jq '{
  success: .success,
  nodeName: .nodeName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsageMB,
  healthStatus: .metrics.health.status
}'
echo ""

# 测试 Container 指标
echo "3. 测试 Container 指标查询"
echo "-----------------------------------------"
curl -s "${BASE_URL}/api/metrics/container/default/frontend-pod/app" | jq '{
  success: .success,
  containerName: .containerName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsageMB,
  restartCount: .metrics.health.restartCount
}'
echo ""

# 测试拓扑数据中的 Prometheus 指标
echo "4. 测试拓扑数据中的 Prometheus 指标"
echo "-----------------------------------------"
curl -s "${BASE_URL}/api/xflow/topology" | jq '.nodes[] | select(.data.attributes.prometheus != null) | {
  id: .id,
  label: .label,
  entityType: .data.entityType,
  cpuUsage: .data.attributes.prometheus.resources.cpuUsagePercent,
  memoryUsage: .data.attributes.prometheus.resources.memoryUsageMB,
  healthStatus: .data.attributes.prometheus.health.status
}' | head -50
echo ""

echo "========================================="
echo "Prometheus 集成测试完成"
echo "========================================="
```

保存为 `test_prometheus_integration.sh`

---

### 6.3 使用 Postman 测试

#### 导入 Postman Collection

创建一个 Postman Collection JSON 文件：

```json
{
  "info": {
    "name": "svc-topo API Collection",
    "description": "Complete API collection for svc-topo service",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Prometheus Metrics",
      "item": [
        {
          "name": "Get Pod Metrics",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/metrics/pod/default/test-pod",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "metrics", "pod", "default", "test-pod"]
            }
          }
        },
        {
          "name": "Get Node Metrics",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/metrics/node/worker-node-1",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "metrics", "node", "worker-node-1"]
            }
          }
        },
        {
          "name": "Get Container Metrics",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/metrics/container/default/test-pod/app-container",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "metrics", "container", "default", "test-pod", "app-container"]
            }
          }
        }
      ]
    },
    {
      "name": "XFlow Topology",
      "item": [
        {
          "name": "Get Topology",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/xflow/topology",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "xflow", "topology"]
            }
          }
        },
        {
          "name": "Refresh Topology",
          "request": {
            "method": "POST",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/xflow/refresh",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "xflow", "refresh"]
            }
          }
        },
        {
          "name": "Get Auto-Refresh Status",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/xflow/auto-refresh/status",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "xflow", "auto-refresh", "status"]
            }
          }
        },
        {
          "name": "Trigger Manual Refresh",
          "request": {
            "method": "POST",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/api/xflow/auto-refresh/trigger",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["api", "xflow", "auto-refresh", "trigger"]
            }
          }
        }
      ]
    },
    {
      "name": "Cache Management",
      "item": [
        {
          "name": "Get Cache Stats",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/v1/cache/stats",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["v1", "cache", "stats"]
            }
          }
        },
        {
          "name": "Clear Cache",
          "request": {
            "method": "DELETE",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/v1/cache/clear",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["v1", "cache", "clear"]
            }
          }
        }
      ]
    },
    {
      "name": "Health Check",
      "item": [
        {
          "name": "Actuator Health",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "http://localhost:8106/actuator/health",
              "protocol": "http",
              "host": ["localhost"],
              "port": "8106",
              "path": ["actuator", "health"]
            }
          }
        }
      ]
    }
  ]
}
```

保存为 `svc-topo-postman-collection.json`，然后在 Postman 中导入。

---

## 7. 测试检查清单

### 7.1 Prometheus 集成功能测试

- [ ] **Pod 指标查询**
  - [ ] 查询存在的 Pod 返回正确的指标数据
  - [ ] 查询不存在的 Pod 返回适当的错误
  - [ ] CPU 使用率数据正确
  - [ ] 内存使用率数据正确
  - [ ] 健康状态正确

- [ ] **Node 指标查询**
  - [ ] 查询存在的 Node 返回正确的指标数据
  - [ ] Node 资源使用率数据正确
  - [ ] 网络流量数据正确

- [ ] **Container 指标查询**
  - [ ] 查询存在的 Container 返回正确的指标数据
  - [ ] Container 资源限制数据正确
  - [ ] 重启次数统计正确

### 7.2 拓扑可视化功能测试

- [ ] **拓扑数据获取**
  - [ ] 获取拓扑数据返回正确的节点和边
  - [ ] 新的 NODE 实体类型正确显示
  - [ ] Prometheus 指标正确附加到节点
  - [ ] Kubernetes 元数据正确附加到节点

- [ ] **拓扑刷新**
  - [ ] 手动刷新功能正常工作
  - [ ] 自动刷新可以启用/禁用
  - [ ] 刷新状态查询正确

- [ ] **节点详情**
  - [ ] 节点详情查询返回完整信息
  - [ ] 包含 RED 指标
  - [ ] 包含 Prometheus 指标
  - [ ] 包含 Kubernetes 元数据

### 7.3 缓存管理功能测试

- [ ] **缓存统计**
  - [ ] 缓存统计信息正确
  - [ ] 缓存命中率计算正确

- [ ] **缓存清空**
  - [ ] 清空缓存功能正常工作
  - [ ] 清空后统计信息重置

### 7.4 数据源集成测试

- [ ] **Jaeger 集成**
  - [ ] 从 Jaeger 获取 trace 数据
  - [ ] Trace 数据正确转换为拓扑

- [ ] **Prometheus 集成**
  - [ ] 从 Prometheus 查询指标数据
  - [ ] 指标数据正确附加到拓扑节点

- [ ] **Kubernetes 集成**
  - [ ] 从 Kubernetes API 获取元数据
  - [ ] 元数据正确附加到拓扑节点

- [ ] **并行数据获取**
  - [ ] 三个数据源并行查询
  - [ ] 性能符合预期

---

## 8. 常见问题排查

### 8.1 连接错误

**问题**: 无法连接到 Prometheus 或 Kubernetes API

**错误信息**:
```
Connection refused: prometheus.monitoring.svc.cluster.local:9090
```

**解决方案**:
1. 检查应用是否在 Kubernetes 集群内运行
2. 如果在集群外运行，需要使用端口转发：
   ```bash
   kubectl port-forward -n monitoring svc/prometheus 9090:9090
   ```
3. 更新 `application.yml` 中的 Prometheus 配置为 `localhost:9090`

---

### 8.2 空数据返回

**问题**: API 返回空的指标数据

**可能原因**:
1. Prometheus 中没有对应的指标数据
2. Pod/Node 名称不正确
3. 时间范围内没有数据

**排查步骤**:
1. 直接访问 Prometheus UI 检查数据
2. 检查 Pod/Node 名称是否正确
3. 查看应用日志了解详细错误

---

### 8.3 性能问题

**问题**: API 响应缓慢

**优化建议**:
1. 启用拓扑缓存
2. 调整 Prometheus 查询的时间范围
3. 使用并行查询
4. 增加应用的资源限制

---

## 9. 附录

### 9.1 实体类型说明

| 实体类型 | Level | 描述 |
|---------|-------|------|
| CLUSTER | 0 | Kubernetes 集群 |
| NAMESPACE | 1 | Kubernetes 命名空间 |
| SERVICE | 1 | 服务 |
| EXTERNAL_SERVICE | 1 | 外部服务 |
| MIDDLEWARE | 1 | 中间件 |
| NODE | 2 | Kubernetes Node |
| POD | 2 | Kubernetes Pod |
| CONTAINER | 2 | 容器 |
| INSTANCE | 2 | 实例 |
| RPC | 3 | RPC 调用 |
| RPC_GROUP | 3 | RPC 调用组 |
| METRIC | 4 | 指标 |
| METRIC_GROUP | 4 | 指标组 |

### 9.2 关系类型说明

| 关系类型 | 描述 | 权重 |
|---------|------|------|
| CALLS | 调用关系 | 5 |
| BELONGS_TO | 归属关系 | 1 |
| HOSTS | 托管关系 | 2 |
| INCLUDES | 包含关系 | 3 |
| EXPOSES | 暴露关系 | 4 |
| AGGREGATES | 聚合关系 | 6 |

### 9.3 健康状态说明

| 状态 | 描述 |
|------|------|
| HEALTHY | 健康 |
| DEGRADED | 降级 |
| UNHEALTHY | 不健康 |
| UNKNOWN | 未知 |

---

## 10. 总结

本文档提供了 svc-topo 服务的完整 API 测试指南，包括：

1. ✅ **Prometheus 指标查询 API** - 查询 Pod、Node、Container 的指标
2. ✅ **XFlow 拓扑可视化 API** - 获取和管理拓扑数据
3. ✅ **拓扑缓存管理 API** - 管理拓扑缓存
4. ✅ **API 查询 API** - 查询系统 API 列表
5. ✅ **健康检查 API** - 服务健康状态检查
6. ✅ **测试脚本** - 自动化测试脚本
7. ✅ **Postman Collection** - Postman 测试集合
8. ✅ **测试检查清单** - 完整的测试检查项
9. ✅ **问题排查指南** - 常见问题解决方案

使用本文档，您可以系统地测试 svc-topo 服务的所有功能，确保 Prometheus 和 Kubernetes 集成正常工作。

---

**文档版本**: 1.0
**最后更新**: 2024-11-08
**维护者**: ChaosBlade Team
