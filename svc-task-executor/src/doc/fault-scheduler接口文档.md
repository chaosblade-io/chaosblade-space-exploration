ip：http://1.94.151.57:8103

## 目录
- 概述与统一返回格式
- 服务地址与认证
- 接口分组
- 故障管理 API（/api/faults）
  - 1. 执行故障 POST /api/faults/execute
  - 2. 查看故障状态与事件 GET /api/faults/{bladeName}/status
  - 3. 停止故障 DELETE /api/faults/{bladeName}
  - 4. 获取故障详情 GET /api/faults/{bladeName}
  - 5. 列出所有故障 GET /api/faults
  - 6. 检查故障是否存在 GET /api/faults/{bladeName}/exists
  - 7. 健康检查 GET /api/faults/health
- 通用 API
  - Hello GET /hello
- 错误处理与状态码
- 调用时机与前置条件
- 备注：接口路径风格差异说明


## 概述与统一返回格式

- 模块：svc-fault-scheduler
- 功能：基于 ChaosBlade Operator，通过 Kubernetes API 创建/查询/删除 ChaosBlade CR，实现故障注入、状态查询与停止，配合 Redis 记录元数据并支持 TTL 自动清理。

统一返回体（所有接口）：
- success: boolean
- data: any（成功时返回）
- error: { code: string, message: string, details?: any }（失败时返回）

成功示例：
```json
{
  "success": true,
  "data": { "key": "value" },
  "error": null
}
```

失败示例：
```json
{
  "success": false,
  "data": null,
  "error": { "code": "RESOURCE_NOT_FOUND", "message": "Fault not found: blade-xxx" }
}
```


## 服务地址与认证

- 缺省端口：8103（见 svc-fault-scheduler/src/main/resources/application.yml）
- 缺省不启用鉴权；若部署侧有网关/Ingress，请以网关侧认证为准


## 接口分组
- 故障管理 API：/api/faults（FaultsController）
- 通用 API：/hello（HelloController）


## 故障管理 API（/api/faults）

控制器：FaultsController（@RestController, @RequestMapping("/api/faults")）

### 1) 执行故障

- 方法：POST
- 路径：/api/faults/execute
- 用途：创建 ChaosBlade 故障（支持传完整 CR 或仅传 spec），可指定 TTL，到期自动删除
- 典型业务场景：发起一次容器/网络/系统类故障以验证系统韧性

请求参数
- Query
  - name (string, 可选)：故障名称；不传则后端按前缀生成（prefix 见 app.faults.name-prefix）
  - durationSec (int, 可选, >0)：TTL 秒数，到点自动删除（不传则使用 app.faults.default-ttl-seconds）
- Body（JSON, 必填）
  - Map<String, Object>，两种形态均支持：
    - A. 仅 spec（推荐）：{"spec": {...}}
    - B. 完整 ChaosBlade CR：{"apiVersion":"chaosblade.io/v1alpha1","kind":"ChaosBlade","metadata":{...},"spec":{...}}

请求体示例 A（仅 spec）：
```json
{
  "spec": {
    "experiments": [
      {
        "scope": "container",
        "target": "cpu",
        "action": "load",
        "desc": "Inject CPU load",
        "matchers": [
          { "name": "container-names", "value": ["app-container"] },
          { "name": "namespace", "value": ["train-ticket"] }
        ],
        "flags": { "cpu-percent": "80", "timeout": "60" }
      }
    ]
  }
}
```

请求体示例 B（完整 CR）：
```json
{
  "apiVersion": "chaosblade.io/v1alpha1",
  "kind": "ChaosBlade",
  "metadata": { "name": "full-cr-test" },
  "spec": {
    "experiments": [
      {
        "scope": "pod",
        "target": "network",
        "action": "delay",
        "desc": "Inject network latency",
        "matchers": [
          { "name": "labels", "value": ["app=ts-preserve-service"] },
          { "name": "namespace", "value": ["train-ticket"] }
        ],
        "flags": { "time": "200", "offset": "50", "timeout": "120" }
      }
    ]
  }
}
```

成功响应
- data: { faultId: string, bladeName: string }

示例：
```json
{
  "success": true,
  "data": { "faultId": "1d0f2b3c4d5e6f...", "bladeName": "blade-1d0f2b3c4d5e" },
  "error": null
}
```

错误示例
- 名称重复（400）
```json
{
  "success": false,
  "data": null,
  "error": { "code": "INVALID_ARGUMENT", "message": "Invalid request: Fault with name already exists: blade-xxxxx" }
}
```
- K8s 权限不足（403）
```json
{
  "success": false,
  "data": null,
  "error": { "code": "K8S_PERMISSION_DENIED", "message": "Insufficient permissions for Kubernetes operation" }
}
```

状态码
- 200：成功（或业务失败但 success=false）
- 400：参数/JSON 解析/校验错误
- 401/403：K8s 认证或权限错误
- 404：K8s 资源不存在（不常见于创建）
- 500：K8s 客户端错误、Redis 连接失败、内部错误

调用时机与前置条件
- 已部署 ChaosBlade Operator 且 CRD 已注册
- svc-fault-scheduler 具备访问 K8s 集群与 Redis 的配置权限
- 建议先在测试环境验证 spec 的正确性


### 2) 查看故障状态与事件

- 方法：GET
- 路径：/api/faults/{bladeName}/status
- 用途：查询指定 ChaosBlade 故障的当前 phase、详细 status 以及最近 N 条事件
- 业务场景：监控故障执行进度与结果

路径参数
- bladeName (string, 必填)：故障名称（创建时返回的 bladeName 或自定义 name）

成功响应
- data: {
    bladeName: string,
    phase: string,
    status: object,
    events: array,
    eventsCount: number
  }

示例：
```json
{
  "success": true,
  "data": {
    "bladeName": "blade-1d0f2b3c4d5e",
    "phase": "Running",
    "status": {
      "phase": "Running",
      "expStatuses": [ /* 原样透传 ChaosBlade status */ ]
    },
    "events": [
      { "lastTimestamp": "2025-09-02T15:30:16Z", "message": "Experiment started" }
    ],
    "eventsCount": 1
  },
  "error": null
}
```

错误示例（不存在）
```json
{
  "success": false,
  "data": null,
  "error": { "code": "RESOURCE_NOT_FOUND", "message": "Fault not found: blade-unknown" }
}
```

状态码
- 200：成功
- 404：资源不存在
- 500：查询异常

调用时机与前置条件
- 故障创建后查询；events 数量上限受 app.faults.events-limit 配置控制


### 3) 停止故障

- 方法：DELETE
- 路径：/api/faults/{bladeName}
- 用途：删除 ChaosBlade 资源并清理 Redis 中的元数据
- 业务场景：手工终止故障或清理失效故障

路径参数
- bladeName (string, 必填)

成功响应
- data: string 描述

示例：
```json
{
  "success": true,
  "data": "Fault stopped successfully: blade-1d0f2b3c4d5e",
  "error": null
}
```

错误示例（不存在）
```json
{
  "success": false,
  "data": null,
  "error": { "code": "RESOURCE_NOT_FOUND", "message": "Fault not found: blade-unknown" }
}
```

状态码
- 200：成功
- 404：不存在
- 500：删除失败、内部错误

调用时机与前置条件
- 故障已存在且处于可删除状态；若创建时设置了 TTL，到期会自动删除（无需手工）


### 4) 获取故障详情

- 方法：GET
- 路径：/api/faults/{bladeName}
- 用途：组合返回 Redis 元数据（faultId、创建时间、TTL、存档的 YAML）与 K8s 实时状态与 CR 规格
- 业务场景：用于审计/溯源/详细排查

路径参数
- bladeName (string, 必填)

成功响应
- data: {
    bladeName: string,
    faultId?: string,
    createdAt?: string,
    ttlSec?: string,
    specYaml?: string,
    phase: string,
    status: object,
    spec?: object,         // 从 K8s 资源提取
    metadata?: object      // K8s metadata
  }

示例：
```json
{
  "success": true,
  "data": {
    "bladeName": "blade-1d0f2b3c4d5e",
    "faultId": "1d0f2b3c4d5e6f...",
    "createdAt": "2025-09-02T15:30:16Z",
    "ttlSec": "300",
    "specYaml": "apiVersion: chaosblade.io/v1alpha1\nkind: ChaosBlade\nmetadata:\n  name: blade-1d0f2b3c4d5e\n...",
    "phase": "Running",
    "status": { "phase": "Running" },
    "spec": { "experiments": [ /* 当前CR spec */ ] },
    "metadata": { "name": "blade-1d0f2b3c4d5e", "namespace": "default", ... }
  },
  "error": null
}
```

状态码
- 200：成功
- 404：资源不存在
- 500：内部错误


### 5) 列出所有故障

- 方法：GET
- 路径：/api/faults
- 用途：返回当前记录在 Redis 的全部故障名称集合
- 业务场景：后台巡检、列表展示

成功响应
- data: Set<string>

示例：
```json
{
  "success": true,
  "data": ["blade-1d0f2b3c4d5e", "blade-9a8b7c6d5e4f"],
  "error": null
}
```

状态码
- 200：成功
- 500：读取失败


### 6) 检查故障是否存在

- 方法：GET
- 路径：/api/faults/{bladeName}/exists
- 用途：快速判断 K8s 中是否存在该 ChaosBlade 资源
- 业务场景：前置校验、幂等检查

路径参数
- bladeName (string, 必填)

成功响应
- data: boolean

示例：
```json
{
  "success": true,
  "data": true,
  "error": null
}
```

状态码
- 200：成功
- 500：检查失败（内部错误）


### 7) 健康检查

- 方法：GET
- 路径：/api/faults/health
- 用途：模块健康检查
- 成功响应示例：
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "service": "svc-fault-scheduler",
    "timestamp": "1735889656123"
  },
  "error": null
}
```


## 通用 API

### Hello

- 方法：GET
- 路径：/hello
- 用途：简单连通性测试
- 成功响应：
```json
{
  "success": true,
  "data": "svc-fault-scheduler: hello world",
  "error": null
}
```


## 错误处理与状态码

全局异常处理（GlobalExceptionHandler）将错误标准化为 ApiResponse.error，并设置合适的 HTTP 状态码。常见错误码与对应 HTTP 状态：

- 400
  - INVALID_ARGUMENT（参数非法）
  - VALIDATION_FAILED（方法参数校验失败）
  - BIND_ERROR（数据绑定失败）
  - CONSTRAINT_VIOLATION（约束校验失败）
  - TYPE_MISMATCH（参数类型不匹配）
  - JSON_PROCESSING_ERROR（JSON 格式错误）
- 401
  - K8S_AUTH_FAILED（K8s 认证失败）
- 403
  - K8S_PERMISSION_DENIED（K8s 权限不足）
- 404
  - RESOURCE_NOT_FOUND（资源不存在）
  - K8S_RESOURCE_NOT_FOUND（K8s 资源不存在）
- 500
  - K8S_ERROR（K8s 客户端错误）
  - REDIS_CONNECTION_FAILED（Redis 连接失败）
  - FAULT_SCHEDULER_ERROR（故障调度内部错误）
  - RUNTIME_ERROR / INTERNAL_ERROR（通用内部错误）

错误响应示例（参数类型不匹配 400）：
```json
{
  "success": false,
  "data": null,
  "error": { "code": "TYPE_MISMATCH", "message": "Parameter 'durationSec' should be of type 'Integer'" }
}
```

错误响应示例（资源不存在 404）：
```json
{
  "success": false,
  "data": null,
  "error": { "code": "RESOURCE_NOT_FOUND", "message": "Fault not found: blade-unknown" }
}
```


## 调用时机与前置条件

- 基础环境
  - ChaosBlade Operator 已部署，CRD 注册（chaosblade.io/v1alpha1）
  - svc-fault-scheduler 具备访问 K8s API 的配置（application.yml 中 kubernetes.api-url 等）
  - Redis 可用（spring.data.redis.* 配置正确）
- 建议流程
  1) POST /api/faults/execute 发起故障（可指定 durationSec TTL）
  2) GET /api/faults/{bladeName}/status 轮询状态与事件
  3) 如需详情/审计：GET /api/faults/{bladeName}
  4) 结束实验：DELETE /api/faults/{bladeName}（或等待 TTL 自动删除）
- 幂等与约束
  - name 冲突将拒绝（INVALID_ARGUMENT）
  - durationSec 未传时使用默认 TTL（0 表示不自动删除）
  - events 数量上限受 app.faults.events-limit 控制


## 备注：接口路径风格差异说明

项目历史文档与部分测试中曾出现“冒号风格”的路径（例：/api/faults:execute）。当前主代码（FaultsController）已采用 REST 风格路径 /api/faults/execute。本文档以实际代码为准。若需要兼容冒号风格路径，请在网关层或控制器层单独添加路由映射。
