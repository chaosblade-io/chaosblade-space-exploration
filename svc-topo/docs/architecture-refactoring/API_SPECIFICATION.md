# svc-topo API 规范文档

## 版本信息
- **API 版本**: v1.0
- **文档日期**: 2025-11-08

---

## 目录
1. [Prometheus 指标 API](#1-prometheus-指标-api)
2. [拓扑查询 API（扩展）](#2-拓扑查询-api扩展)
3. [数据模型](#3-数据模型)
4. [错误处理](#4-错误处理)

---

## 1. Prometheus 指标 API

### 1.1 查询 Pod 指标

**请求**：
```
GET /api/metrics/pod/{namespace}/{podName}
```

**路径参数**：
- `namespace` (string, required) - Kubernetes 命名空间
- `podName` (string, required) - Pod 名称

**响应示例**：
```json
{
  "success": true,
  "namespace": "default",
  "podName": "my-app-pod-12345",
  "metrics": {
    "resources": {
      "cpuUsagePercent": 45.2,
      "cpuUsageCores": 0.452,
      "cpuThrottlingPercent": 2.1,
      "memoryUsageBytes": 536870912,
      "memoryUsagePercent": 52.4,
      "memoryWorkingSetBytes": 524288000,
      "memoryRssBytes": 500000000,
      "memoryCacheBytes": 36870912,
      "networkReceiveBytesPerSec": 1024000,
      "networkTransmitBytesPerSec": 2048000,
      "networkReceivePacketsPerSec": 1500,
      "networkTransmitPacketsPerSec": 2000,
      "networkErrorsPerSec": 0.5,
      "diskUsageBytes": 1073741824,
      "diskUsagePercent": 10.5,
      "diskReadBytesPerSec": 512000,
      "diskWriteBytesPerSec": 1024000,
      "diskIoTimePercent": 5.2,
      "quota": {
        "cpu": "1000m",
        "memory": "1Gi",
        "storage": "10Gi"
      }
    },
    "performance": {
      "requestsPerSecond": 150.5,
      "requestsTotal": 1500000,
      "requestDurationP50": 0.05,
      "requestDurationP95": 0.15,
      "requestDurationP99": 0.25,
      "errorRate": 0.5,
      "errorsTotal": 7500,
      "errors4xxTotal": 5000,
      "errors5xxTotal": 2500,
      "concurrentConnections": 50,
      "activeRequests": 10,
      "queueDepth": 5,
      "queueLatency": 0.01
    },
    "health": {
      "status": "healthy",
      "checks": {
        "liveness": true,
        "readiness": true
      },
      "restartCount": 2,
      "uptimeSeconds": 86400,
      "lastRestartTime": 1699401600000
    },
    "custom": {
      "jvm_memory_used_bytes": 450000000,
      "jvm_gc_pause_seconds_sum": 2.5
    },
    "timestamp": 1699488000000
  },
  "timestamp": 1699488000000
}
```

**错误响应**：
```json
{
  "success": false,
  "error": "Failed to query Pod metrics",
  "message": "Pod not found: default/my-app-pod-12345"
}
```

---

### 1.2 查询 Node 指标

**请求**：
```
GET /api/metrics/node/{nodeName}
```

**路径参数**：
- `nodeName` (string, required) - Node 名称

**响应示例**：
```json
{
  "success": true,
  "nodeName": "node-1",
  "metrics": {
    "resources": {
      "cpuUsagePercent": 65.3,
      "cpuUsageCores": 2.612,
      "memoryUsageBytes": 8589934592,
      "memoryUsagePercent": 53.7,
      "networkReceiveBytesPerSec": 10240000,
      "networkTransmitBytesPerSec": 20480000,
      "diskUsageBytes": 107374182400,
      "diskUsagePercent": 67.2,
      "diskReadBytesPerSec": 5120000,
      "diskWriteBytesPerSec": 10240000,
      "diskIoTimePercent": 15.5,
      "quota": {
        "cpu": "4",
        "memory": "16Gi",
        "storage": "160Gi"
      }
    },
    "health": {
      "status": "healthy",
      "checks": {
        "ready": true,
        "diskPressure": false,
        "memoryPressure": false,
        "pidPressure": false
      },
      "uptimeSeconds": 2592000
    },
    "timestamp": 1699488000000
  },
  "timestamp": 1699488000000
}
```

---

### 1.3 查询 Container 指标

**请求**：
```
GET /api/metrics/container/{namespace}/{podName}/{containerName}
```

**路径参数**：
- `namespace` (string, required) - Kubernetes 命名空间
- `podName` (string, required) - Pod 名称
- `containerName` (string, required) - Container 名称

**响应示例**：
```json
{
  "success": true,
  "namespace": "default",
  "podName": "my-app-pod-12345",
  "containerName": "app-container",
  "metrics": {
    "resources": {
      "cpuUsageCores": 0.25,
      "memoryUsageBytes": 268435456,
      "networkReceiveBytesPerSec": 512000,
      "networkTransmitBytesPerSec": 1024000,
      "quota": {
        "cpu": "500m",
        "memory": "512Mi"
      }
    },
    "timestamp": 1699488000000
  },
  "timestamp": 1699488000000
}
```

---

### 1.4 批量查询命名空间 Pod 指标

**请求**：
```
GET /api/metrics/namespace/{namespace}/pods
```

**路径参数**：
- `namespace` (string, required) - Kubernetes 命名空间

**响应示例**：
```json
{
  "success": true,
  "namespace": "default",
  "podCount": 3,
  "metrics": {
    "my-app-pod-12345": {
      "resources": { ... },
      "performance": { ... },
      "health": { ... },
      "timestamp": 1699488000000
    },
    "my-app-pod-67890": {
      "resources": { ... },
      "performance": { ... },
      "health": { ... },
      "timestamp": 1699488000000
    },
    "my-db-pod-11111": {
      "resources": { ... },
      "performance": { ... },
      "health": { ... },
      "timestamp": 1699488000000
    }
  },
  "timestamp": 1699488000000
}
```

---

## 2. 拓扑查询 API（扩展）

### 2.1 查询增强拓扑（包含 Prometheus 指标）

**请求**：
```
GET /api/topology/enhanced?namespace={namespace}&service={serviceName}
```

**查询参数**：
- `namespace` (string, required) - 命名空间
- `service` (string, required) - 服务名称
- `includeMetrics` (boolean, optional, default=true) - 是否包含 Prometheus 指标
- `includeK8sMetadata` (boolean, optional, default=true) - 是否包含 Kubernetes 元数据

**响应示例**：
```json
{
  "success": true,
  "topology": {
    "nodes": [
      {
        "nodeId": "pod-my-app-pod-12345",
        "entity": {
          "entityId": "pod-default-my-app-pod-12345",
          "type": "POD",
          "name": "my-app-pod-12345",
          "displayName": "my-app-pod-12345",
          "namespace": "default"
        },
        "attrs": {
          "RED": {
            "count": 1000,
            "error": 5,
            "rt": 0.15,
            "status": "healthy"
          },
          "prometheus": {
            "resources": {
              "cpuUsagePercent": 45.2,
              "memoryUsagePercent": 52.4
            },
            "health": {
              "status": "healthy",
              "restartCount": 2
            }
          },
          "kubernetes": {
            "kind": "Pod",
            "namespace": "default",
            "name": "my-app-pod-12345",
            "labels": {
              "app": "my-app",
              "version": "v1.0"
            }
          },
          "extensions": {
            "healthScore": 85.5
          }
        }
      },
      {
        "nodeId": "node-node-1",
        "entity": {
          "entityId": "node-node-1",
          "type": "NODE",
          "name": "node-1",
          "displayName": "node-1"
        },
        "attrs": {
          "prometheus": {
            "resources": {
              "cpuUsagePercent": 65.3,
              "memoryUsagePercent": 53.7
            },
            "health": {
              "status": "healthy"
            }
          },
          "kubernetes": {
            "kind": "Node",
            "name": "node-1"
          }
        }
      }
    ],
    "edges": [
      {
        "edgeId": "hosts-node-1-my-app-pod-12345",
        "from": "node-node-1",
        "to": "pod-my-app-pod-12345",
        "type": "HOSTS",
        "firstSeen": 1699401600000,
        "lastSeen": 1699488000000
      }
    ]
  },
  "timestamp": 1699488000000
}
```

---

## 3. 数据模型

### 3.1 PrometheusMetrics

```typescript
interface PrometheusMetrics {
  resources?: ResourceMetrics;
  performance?: PerformanceMetrics;
  health?: HealthMetrics;
  custom?: Record<string, any>;
  timestamp: number;
}
```

### 3.2 ResourceMetrics

```typescript
interface ResourceMetrics {
  // CPU
  cpuUsagePercent?: number;
  cpuUsageCores?: number;
  cpuThrottlingPercent?: number;
  
  // Memory
  memoryUsageBytes?: number;
  memoryUsagePercent?: number;
  memoryWorkingSetBytes?: number;
  memoryRssBytes?: number;
  memoryCacheBytes?: number;
  
  // Network
  networkReceiveBytesPerSec?: number;
  networkTransmitBytesPerSec?: number;
  networkReceivePacketsPerSec?: number;
  networkTransmitPacketsPerSec?: number;
  networkErrorsPerSec?: number;
  
  // Disk
  diskUsageBytes?: number;
  diskUsagePercent?: number;
  diskReadBytesPerSec?: number;
  diskWriteBytesPerSec?: number;
  diskIoTimePercent?: number;
  
  // Quota
  quota?: ResourceQuota;
}

interface ResourceQuota {
  cpu?: string;      // e.g., "1000m" or "2"
  memory?: string;   // e.g., "1Gi" or "512Mi"
  storage?: string;  // e.g., "10Gi"
}
```

### 3.3 HealthMetrics

```typescript
interface HealthMetrics {
  status: "healthy" | "degraded" | "unhealthy" | "unknown";
  checks?: Record<string, boolean>;
  restartCount?: number;
  uptimeSeconds?: number;
  lastRestartTime?: number;
}
```

---

## 4. 错误处理

### 4.1 错误响应格式

```json
{
  "success": false,
  "error": "错误类型",
  "message": "详细错误信息"
}
```

### 4.2 HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用（Prometheus/Kubernetes 连接失败）|

### 4.3 常见错误

**Prometheus 连接失败**：
```json
{
  "success": false,
  "error": "Prometheus connection failed",
  "message": "Failed to connect to Prometheus server at localhost:9090"
}
```

**Kubernetes API 权限不足**：
```json
{
  "success": false,
  "error": "Kubernetes API permission denied",
  "message": "Forbidden: pods is forbidden: User cannot list resource 'pods' in API group '' in the namespace 'default'"
}
```

**资源不存在**：
```json
{
  "success": false,
  "error": "Resource not found",
  "message": "Pod not found: default/my-app-pod-12345"
}
```

