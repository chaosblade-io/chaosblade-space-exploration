ip：http://1.94.151.57:8105

## 目录
- 背景与统一返回
- 接口分组与基础信息
- 1. 开始录制
- 2. 获取录制状态
- 3. 停止录制
- 4. 开始拦截
- 5. 停止拦截
- 6. 查看录制条目
- 错误说明与示例
- 调用时机与业务指引
- 变更备注（路径名校正）


## 背景与统一返回

- 服务名：svc-reqrsp-proxy
- 基础路径：无（以下均为绝对路径）
- 统一返回结构：所有接口均返回一个统一的 ApiResponse 包装
  - success: boolean
  - data: 任意类型（成功时返回）
  - error: { code: string, message: string, details?: any }（失败时返回）

成功示例：
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

失败示例：
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "500",
    "message": "服务器内部错误: 详细错误信息"
  }
}
```

注意：参数校验失败等异常会以 HTTP 400/500 并带有上述错误体返回；部分业务错误在控制器内被捕获后以 HTTP 200 返回，但 success=false、error 有明确信息。


## 接口分组与基础信息

- 录制功能 API（RecordingController）
  - Base: /api/recordings
  - 开始录制：POST /api/recordings/start
  - 获取录制状态：GET /api/recordings/{recordingId}
  - 停止录制：POST /api/recordings/{recordingId}/stop
  - 查看录制条目：GET /api/recordings/{recordingId}/entries

- 拦截功能 API（InterceptionController）
  - Base: /api/interceptions
  - 开始拦截：POST /api/interceptions/start
  - 停止拦截：POST /api/interceptions/session/{sessionId}/stop


## 1) 开始录制

### 基本信息
- 方法：POST
- 路径：/api/recordings/start
- 用途：为指定服务启动基于 Envoy Tap 的 HTTP 请求录制，自动注入 sidecar、重定向 Service、应用录制规则、等待滚动生效。
- 调用时机：启动一次新的录制会话，供后续采集请求样本和模式分析。

### 请求体
字段说明：
- namespace (string, required)：K8s 命名空间
- serviceName (string, required)：服务名（同时作为 Deployment 名）
- appPort (int, optional)：应用容器端口，不传将自动探测 Service targetPort
- rules (RecordingRule[], required)：录制规则列表
  - path (string, required)：匹配路径（如 /api/v1/preserveservice/preserve）
  - method (string, required, 取值：GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS）
- durationSec (int, optional)：录制持续时间（秒），可选；指定后将自动停止

请求示例：
```json
{
  "namespace": "train-ticket",
  "serviceName": "ts-preserve-service",
  "appPort": 12031,
  "rules": [
    { "path": "/api/v1/preserveservice/preserve", "method": "POST" },
    { "path": "/api/v1/securityservice/validate", "method": "POST" }
  ],
  "durationSec": 300
}
```

### 成功响应
data 为 RecordingResponse：
- recordingId (string)
- status (string)："RUNNING"
- message (string)

示例：
```json
{
  "success": true,
  "data": {
    "recordingId": "rec-20250902-153015-8a1b2c3d",
    "status": "RUNNING",
    "message": "Recording started successfully"
  },
  "error": null
}
```

### 可能状态码
- 200：成功（或业务失败但以 success=false 表示）
- 400：参数校验失败（如 rules 为空、method 非法）
- 500：内部错误（如无法探测端口、K8s 资源失败）


## 2) 获取录制状态

### 基本信息
- 方法：GET
- 路径：/api/recordings/{recordingId}
- 用途：查询录制会话状态、K8s 资源状态统计、收集条目计数等
- 调用时机：录制过程中或结束后查询状态

### 路径参数
- recordingId (string, required)：开始录制时返回的 ID

### 成功响应
data 为 RecordingStatusResponse（在 RecordingResponse 基础上扩展）：
- recordingId, status, message（同上）
- namespace (string)
- serviceName (string)
- appPortOriginal (int)：录制前 Service 的原始 targetPort
- envoyPort (int)：Envoy 暴露端口（通常 15006）
- rules (RecordingRule[])：录制规则
- startAt (string, ISO-8601)
- endAt (string, ISO-8601)（未结束为空）
- durationSec (int)
- entryCount (long)：已采集条目数
- deploymentStatus (string)：READY|NOT_READY|NOT_FOUND|ERROR|UNKNOWN
- serviceStatus (string)：ACTIVE|NOT_FOUND|ERROR

示例：
```json
{
  "success": true,
  "data": {
    "recordingId": "rec-20250902-153015-8a1b2c3d",
    "status": "RECORDING",
    "message": null,
    "namespace": "train-ticket",
    "serviceName": "ts-preserve-service",
    "appPortOriginal": 12031,
    "envoyPort": 15006,
    "rules": [
      {"path": "/api/v1/preserveservice/preserve", "method": "POST"}
    ],
    "startAt": "2025-09-02T15:30:16.000",
    "endAt": null,
    "durationSec": 300,
    "entryCount": 42,
    "deploymentStatus": "READY",
    "serviceStatus": "ACTIVE"
  },
  "error": null
}
```

### 可能状态码
- 200：成功
- 500：读取状态失败（异常）


## 3) 停止录制

### 基本信息
- 方法：POST
- 路径：/api/recordings/{recordingId}/stop
- 用途：结束录制，恢复 Service 端口、移除/禁用 sidecar、删除本次录制的 ConfigMap，并进行一次最终采集
- 调用时机：录制完成时或异常需要终止

### 路径参数
- recordingId (string, required)

### 成功响应
data 为 RecordingResponse：
- status："STOPPED"

示例：
```json
{
  "success": true,
  "data": {
    "recordingId": "rec-20250902-153015-8a1b2c3d",
    "status": "STOPPED",
    "message": "Recording stopped successfully"
  },
  "error": null
}
```

### 可能状态码
- 200：成功
- 500：停止失败（资源恢复失败、删除 ConfigMap 失败等）


## 4) 开始拦截

### 基本信息
- 方法：POST
- 路径：/api/interceptions/start
- 用途：为目标服务添加拦截规则（智能模式）
  - 若目标服务当前处于录制状态：在现有配置基础上添加拦截
  - 若不在录制：可启用“仅拦截模式”（不包含录制）
- 调用时机：希望对指定接口返回模拟响应，或在录制期间叠加拦截规则

### 请求体
AddInterceptionRequest：
- namespace (string, required)
- serviceName (string, required)
- interceptionRules (InterceptionRule[], required)
  - path (string)
  - method (string, 取值同上)
  - mockResponse (MockResponse, required)
    - statusCode (int, 100~599)
    - headers (map<string,string>)
    - body (string)
    - contentType (string, 默认 "application/json; charset=utf-8")
- enableRecording (boolean, default=true)：
  - true：拦截 + 录制
  - false：仅拦截（纯拦截模式）

请求示例：
```json
{
  "namespace": "train-ticket",
  "serviceName": "ts-preserve-service",
  "interceptionRules": [
    {
      "path": "/api/v1/preserveservice/preserve",
      "method": "POST",
      "mockResponse": {
        "statusCode": 200,
        "headers": {
          "x-mock-response": "true"
        },
        "body": "{\"result\":\"ok\",\"orderId\":\"MOCK-12345\"}",
        "contentType": "application/json; charset=utf-8"
      }
    }
  ],
  "enableRecording": true
}
```

### 成功响应
data 为 InterceptionResponse：
- sessionId (string)（使用录制状态的 recordingId 作为会话标识）
- status (string)
- message (string)
- recordingRulesCount (int)
- interceptionRulesCount (int)
- mode (string)："RECORDING_WITH_INTERCEPTION" | "INTERCEPTION_ONLY"

示例：
```json
{
  "success": true,
  "data": {
    "sessionId": "rec-20250902-153015-8a1b2c3d",
    "status": "APPLIED",
    "message": "Interception rules applied",
    "recordingRulesCount": 1,
    "interceptionRulesCount": 1,
    "mode": "RECORDING_WITH_INTERCEPTION"
  },
  "error": null
}
```

### 可能状态码
- 200：成功
- 400：参数校验失败
- 500：渲染或应用配置失败、K8s 操作失败


## 5) 停止拦截

### 基本信息
- 方法：POST
- 路径：/api/interceptions/session/{sessionId}/stop
- 用途：停止拦截会话，恢复 Service、移除 sidecar、删除 ConfigMap，更新状态为 STOPPED
- 调用时机：拦截验证完成后清理环境

### 路径参数
- sessionId (string, required)：开始拦截返回的 sessionId（即 recordingId）

### 成功响应
data 为 InterceptionResponse：
```json
{
  "success": true,
  "data": {
    "sessionId": "rec-20250902-153015-8a1b2c3d",
    "status": "STOPPED",
    "message": "Interception stopped",
    "recordingRulesCount": 0,
    "interceptionRulesCount": 0,
    "mode": "INTERCEPTION_ONLY"
  },
  "error": null
}
```

### 可能状态码
- 200：成功
- 500：停止失败（资源恢复失败、删除失败等）


## 6) 查看录制条目

### 基本信息
- 方法：GET
- 路径：/api/recordings/{recordingId}/entries
- 用途：分页读取录制的请求-响应条目（TapCollector 已将条目写入 Redis）
- 调用时机：录制期间/结束后查看具体抓取到的 HTTP 请求与响应

### 路径参数
- recordingId (string, required)

### 查询参数
- offset (int, default=0, >=0)
- limit (int, default=50, >=1)

### 成功响应
data 为 RecordedEntry[]：
- recordingId, timestamp, namespace, serviceName, pod
- path, method, status
- xRequestId, traceparent
- requestHeaders (map<string,string>)：包含 Authorization/Cookie 等原样保留
- responseHeaders (map<string,string>)
- requestBody (string)
- responseBody (string)
- reqBytes, respBytes (long)
- requestTruncated, responseTruncated (boolean)

示例：
```json
{
  "success": true,
  "data": [
    {
      "recordingId": "rec-20250902-153015-8a1b2c3d",
      "timestamp": "2025-09-02T15:30:20.123Z",
      "namespace": "train-ticket",
      "serviceName": "ts-preserve-service",
      "pod": "ts-preserve-service-7c69f4d9b-abcde",
      "path": "/api/v1/preserveservice/preserve",
      "method": "POST",
      "status": 200,
      "xRequestId": "a1b2c3d4e5f6",
      "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
      "requestHeaders": {
        ":method": "POST",
        ":path": "/api/v1/preserveservice/preserve",
        "Host": "ts-preserve-service",
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": "Bearer eyJhbGciOi...",
        "Cookie": "JSESSIONID=xxx; other=yyy"
      },
      "responseHeaders": {
        "Content-Type": "application/json",
        "x-envoy-upstream-service-time": "12"
      },
      "requestBody": "{\"trainNumber\":\"D1345\",\"seatType\":\"DongCheOne\"}",
      "responseBody": "{\"status\":0,\"orderId\":\"S202509020001\"}",
      "reqBytes": 256,
      "respBytes": 512,
      "requestTruncated": false,
      "responseTruncated": false
    }
  ],
  "error": null
}
```

### 可能状态码
- 200：成功
- 500：读取失败（录制不存在、Redis 读取异常等）


## 错误说明与示例

- 参数校验失败（400）：由全局异常处理器返回
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "400",
    "message": "参数验证失败: 命名空间不能为空, 录制规则不能为空"
  }
}
```

- 业务异常（多数场景为 HTTP 200 + success=false）：
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "500",
    "message": "Failed to start recording: Cannot determine application port for service: ts-preserve-service"
  }
}
```

- 未捕获异常（500）：
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "500",
    "message": "服务器内部错误: 详细错误信息"
  }
}
```


## 调用时机与业务指引

- 开始录制（/api/recordings/start）
  - 在进行请求模式挖掘或回放链路构建前，先对目标服务开启录制
  - 可通过 durationSec 设置自动停止，或手动停止

- 获取录制状态（/api/recordings/{id}）
  - 在录制滚动生效后，确认 Deployment/Service 状态与采集进度（entryCount）

- 查看录制条目（/api/recordings/{id}/entries）
  - 需要具体样本流量（请求头/体/响应体）时使用
  - 支持分页读取；Authorization/Cookie 原样保留，便于复现请求

- 停止录制（/api/recordings/{id}/stop）
  - 录制完成后及时清理现场，恢复 targetPort、移除 sidecar、删除 ConfigMap

- 开始拦截（/api/interceptions/start）
  - 在录制期间叠加拦截返回模拟结果，或独立启用纯拦截模式（enableRecording=false）
  - 支持自定义响应码/头/体与 contentType

- 停止拦截（/api/interceptions/session/{sessionId}/stop）
  - 完成验证后恢复现场，删除拦截配置


## 变更备注（路径名校正）

你提供的“接口列表”中使用了单数形式的 recording/interception 路径；当前代码实现采用复数形式：
- 录制：/api/recordings/...
- 拦截：/api/interceptions/...

本文档严格基于当前代码实现（RecordingController、InterceptionController），以复数路径为准。若需兼容你列出的单数路径，请告知，我可以补充路由映射或网关层转发规则。
