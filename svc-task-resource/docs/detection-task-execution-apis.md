# 检测任务执行相关接口说明（svc-task-resource）

本文档描述 svc-task-resource 模块中与“任务执行”相关的三个接口：
- 代理执行接口（转发到 svc-task-executor）
- 任务执行详情（新路径，按 executionId 查询）
- 执行列表分页查询（按多条件筛选）

统一说明：除代理接口外，返回格式为统一的 ApiResponse 包装：

```
{
  "success": true|false,
  "data": <对象或分页对象>,
  "error": {"code": "...", "message": "...", "details": {...}} (出错时)
}
```

---

## 1) 代理执行接口
- Endpoint: POST /api/detection-tasks/{taskId}/execute
- Method: POST
- Path Params:
  - taskId (Long) 任务ID
- Request Body: 透明透传（如有），Content-Type 一般为 application/json
- 功能：将请求代理转发到 svc-task-executor: `POST http://localhost:8102/api/tasks/{taskId}/execute`
- 响应：状态码与响应体原样透传 executor 的返回（不做业务处理）
- 日志：记录转发前后状态；失败时返回 502 Bad Gateway

说明：executor 的返回（通常也是统一 ApiResponse）将不被二次包装，保持原样，便于前后端联调与兼容。

示例（curl）：

```
curl -X POST \
  http://127.0.0.1:8101/api/detection-tasks/123/execute \
  -H 'Content-Type: application/json' \
  -d '{"options":{"dryRun":false}}'
```

可能的错误：
- 502 EXECUTOR_PROXY_FAILED：无法连接或调用 svc-task-executor

错误响应示例：
```
HTTP/1.1 502 Bad Gateway
{
  "success": false,
  "error": {"code":"EXECUTOR_PROXY_FAILED", "message": "<异常信息>"}
}
```

---

## 2) 任务执行详情（增强版，按 executionId）
- Endpoint: GET /api/task-executions/{executionId} （别名 /api/executions/{executionId}）
- Method: GET
- Path Params:
  - executionId (Long) 执行记录ID
- Request Params: 无
- Response: ApiResponse<ExecutionDetailsDto>

ExecutionDetailsDto 结构：
- basic（执行基础信息）
  - id (Long) 执行记录ID
  - taskName (String) 任务名称
  - environment (String) 命名空间/环境
  - api (Api 对象) 任务关联 API 信息
  - initiator (String) 发起人
  - startTime (datetime) 开始时间
  - currentStatus (String) 当前状态（INIT/…/DONE/FAILED）
  - cumulativeDuration (Long) 累计时长（秒）
- logs（执行日志）
  - 当前为空列表（后续可接入 task_execution_log）
- realtime（实时状态统计）
  - totalTestCases (Integer) 总用例数
  - completedTestCases (Integer) 已完成用例数
  - totalServices (Integer) 总服务数
  - completedServices (Integer) 已完成服务数
  - testingServices (Integer) 正在测试的服务数
- faultInjections（故障注入摘要）
  - 数组，每项包含：
    - serviceName (String) 服务名
    - faultTypes (List<String>) 注入的故障类型列表
- testCases（测试用例列表，视实现可为精简视图）

成功响应示例：
```
{
  "success": true,
  "data": {
    "basic": {
      "id": 567,
      "taskName": "链路稳定性巡检",
      "environment": "default",
      "api": { "id": 31005, "operationId": "getOrder", ... },
      "initiator": "alice",
      "startTime": "2025-09-07 23:00:01",
      "currentStatus": "DONE",
      "cumulativeDuration": 126
    },
    "logs": [],
    "realtime": {
      "totalTestCases": 12,
      "completedTestCases": 12,
      "totalServices": 5,
      "completedServices": 5,
      "testingServices": 0
    },
    "faultInjections": [
      {"serviceName": "inventory", "faultTypes": ["container-remove"]},
      {"serviceName": "payment", "faultTypes": ["cpu-load"]}
    ],
    "testCases": [ {"id": 1, ...}, {"id": 2, ...} ]
  }
}
```

错误情况：
- TASK_EXECUTION_NOT_FOUND：executionId 不存在

---

## 3) 执行列表分页查询（支持筛选）
- Endpoint: GET /api/task-executions （别名 /api/executions）
- Method: GET
- Query Params：
  - taskId (Long, 可选) 按任务ID筛选
  - status (String, 可选) 状态筛选（INIT/GENERATING_CASES/ANALYZING_PATTERNS/RECORDING_READY/INJECTING_AND_REPLAYING/RULES_READY/LOAD_TEST_BASELINE/DONE/FAILED）
  - namespace (String, 可选) 命名空间筛选
  - startDate (String, 可选) 开始时间（格式：yyyy-MM-dd HH:mm:ss）
  - endDate (String, 可选) 结束时间（格式：yyyy-MM-dd HH:mm:ss）
  - page (int, 默认1) 页码
  - size (int, 默认20) 每页大小
- 排序：按 started_at 降序
- Response: ApiResponse<PageResponse<TaskExecutionView>>

TaskExecutionView 字段：
- id (Long)
- status (String)
- namespace (String)
- requestNum (Integer)
- errorCode (String)
- errorMsg (String)
- startedAt (datetime)
- finishedAt (datetime|null)
- duration (Long, 秒)

示例（curl）：
```
curl -G 'http://127.0.0.1:8101/api/task-executions' \
  --data-urlencode 'status=DONE' \
  --data-urlencode 'namespace=default' \
  --data-urlencode 'page=1' \
  --data-urlencode 'size=20'
```

成功响应示例：
```
{
  "success": true,
  "data": {
    "items": [
      {"id": 567, "status":"DONE", "namespace":"default", "requestNum":20, "startedAt":"2025-09-07 23:00:01", "finishedAt":"2025-09-07 23:02:07", "duration":126},
      {"id": 566, "status":"FAILED", "namespace":"default", "requestNum":20, "startedAt":"2025-09-07 21:10:00", "finishedAt":"2025-09-07 21:12:13", "duration":133}
    ],
    "total": 2,
    "page": 1,
    "size": 20,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

错误情况：
- 参数格式错误（时间格式不正确等）→ 返回统一 ApiResponse.error

---

## 其他说明
- 任务详情 GET /api/detection-tasks/{id} 的返回对象中，已补充字段：
  - http_req_def（与 apiDefinition 等价，便于前端兼容）
  - request_num（来自 DetectionTask.requestNum）
- 如需执行日志实时查询接口，可结合 task_execution_log 表在后续版本提供。

