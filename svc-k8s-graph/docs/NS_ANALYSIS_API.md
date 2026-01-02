# 命名空间分析 API 文档

## 概述

命名空间分析API提供对Kubernetes命名空间的全面分析能力，包括拓扑发现、风险评估、LLM智能分析等功能。

**Base URL**: `/api/ns-analysis`

---

## 任务管理

### 1. 创建分析任务

**POST** `/tasks`

创建一个新的命名空间分析任务。

**请求体**:
```json
{
  "namespace": "default",
  "systemId": 1,
  "topN": 5,
  "config": {
    "enableLlmAnalysis": true,
    "includeMetrics": true
  },
  "createdBy": "admin"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| namespace | string | ✅ | 目标命名空间 |
| systemId | long | ❌ | 关联系统ID |
| topN | int | ❌ | Top N风险数量，默认5，范围1-20 |
| config | object | ❌ | 任务配置 |
| createdBy | string | ❌ | 创建者 |

**响应**:
```json
{
  "success": true,
  "message": "分析任务创建成功",
  "data": {
    "id": 1,
    "taskId": "ns-a1b2c3d4",
    "namespace": "default",
    "status": "PENDING",
    "progressPercent": 0,
    "createdAt": "2026-01-02 10:00:00"
  }
}
```

---

### 2. 获取任务状态

**GET** `/tasks/{taskId}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务ID |

**响应**:
```json
{
  "success": true,
  "data": {
    "taskId": "ns-a1b2c3d4",
    "namespace": "default",
    "status": "RUNNING",
    "triggerType": "API",
    "topN": 5,
    "startedAt": "2026-01-02 10:00:05",
    "currentPhase": 2,
    "progressPercent": 45
  },
  "source": "cache"
}
```

**任务状态枚举**:
| 状态 | 说明 |
|------|------|
| PENDING | 等待执行 |
| RUNNING | 执行中 |
| COMPLETED | 已完成 |
| FAILED | 失败 |
| CANCELLED | 已取消 |

---

### 3. 分页查询任务列表

**GET** `/tasks`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| namespace | string | ❌ | 按命名空间过滤 |
| status | string | ❌ | 按状态过滤 |
| page | int | ❌ | 页码，默认1 |
| size | int | ❌ | 每页数量，默认20，最大100 |

**响应**:
```json
{
  "success": true,
  "data": {
    "content": [...],
    "total": 50,
    "page": 1,
    "size": 20,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### 4. 获取任务进度

**GET** `/tasks/{taskId}/progress`

实时获取任务执行进度。

**响应**:
```json
{
  "success": true,
  "data": {
    "phase": 3,
    "percent": 75,
    "message": "正在进行风险分析...",
    "status": "RUNNING"
  }
}
```

---

### 5. 取消任务

**POST** `/tasks/{taskId}/cancel`

取消正在执行或等待中的任务。

**响应**:
```json
{
  "success": true,
  "message": "任务已取消"
}
```

---

### 6. 删除任务

**DELETE** `/tasks/{taskId}`

删除任务及其关联的所有数据（结果、风险分析）。

**响应**:
```json
{
  "success": true,
  "message": "任务删除成功"
}
```

---

## 分析结果

### 7. 获取分析结果

**GET** `/results/{taskId}`

**响应**:
```json
{
  "success": true,
  "data": {
    "taskId": "ns-a1b2c3d4",
    "namespace": "default",
    "totalServices": 12,
    "totalPods": 35,
    "totalContainers": 48,
    "totalNodes": 50,
    "totalEdges": 67,
    "topology": { ... },
    "services": [ ... ],
    "riskSummary": { ... },
    "analysisTimeMs": 15230
  },
  "source": "database"
}
```

---

### 8. 获取命名空间最新结果

**GET** `/namespaces/{namespace}/latest`

获取指定命名空间最近一次成功分析的结果。

**响应**: 同上

---

## 风险分析

### 9. 获取任务的风险分析列表

**GET** `/tasks/{taskId}/risks`

获取任务的所有风险分析结果，按排名顺序返回。

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "taskId": "ns-a1b2c3d4",
      "serviceName": "payment-service",
      "riskLevel": "CRITICAL",
      "riskScore": 95.50,
      "riskCategory": "SinglePointOfFailure",
      "riskTitle": "单点故障风险",
      "riskDescription": "该服务仅有1个Pod副本，存在单点故障风险",
      "recommendations": [
        "增加Pod副本数至少为2",
        "配置PodDisruptionBudget"
      ],
      "affectedPods": ["payment-service-7d8f9-abc12"],
      "rankOrder": 1
    }
  ],
  "total": 5
}
```

---

### 10. 获取Top N风险

**GET** `/tasks/{taskId}/top-risks`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| topN | int | ❌ | 返回数量，默认5，范围1-20 |

**响应**: 同上，仅返回前N条

---

## 风险级别定义

| 级别 | 分数范围 | 说明 |
|------|---------|------|
| CRITICAL | 90-100 | 严重风险，需立即处理 |
| HIGH | 70-89 | 高风险，建议尽快处理 |
| MEDIUM | 50-69 | 中等风险，计划处理 |
| LOW | 30-49 | 低风险，可观察 |
| INFO | 0-29 | 提示信息 |

---

## 错误响应

```json
{
  "success": false,
  "message": "任务不存在: ns-invalid"
}
```

**HTTP状态码**:
| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

