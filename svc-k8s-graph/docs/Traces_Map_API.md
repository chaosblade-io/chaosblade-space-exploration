
## 📡 **svc-k8s-graph 围绕 Traces 和 Map 数据暴露的 API 接口**

### 🗂️ **接口分类总览**

| 类别 | 接口数量 | 用途 |
|------|---------|------|
| **Map（服务拓扑图）** | 3 | 服务调用关系、拓扑可视化 |
| **Traces（链路追踪）** | 2 | 分布式追踪查询、详情分析 |
| **混合数据（Map + Traces）** | 2 | 拓扑 + 追踪综合分析 |
| **风险衍伸** | 10+ | 基于 Map/Traces 的风险分析结果 |

---

## 🗺️ **一、Map（服务拓扑图）相关接口**

### **1.1 纯服务拓扑图**

#### **📍 GET `/api/k8s-graph/service-map`**
```
功能：获取服务调用拓扑图（基于 Coroot map 数据）
参数：
  - from: 开始时间（毫秒时间戳，必填）
  - to: 结束时间（毫秒时间戳，必填）
  
示例：
curl "http://localhost:8106/api/k8s-graph/service-map?from=1712649600000&to=1712653200000"

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "namespace": "default",       // 可选
    "fromTime": 1712649600000,    // 开始时间（毫秒）
    "toTime": 1712653200000,      // 结束时间（毫秒）
    "nodes": [                    // 服务节点列表
      {
        "serviceName": "ts-preserve-service",
        "namespace": "default",
        "status": "ok",
        "requestCount": 10800,    // 请求数
        "errorCount": 5,          // 错误数
        "avgLatency": 5.2         // 平均延迟（毫秒）
      }
    ],
    "edges": [                    // 服务调用边列表
      {
        "sourceService": "ts-order-service",
        "targetService": "ts-preserve-service",
        "callCount": 3600,
        "avgLatency": 5.2,
        "errorCount": 2
      }
    ]
  }
}
```

---

#### **📍 GET `/api/k8s-graph/with-service-map`**
```
功能：获取包含服务调用关系的完整 K8s 资源拓扑图（K8s 资源 + 服务调用叠加）
参数：
  - from: 开始时间（毫秒，必填）
  - to: 结束时间（毫秒，必填）
  - namespaces: 可选，命名空间过滤（逗号分隔）

示例：
curl "http://localhost:8106/api/k8s-graph/with-service-map?from=1712649600000&to=1712653200000&namespaces=default,production"

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "nodes": [
      { "id": "pod-1", "type": "pod", "name": "ts-preserve-service-abc123", "namespace": "default" },
      { "id": "svc-1", "type": "service", "name": "ts-preserve-service" }
      // ... K8s 资源节点 + 服务节点
    ],
    "edges": [
      { "source": "pod-1", "target": "svc-1", "relationType": "EXPOSED_BY" },
      { "source": "svc-order", "target": "svc-preserve", "relationType": "CALLS" }
      // ... K8s 关系 + 服务调用关系
    ]
  }
}
```

---

## 🔍 **二、Traces（链路追踪）相关接口**

### **2.1 Trace 列表查询**

#### **📍 GET `/api/k8s-graph/traces`**
```
功能：获取指定服务的 Trace 列表
参数：
  - serviceName: 服务名称（必填）
  - from: 开始时间（毫秒，必填）
  - to: 结束时间（毫秒，必填）

示例：
curl "http://localhost:8106/api/k8s-graph/traces?serviceName=ts-preserve-service&from=1712649600000&to=1712653200000"

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "traces": [
      {
        "traceId": "abc123def456...",
        "serviceName": "ts-preserve-service",
        "operationName": "GET /api/v1/orders",
        "duration": 15000000,     // 纳秒
        "startTime": 1712649600000,
        "endTime": 1712649615000,
        "statusCode": 200,
        "hasError": false,
        "httpMethod": "GET",
        "httpUrl": "/api/v1/orders",
        "spanCount": 5
      }
    ],
    "total": 10,
    "hasMore": false
  }
}
```

---

### **2.2 Trace 详情查询**

#### **📍 GET `/api/k8s-graph/traces/{traceId}`**
```
功能：获取单个 Trace 的完整详情（返回原始数据）
参数：
  - traceId: Trace ID（路径参数，必填）

示例：
curl "http://localhost:8106/api/k8s-graph/traces/abc123def456"

返回数据结构（原始 Coroot API 格式）：
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "traceID": "abc123def456...",
      "spans": [
        {
          "spanID": "span-001",
          "operationName": "GET /api/v1/orders",
          "serviceName": "ts-preserve-service",
          "startTime": 1712649600000,
          "duration": 15000000,
          "tags": [
            {"key": "http.method", "value": "GET"},
            {"key": "http.url", "value": "/api/v1/orders"}
          ],
          "references": [
            {"refType": "CHILD_OF", "traceID": "abc123...", "spanID": "span-000"}
          ],
          "logs": [...]
        }
        // ... 多个 span 组成完整 Trace
      ],
      "processes": {
        "p1": {"serviceName": "ts-preserve-service", "tags": [...]}
      }
    }
  ]
}
```

---

## 🔄 **三、Map + Traces 混合数据接口**

### **3.1 服务性能指标（基于 Traces 聚合）**

#### **📍 GET `/api/k8s-graph/service/{serviceName}/metrics`**
```
功能：获取服务的性能指标（从 Traces 聚合统计）
参数：
  - serviceName: 服务名称（路径参数，必填）
  - from: 开始时间（毫秒，必填）
  - to: 结束时间（毫秒，必填）

示例：
curl "http://localhost:8106/api/k8s-graph/service/ts-preserve-service/metrics?from=1712649600000&to=1712653200000"

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "serviceName": "ts-preserve-service",
    "fromTime": 1712649600000,
    "toTime": 1712653200000,
    "metrics": {
      "requestRate": 120.5,       // 请求率 (req/s)
      "errorRate": 0.02,          // 错误率 (%)
      "latencyP50": 5.2,          // P50 延迟（毫秒）
      "latencyP95": 12.8,         // P95 延迟（毫秒）
      "latencyP99": 25.6,         // P99 延迟（毫秒）
      "apdexScore": 0.92,         // Apdex 分数
      "slowTraces": 15,           // 慢调用数
      "failedTraces": 3           // 失败调用数
    }
  }
}
```

---

### **3.2 拓扑风险分析（基于 Map + Traces 综合分析）**

#### **📍 POST `/api/risk-analysis/service-topology`**
```
功能：分析服务拓扑风险（基于 Map 和 Traces 数据综合分析）
参数：
  - namespace: 命名空间（必填）

请求体：
{
  "namespace": "default"
}

示例：
curl -X POST "http://localhost:8106/api/risk-analysis/service-topology" \
  -H "Content-Type: application/json" \
  -d '{"namespace":"default"}'

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "namespace": "default",
    "riskScore": 75,
    "riskLevel": "HIGH",
    "riskyServices": [
      {
        "serviceName": "ts-preserve-service",
        "riskScore": 85,
        "issues": [
          {
            "type": "HIGH_ERROR_RATE",
            "description": "错误率过高 (5%)",
            "severity": "critical",
            "source": "traces",  // 数据来源：traces
            "evidence": {
              "errorRate": 5.2,
              "failedTraces": 23,
              "totalTraces": 456
            }
          },
          {
            "type": "SLOW_DEPENDENCY",
            "description": "依赖服务响应慢",
            "severity": "warning",
            "source": "map",  // 数据来源：map
            "evidence": {
              "dependency": "ts-inventory-service",
              "avgLatency": 150.5,
              "p99Latency": 500.0
            }
          }
        ]
      }
    ],
    "topologyIssues": [
      {
        "type": "SINGLE_POINT_OF_FAILURE",
        "description": "单点故障风险",
        "affectedServices": ["ts-order-service"]
      }
    ],
    "recommendations": [
      "增加 ts-order-service 的副本数",
      "优化 ts-inventory-service 的数据库查询"
    ]
  }
}
```

---

### **3.3 Trace 风险分析（基于单个 Trace 深度分析）**

#### **📍 POST `/api/risk-analysis/trace`**
```
功能：分析单个 Trace 的风险（深度分析 Span 关系、错误传播）
参数：
  - traceId: Trace ID（必填）

请求体：
{
  "traceId": "abc123def456..."
}

示例：
curl -X POST "http://localhost:8106/api/risk-analysis/trace" \
  -H "Content-Type: application/json" \
  -d '{"traceId":"abc123def456"}'

返回数据结构：
{
  "code": 0,
  "message": "ok",
  "data": {
    "traceId": "abc123def456...",
    "riskScore": 65,
    "riskLevel": "MEDIUM",
    "issues": [
      {
        "type": "ERROR_PROPAGATION",
        "description": "错误在调用链中传播",
        "severity": "critical",
        "spanId": "span-003",
        "service": "ts-payment-service",
        "rootCause": true
      },
      {
        "type": "LONG_LATENCY_SPAN",
        "description": "Span 耗时过长",
        "severity": "warning",
        "spanId": "span-002",
        "service": "ts-database-service",
        "duration": 8500000  // 8.5ms
      }
    ],
    "criticalPath": {
      "totalDuration": 15000000,
      "spans": ["span-000", "span-001", "span-002", "span-003"],
      "bottleneck": "span-002"
    },
    "recommendations": [
      "检查 ts-payment-service 的支付网关超时配置",
      "优化 ts-database-service 的 SQL 查询"
    ]
  }
}
```

---

## 📊 **四、Pipeline 阶段的 Traces/Map 分析接口**

### **4.1 Pipeline 完整执行（包含 Phase 2 Map 分析 + Phase 4 Trace 分析）**

#### **📍 GET `/api/risk-analysis/pipeline/{namespace}`**
```
功能：异步执行完整的六阶段风险分析（包含 Map 和 Traces 分析）
参数：
  - namespace: 命名空间（路径参数，必填）

执行阶段：
  - Phase 1: 规则扫描（K8s 配置）
  - Phase 2: 拓扑风险分析 ← 基于 Map 数据
  - Phase 3: RiskRank 计算
  - Phase 4: Trace 深度分析 ← 基于 Traces 数据
  - Phase 5: 综合分析与故障场景生成
  - Phase 6: 配置生成（ChaosBlade 实验）

示例：
curl "http://localhost:8106/api/risk-analysis/pipeline/default"

返回（立即返回，异步执行）：
{
  "executionId": "exec-abc123",
  "namespace": "default",
  "state": "RUNNING",
  "currentPhase": 1,
  "progressPercent": 10,
  "startTime": "2024-04-09 12:00:00",
  "phaseStatuses": {
    "1": {"status": "RUNNING"},
    "2": {"status": "PENDING"},
    "3": {"status": "PENDING"},
    "4": {"status": "PENDING"},
    "5": {"status": "PENDING"},
    "6": {"status": "PENDING"}
  }
}
```

---

#### **📍 GET `/api/risk-analysis/pipeline/{namespace}/summary`**
```
功能：获取 Pipeline 完整结果（包含 Map 和 Traces 分析结果）

返回数据结构：
{
  "namespace": "default",
  "state": "COMPLETED",
  "result": {
    "phase2Result": {  // 拓扑风险分析结果（Map 数据）
      "riskScore": 75,
      "topologyIssues": [...],
      "riskyServices": [...]
    },
    "phase4Results": {  // Trace 深度分析结果（Traces 数据）
      "slowestApis": [
        {
          "service": "ts-preserve-service",
          "operation": "GET /api/v1/orders",
          "avgLatency": 150.5,
          "p99Latency": 500.0,
          "issueCount": 5,
          "issues": [
            {
              "type": "SLOW_DATABASE_QUERY",
              "occurrences": 12,
              "avgDuration": 80.5
            }
          ]
        }
      ],
      "errorPropagationChains": [...],
      "criticalPaths": [...]
    }
  }
}
```

---

### **4.2 规则扫描结果（包含 Map/Traces 统计）**

#### **📍 GET `/api/risk-analysis/rule-scan/service/{namespace}/{serviceName}`**
```
功能：获取单个服务的风险画像（包含 Map 和 Traces 统计数据）
参数：
  - namespace: 命名空间（路径参数）
  - serviceName: 服务名称（路径参数）

返回数据结构：
{
  "service": "ts-preserve-service",
  "namespace": "default",
  "overallRiskScore": 85,
  "riskCategories": {
    "topology": {     // 拓扑风险（Map 数据）
      "score": 70,
      "issues": [...]
    },
    "performance": {  // 性能风险（Traces 数据）
      "score": 80,
      "slowTraces": 15,
      "avgLatency": 120.5,
      "p99Latency": 500.0
    },
    "reliability": {  // 可靠性风险（Traces 数据）
      "score": 90,
      "errorRate": 5.2,
      "failedTraces": 23
    }
  },
  "triggeredRules": [...]
}
```

---

## 🗂️ **五、API 完整清单（按用途分类）**

### **纯 Map 数据接口**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/k8s-graph/service-map` | 获取服务调用拓扑图 |
| GET | `/api/k8s-graph/with-service-map` | K8s 资源 + 服务调用叠加图 |

### **纯 Traces 数据接口**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/k8s-graph/traces` | Trace 列表查询 |
| GET | `/api/k8s-graph/traces/{traceId}` | Trace 详情查询 |

### **Map + Traces 综合接口**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/k8s-graph/service/{serviceName}/metrics` | 服务性能指标（Traces 聚合） |
| POST | `/api/risk-analysis/service-topology` | 服务拓扑风险分析 |
| POST | `/api/risk-analysis/trace` | Trace 风险分析 |
| GET | `/api/risk-analysis/rule-scan/service/{namespace}/{serviceName}` | 服务风险画像 |

### **Pipeline（异步，包含 Map/Traces 分析）**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/risk-analysis/pipeline/{namespace}` | 启动 Pipeline |
| GET | `/api/risk-analysis/pipeline/{namespace}/status` | 查看执行状态 |
| GET | `/api/risk-analysis/pipeline/{namespace}/summary` | 获取完整结果 |
| GET | `/api/risk-analysis/pipeline/{namespace}/sync` | 同步执行 Pipeline |

---

## 📋 **总结**

**svc-k8s-graph** 围绕 **Coroot** 的 **traces** 和 **map** 数据，形成了三层 API 体系：

```
┌─────────────────────────────────────────┐
│ Level 3: 风险分析层                     │
│ - 拓扑风险分析 (Map + Traces)           │
│ - Trace 风险分析 (Traces 深度分析)       │
│ - Pipeline 六阶段综合                   │
│ - 服务风险画像                          │
└─────────────────────────────────────────┘
                    ▲
                    │
┌─────────────────────────────────────────┐
│ Level 2: 聚合指标层                     │
│ - 服务性能指标 (Traces 聚合)            │
│ - 服务拓扑叠加 (K8s + Map)              │
└─────────────────────────────────────────┘
                    ▲
                    │
┌─────────────────────────────────────────┐
│ Level 1: 原始数据层                     │
│ - 服务调用拓扑图 (Map)                  │
│ - Trace 列表查询 (Traces)               │
│ - Trace 详情查询 (Traces)               │
└─────────────────────────────────────────┘
```

- **Level 1**: 直接从 Coroot API 获取原始数据并封装
- **Level 2**: 基于原始数据进行聚合、转换、叠加
- **Level 3**: 基于 Map + Traces 进行 AI 风险分析和诊断

整个系统通过这 **7+ 核心接口** 和 **10+ 风险衍伸接口**，为用户提供从"数据查询"到"风险诊断"的完整能力。
