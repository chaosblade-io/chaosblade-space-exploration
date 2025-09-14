## 故障空间检测模块 API 接口需求说明（Markdown 版）

### 1. 概述
- 目标：支撑“新增检测”流程（目标系统选择、API 参数配置、调用链与故障配置、SLO、执行）的后端接口。
- 依据：前端页面 src/pages/Chaos/FaultSpaceDetection/AddDetection/index.tsx 的数据结构与交互流程。
- Base URL：/api
- 认证：本版本不需要认证。
- 返回包装：统一为 { success, data, error }
    - success: boolean
    - data: 任意数据
    - error: { code, message, details? }

### 2. 错误码
- VALIDATION_ERROR：参数校验失败（details 指出字段错误）
- NOT_FOUND：资源不存在
- CONFLICT：资源冲突/非法状态（如重复执行）
- DEPENDENCY_MISSING：依赖缺失（如未同步拓扑）
- INTERNAL_ERROR：服务内部错误
- THROTTLED：限流

### 3. 数据模型（面向前端交互）
- System（系统）
    - systemId: int64
    - name: string
    - environments: string[]
    - apiSource: { syncTime: datetime }
    - latestTopologyId: int64
- TopologySummary（拓扑摘要）
    - topologyId: int64
    - discoveredAt: datetime
    - totalServices: number
    - totalEdges: number
    - totalCalls: number
- TopologyNode（拓扑节点）
    - id: int64
    - topologyId: int64
    - nodeKey: string
    - name: string
    - layer: number
    - protocol: enum("HTTP","gRPC","DB","MQ","OTHER")
- FaultType（故障类型）
    - faultTypeId: int64
    - faultCode: string（唯一稳定编码）
    - name: string
    - description: string
    - category: string（CPU、Memory、Disk、Network、Process、Internal）
    - enabled: boolean
    - displayOrder: number
    - paramConfig: object（JSON，参数定义，用于前端动态表单）
- FaultConfig（故障配置）
    - configId: int64
    - taskId: int64
    - nodeId: int64
    - faultTypeId: int64
    - paramValues: object（JSON，用户具体参数值）
    - status: enum("PENDING","RUNNING","COMPLETED","FAILED","CANCELLED","DRAFT")
    - createdAt/updatedAt: datetime
- ApiSummary（API 概览，用于选择）
    - method: string（GET/POST/...）
    - path: string（如 /orders/{id}）
    - operationId: string
    - summary: string
- ApiOperationDetail（API 详情/参数 Schema）
    - method, path, operationId
    - pathParams: { name, type, required }[]
    - queryParams: { name, type, required, multiple? }[]
    - headers: {
      authTypes: ("TOKEN"|"COOKIE"|"PROFILE")[],
      customHeaderSchema?: { name, type, required }[]
      }
    - requestBodySchema: object（任意 JSON Schema 结构）
- Task（检测任务，承载整页配置）
    - taskId: int64
    - status: enum("DRAFT","READY","RUNNING","COMPLETED","FAILED","CANCELLED")
    - targetSystem: { systemId: string, environment: string, apiSource: { syncTime: string }, selectedAPI?: { method, path, operationId } }
    - apiParameters: { pathParams: object, queryParams: object, headers: { authType: "TOKEN"|"COOKIE"|"PROFILE", customHeaders: object }, requestBody: string }
    - traceConfig: { baselineTrace: any, faultConfigurations: any[] }
    - sloConfig: { functionalAssertions: any, performanceTargets: any }
    - executionConfig: { concurrency: number }
    - createdAt/updatedAt: datetime

---

### 4. 系统与拓扑

#### 4.1 GET /api/systems
- 描述：获取被测系统列表，用于“目标系统选择”。
- 查询参数：
    - page: integer，默认 1
    - size: integer，默认 20
- 响应 data：
    - items: System[]
    - page: { page, size, total }
- 使用场景：进入新增页时加载系统列表。

#### 4.2 GET /api/systems/{systemId}
- 描述：获取系统详情（含 apiSource.syncTime 等）。
- 路径参数：
    - systemId: int64（必填）
- 响应 data：System
- 使用场景：选择系统后加载详情。

#### 4.3 GET /api/systems/{systemId}/topologies/latest
- 描述：获取系统最新拓扑摘要。
- 路径参数：
    - systemId: int64（必填）
- 响应 data：TopologySummary
- 使用场景：为拓扑可视化与目标节点选择提供上下文。

#### 4.4 GET /api/topologies/{topologyId}/nodes
- 描述：获取拓扑节点列表，用于选择故障注入目标。
- 路径参数：
    - topologyId: int64（必填）
- 查询参数（可选过滤）：
    - serviceName: string
    - layer: integer
    - protocol: string（HTTP、gRPC、DB、MQ、OTHER）
- 响应 data：TopologyNode[]
- 使用场景：XFlow 拓扑内选择节点时的数据来源。

---

### 5. API 目录与参数 Schema（用于 APIParameterSection）

#### 5.1 GET /api/systems/{systemId}/apis
- 描述：获取系统的 API 列表以供选择。
- 路径参数：systemId: int64（必填）
- 查询参数（可选）：
    - method: string
    - keyword: string（在 path/summary 中模糊匹配）
- 响应 data：ApiSummary[]
- 使用场景：TargetSystemSection 中的“API 选择”。

#### 5.2 GET /api/systems/{systemId}/apis/{operationId}
- 描述：获取指定 API 的参数 Schema，用于渲染参数表单与校验。
- 路径参数：systemId: int64、operationId: string（均必填）
- 响应 data：ApiOperationDetail
- 使用场景：APIParameterSection 动态生成 path/query/headers/body 配置项。

---

### 6. 故障类型（字典）

#### 6.1 GET /api/fault-types
- 描述：获取故障类型列表及参数配置，供前端渲染动态表单。
- 查询参数：
    - enabled: boolean（可选）
- 响应 data：FaultType[]
- 说明：paramConfig 定义每种故障的参数（类型、必填、默认、选项、描述等）。

---

### 7. 故障配置（与节点/任务绑定）

#### 7.1 GET /api/fault-configs
- 描述：查询故障配置列表（按任务、节点、状态过滤，分页）。
- 查询参数：
    - taskId: int64（可选）
    - nodeId: int64（可选）
    - status: string（可选）
    - page: integer，默认 1
    - size: integer，默认 20
- 响应 data：
    - items: FaultConfig[]
    - page: { page, size, total }
- 使用场景：任务详情页或新增流程中回显已配置的故障。

#### 7.2 POST /api/fault-configs
- 描述：创建故障配置，绑定 taskId + nodeId + faultTypeId，保存参数值。
- 请求体：
    - taskId: int64（必填）
    - nodeId: int64（必填）
    - faultTypeId: int64（必填）
    - paramValues: object（必填，应满足 GET /fault-types 中 paramConfig 的校验）
    - executeNow: boolean（可选，默认 false）
    - labels: string[]（可选）
- 响应 data：新建的 FaultConfig
- 校验：
    - taskId/nodeId/faultTypeId 有效性
    - paramValues 按 paramConfig 类型/必填/范围校验

#### 7.3 GET /api/fault-configs/{configId}
- 描述：获取单个故障配置详情。
- 路径参数：configId: int64
- 响应 data：FaultConfig

#### 7.4 PATCH /api/fault-configs/{configId}
- 描述：更新故障配置（参数/启用状态/手工状态调整）。
- 路径参数：configId: int64
- 请求体（部分字段可选）：
    - paramValues?: object
    - enabled?: boolean
    - status?: enum("PENDING","RUNNING","COMPLETED","FAILED","CANCELLED","DRAFT")
- 响应 data：更新后的 FaultConfig
- 约束：非法状态流转返回 CONFLICT（建议状态机：DRAFT→PENDING→RUNNING→COMPLETED/FAILED/CANCELLED）。

#### 7.5 DELETE /api/fault-configs/{configId}
- 描述：删除故障配置。
- 路径参数：configId: int64
- 响应：204 No Content

---

### 8. 检测任务（承载整页配置）

#### 8.1 POST /api/detection-tasks
- 描述：创建或保存“新增检测”的整页配置，用于“保存草稿/校验保存”。
- 请求体（对齐前端 TaskConfigurationData）：
    - targetSystem: { systemId: string, environment: string, apiSource?: { syncTime?: string }, selectedAPI?: { method, path, operationId } }
    - apiParameters: { pathParams: object, queryParams: object, headers: { authType: "TOKEN"|"COOKIE"|"PROFILE", customHeaders: object }, requestBody: string }
    - traceConfig: { baselineTrace: any, faultConfigurations: any[] }
    - sloConfig: { functionalAssertions: any, performanceTargets: any }
    - executionConfig: { concurrency: number }
    - mode: "DRAFT" | "READY" （DRAFT=保存草稿；READY=校验并保存）
- 响应 data：{ taskId, status, createdAt, updatedAt }
- 使用场景：
    - 点击“Save Draft”：mode=DRAFT
    - 点击“Validate & Save”：mode=READY（服务端需进行严格校验）

#### 8.2 GET /api/detection-tasks/{taskId}
- 描述：获取任务详情（回显整页配置）。
- 路径参数：taskId: int64
- 响应 data：Task

#### 8.3 PATCH /api/detection-tasks/{taskId}
- 描述：部分更新任务配置（同 8.1 结构的子集）。
- 路径参数：taskId: int64
- 请求体：Task 的任意子集字段
- 响应 data：Task

#### 8.4 POST /api/detection-tasks/{taskId}/execute
- 描述：立即执行整套检测任务（对应“Execute Immediately”）。
- 路径参数：taskId: int64
- 响应：202 Accepted
- 说明：服务端可并行或串行触发该任务下的故障配置执行。

---

### 9. 故障执行与状态

#### 9.1 GET /api/fault-configs/{configId}/status
- 描述：查询单个故障配置的执行状态。
- 路径参数：configId: int64
- 响应 data：{ configId, status, executedAt?, completedAt?, lastError? }

#### 9.2 POST /api/fault-configs/{configId}/execute
- 描述：立即执行单个故障配置（异步）。
- 路径参数：configId: int64
- 响应：202 Accepted（启动成功即返回）
- 异常：若已 RUNNING 返回 CONFLICT。

#### 9.3 POST /api/tasks/{taskId}/fault-configs/execute
- 描述：批量执行某任务下的所有故障配置。
- 路径参数：taskId: int64
- 响应：202 Accepted

---

### 10. 示例

#### 10.1 获取 API 列表
```
GET /api/systems/2/apis?method=GET&keyword=order
```

#### 10.2 获取 API 参数 Schema
```
GET /api/systems/2/apis/getOrderById
```

#### 10.3 保存草稿
```
POST /api/detection-tasks
{
  "targetSystem": { "systemId": "2", "environment": "staging", "selectedAPI": {"method":"GET","path":"/orders/{id}","operationId":"getOrderById"} },
  "apiParameters": { "pathParams": {"id":"123"}, "queryParams": {}, "headers": {"authType":"TOKEN","customHeaders":{"Authorization":"Bearer xxx"}}, "requestBody": "" },
  "traceConfig": { "baselineTrace": null, "faultConfigurations": [] },
  "sloConfig": { "functionalAssertions": { "statusCodes": [200], "jsonPathAssertions": [] }, "performanceTargets": {"p95Limit":800,"p99Limit":1500,"errorRateLimit":5} },
  "executionConfig": { "concurrency": 5 },
  "mode": "DRAFT"
}
```

#### 10.4 创建故障配置
```
POST /api/fault-configs
{
  "taskId": 123,
  "nodeId": 456,
  "faultTypeId": 1,
  "paramValues": {"duration":120,"cpu_percent":90,"cpu_cores":2},
  "executeNow": false
}
```

#### 10.5 立即执行任务
```
POST /api/detection-tasks/123/execute
```

---

### 11. 与前端 AddDetection 模块的映射
- TargetSystemSection：
    - GET /api/systems
    - GET /api/systems/{systemId}
    - GET /api/systems/{systemId}/topologies/latest
    - GET /api/systems/{systemId}/apis
    - GET /api/systems/{systemId}/apis/{operationId}
- APIParameterSection：
    - 使用 5.2 的参数 Schema 渲染表单，并提交到 8.x/7.x 相关接口。
- XFlowTraceVisualization：
    - GET /api/topologies/{topologyId}/nodes
    - GET /api/fault-types
    - POST /api/fault-configs（或由 8.x 创建任务后批量创建故障配置）
- SLOConfigurationSection：
    - 作为 Task 的 sloConfig 字段保存在 8.x 接口中。
- ExecutionConfigurationSection：
    - 作为 Task 的 executionConfig 字段保存在 8.x 接口中；
    - 执行：POST /api/detection-tasks/{taskId}/execute；或单个：POST /api/fault-configs/{configId}/execute。

---

### 12. 约束与校验
- 参数校验：创建/更新故障配置与任务时，严格按 ApiOperationDetail 与 FaultType.paramConfig 校验。
- 状态机建议：Task：DRAFT → READY → RUNNING → COMPLETED/FAILED/CANCELLED；FaultConfig：DRAFT → PENDING → RUNNING → COMPLETED/FAILED/CANCELLED。
- 分页：列表统一支持 page/size，总数为 total。

