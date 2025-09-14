# svc-task-resource 微服务 API 测试用例文档

## 1. 服务概述

`svc-task-resource` 是混沌工程空间探索项目中的任务资源管理微服务，主要负责：

- **系统管理**：管理目标系统的基本信息、版本和状态
- **API管理**：管理系统中的API接口信息，包括路径、方法、参数等
- **拓扑管理**：管理API调用拓扑关系和节点信息
- **故障类型管理**：管理可用的故障注入类型和配置
- **故障配置管理**：管理具体的故障注入配置
- **检测任务管理**：管理故障检测任务的创建、执行和监控
- **故障执行管理**：管理故障注入的实际执行过程

**服务端口**: 8101  
**数据库**: MySQL  
**基础路径**: `/api`

## 2. API端点列表

### 2.1 基础健康检查
- `GET /hello` - 服务健康检查

### 2.2 系统管理 (SystemController)
- `GET /api/systems` - 获取系统列表
- `GET /api/systems/{systemId}` - 获取系统详情
- `POST /api/systems` - 创建新系统
- `PUT /api/systems/{systemId}` - 更新系统信息
- `DELETE /api/systems/{systemId}` - 删除系统
- `GET /api/systems/{systemId}/topologies` - 获取系统拓扑列表
- `POST /api/systems/{systemId}/topologies` - 创建系统拓扑

### 2.3 API管理 (ApiController)
- `GET /api/systems/{systemId}/apis` - 获取系统API列表
- `GET /api/systems/{systemId}/apis/{operationId}` - 获取特定API详情
- `POST /api/systems/{systemId}/apis` - 创建系统API
- `GET /api/apis/{apiId}` - 获取API详情

### 2.4 拓扑管理 (TopologyController)
- `GET /api/topologies/{topologyId}` - 获取拓扑详情
- `PUT /api/topologies/{topologyId}` - 更新拓扑信息
- `DELETE /api/topologies/{topologyId}` - 删除拓扑
- `GET /api/topologies/{topologyId}/nodes` - 获取拓扑节点列表

### 2.5 故障类型管理 (FaultTypeController)
- `GET /api/fault-types` - 获取故障类型列表
- `GET /api/fault-types/{faultTypeId}` - 获取故障类型详情
- `GET /api/fault-types/by-name/{name}` - 根据名称获取故障类型
- `POST /api/fault-types` - 创建故障类型
- `PUT /api/fault-types/{faultTypeId}` - 更新故障类型
- `DELETE /api/fault-types/{faultTypeId}` - 删除故障类型
- `GET /api/fault-types/category/{category}` - 根据分类获取故障类型
- `GET /api/fault-types/categories` - 获取所有分类
- `GET /api/fault-types/severity-levels` - 获取所有严重程度
- `GET /api/fault-types/statistics/categories` - 获取分类统计

### 2.6 故障配置管理 (FaultConfigurationController)
- `GET /api/fault-configs` - 获取故障配置列表
- `GET /api/fault-configs/{configId}` - 获取故障配置详情
- `POST /api/fault-configs` - 创建故障配置
- `PUT /api/fault-configs/{configId}` - 更新故障配置
- `DELETE /api/fault-configs/{configId}` - 删除故障配置
- `POST /api/fault-configs/{configId}/enable` - 启用故障配置
- `POST /api/fault-configs/{configId}/disable` - 禁用故障配置

### 2.7 检测任务管理 (DetectionTaskController)
- `GET /api/detection-tasks` - 获取检测任务列表
- `GET /api/detection-tasks/{taskId}` - 获取检测任务详情
- `POST /api/detection-tasks` - 创建检测任务
- `PUT /api/detection-tasks/{taskId}` - 更新检测任务
- `DELETE /api/detection-tasks/{taskId}` - 删除检测任务
- `POST /api/detection-tasks/{taskId}/start` - 启动检测任务
- `POST /api/detection-tasks/{taskId}/stop` - 停止检测任务
- `GET /api/detection-tasks/running` - 获取运行中任务
- `GET /api/detection-tasks/statistics/status` - 获取状态统计

### 2.8 故障执行管理 (FaultExecutionController)
- `GET /api/fault-executions` - 获取故障执行列表
- `GET /api/fault-executions/{executionId}` - 获取故障执行详情
- `POST /api/fault-executions` - 创建故障执行
- `POST /api/fault-executions/{executionId}/start` - 开始故障执行
- `POST /api/fault-executions/{executionId}/stop` - 停止故障执行
- `POST /api/fault-executions/{executionId}/complete` - 完成故障执行
- `POST /api/fault-executions/{executionId}/fail` - 故障执行失败
- `PATCH /api/fault-executions/{executionId}/progress` - 更新执行进度
- `GET /api/fault-executions/running` - 获取运行中执行
- `GET /api/fault-executions/statistics` - 获取执行统计

## 3. 详细测试用例

### 3.1 服务健康检查

#### 测试用例 1.1: 基础健康检查
**HTTP方法**: GET  
**请求URL**: `http://localhost:8101/hello`  
**请求头**: 
```
Content-Type: application/json
```

**请求体**: 无

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": "svc-task-resource: hello world",
  "error": null
}
```

### 3.2 系统管理测试用例

#### 测试用例 2.1: 获取系统列表
**HTTP方法**: GET  
**请求URL**: `http://localhost:8101/api/systems?page=1&size=20`  
**请求头**: 
```
Content-Type: application/json
```

**查询参数**:
- `name` (可选): 系统名称过滤
- `status` (可选): 状态过滤 (ACTIVE, INACTIVE)
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认20

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "用户管理系统",
        "description": "负责用户注册、登录、权限管理",
        "version": "v1.0.0",
        "status": "ACTIVE",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 20,
    "totalPages": 1
  },
  "error": null
}
```

#### 测试用例 2.2: 创建新系统
**HTTP方法**: POST  
**请求URL**: `http://localhost:8101/api/systems`  
**请求头**: 
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "name": "订单管理系统",
  "description": "处理订单创建、支付、发货等业务",
  "version": "v1.0.0",
  "status": "ACTIVE"
}
```

**字段说明**:
- `name` (必需): 系统名称，最大100字符
- `description` (可选): 系统描述，最大500字符
- `version` (可选): 版本号，最大50字符
- `status` (可选): 状态，最大20字符

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 2,
    "name": "订单管理系统",
    "description": "处理订单创建、支付、发货等业务",
    "version": "v1.0.0",
    "status": "ACTIVE",
    "createdAt": "2024-01-01 11:00:00",
    "updatedAt": "2024-01-01 11:00:00"
  },
  "error": null
}
```

**错误场景**:
- 400: 参数验证失败
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "参数验证失败",
    "details": {
      "name": "系统名称不能为空"
    }
  }
}
```

#### 测试用例 2.3: 获取系统详情
**HTTP方法**: GET  
**请求URL**: `http://localhost:8101/api/systems/{systemId}`  
**请求头**: 
```
Content-Type: application/json
```

**路径参数**:
- `systemId`: 系统ID

**预期响应状态码**: 200

**成功响应示例**: 同创建系统响应

**错误场景**:
- 404: 系统不存在
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SYSTEM_NOT_FOUND",
    "message": "系统不存在: 999"
  }
}
```

#### 测试用例 2.4: 更新系统信息
**HTTP方法**: PUT  
**请求URL**: `http://localhost:8101/api/systems/{systemId}`  
**请求头**: 
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "name": "订单管理系统",
  "description": "处理订单创建、支付、发货、退款等业务",
  "version": "v1.1.0",
  "status": "ACTIVE"
}
```

**预期响应状态码**: 200

#### 测试用例 2.5: 删除系统
**HTTP方法**: DELETE  
**请求URL**: `http://localhost:8101/api/systems/{systemId}`  
**请求头**: 
```
Content-Type: application/json
```

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### 3.3 API管理测试用例

#### 测试用例 3.1: 获取系统API列表
**HTTP方法**: GET  
**请求URL**: `http://localhost:8101/api/systems/{systemId}/apis?page=1&size=50`  
**请求头**: 
```
Content-Type: application/json
```

**查询参数**:
- `method` (可选): HTTP方法过滤 (GET, POST, PUT, DELETE)
- `path` (可选): API路径过滤
- `tags` (可选): 标签过滤
- `status` (可选): 状态过滤
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认50

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "systemId": 1,
        "operationId": "getUserById",
        "path": "/users/{id}",
        "method": "GET",
        "summary": "根据ID获取用户信息",
        "description": "通过用户ID获取用户的详细信息",
        "tags": "用户管理",
        "requestSchema": "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}",
        "responseSchema": "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"}}}",
        "parametersSchema": "{\"path\":{\"id\":{\"type\":\"integer\",\"required\":true}}}",
        "status": "ACTIVE",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 50,
    "totalPages": 1
  },
  "error": null
}
```

#### 测试用例 3.2: 创建系统API
**HTTP方法**: POST  
**请求URL**: `http://localhost:8101/api/systems/{systemId}/apis`  
**请求头**: 
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "operationId": "createUser",
  "path": "/users",
  "method": "POST",
  "summary": "创建新用户",
  "description": "创建一个新的用户账户",
  "tags": "用户管理",
  "requestSchema": "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}},\"required\":[\"name\",\"email\"]}",
  "responseSchema": "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}",
  "parametersSchema": "{}",
  "status": "ACTIVE"
}
```

**字段说明**:
- `operationId` (必需): 操作ID，最大100字符
- `path` (必需): API路径，最大200字符
- `method` (必需): HTTP方法，最大20字符
- `summary` (可选): 摘要，最大200字符
- `description` (可选): 描述，最大1000字符
- `tags` (可选): 标签，最大500字符
- `requestSchema` (可选): 请求Schema (JSON格式)
- `responseSchema` (可选): 响应Schema (JSON格式)
- `parametersSchema` (可选): 参数Schema (JSON格式)
- `status` (可选): 状态，最大20字符

**预期响应状态码**: 200

### 3.4 故障类型管理测试用例

#### 测试用例 4.1: 获取故障类型列表
**HTTP方法**: GET  
**请求URL**: `http://localhost:8101/api/fault-types?page=1&size=20`  
**请求头**: 
```
Content-Type: application/json
```

**查询参数**:
- `category` (可选): 分类过滤
- `severityLevel` (可选): 严重程度过滤
- `status` (可选): 状态过滤
- `name` (可选): 名称过滤
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认20

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "网络延迟",
        "category": "网络故障",
        "description": "模拟网络请求延迟",
        "parametersSchema": "{\"type\":\"object\",\"properties\":{\"delay\":{\"type\":\"integer\",\"minimum\":100,\"maximum\":10000}}}",
        "exampleConfig": "{\"delay\":1000}",
        "supportedTargets": "API,SERVICE",
        "severityLevel": "MEDIUM",
        "status": "ACTIVE",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 20,
    "totalPages": 1
  },
  "error": null
}
```

#### 测试用例 4.2: 创建故障类型
**HTTP方法**: POST  
**请求URL**: `http://localhost:8101/api/fault-types`  
**请求头**: 
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "name": "服务超时",
  "category": "服务故障",
  "description": "模拟服务响应超时",
  "parametersSchema": "{\"type\":\"object\",\"properties\":{\"timeout\":{\"type\":\"integer\",\"minimum\":1000,\"maximum\":30000}}}",
  "exampleConfig": "{\"timeout\":5000}",
  "supportedTargets": "API,SERVICE",
  "severityLevel": "HIGH",
  "status": "ACTIVE"
}
```

**字段说明**:
- `name` (必需): 故障类型名称，最大100字符
- `category` (可选): 分类，最大50字符
- `description` (可选): 描述，最大1000字符
- `parametersSchema` (可选): 参数Schema (JSON格式)
- `exampleConfig` (可选): 示例配置 (JSON格式)
- `supportedTargets` (可选): 支持的目标，最大500字符
- `severityLevel` (可选): 严重程度，最大20字符
- `status` (可选): 状态，最大20字符

**预期响应状态码**: 200

### 3.5 故障配置管理测试用例

#### 测试用例 5.1: 获取故障配置列表
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/fault-configs?page=1&size=20`
**请求头**:
```
Content-Type: application/json
```

**查询参数**:
- `faultTypeId` (可选): 故障类型ID过滤
- `targetSystemId` (可选): 目标系统ID过滤
- `targetApiId` (可选): 目标API ID过滤
- `targetNodeId` (可选): 目标节点ID过滤
- `status` (可选): 状态过滤
- `enabled` (可选): 是否启用过滤
- `createdBy` (可选): 创建者过滤
- `name` (可选): 名称过滤
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认20

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "用户服务延迟注入",
        "description": "对用户服务API注入1秒延迟",
        "faultTypeId": 1,
        "targetSystemId": 1,
        "targetApiId": 1,
        "targetNodeId": null,
        "parameters": "{\"delay\":1000}",
        "scheduleConfig": "{\"type\":\"immediate\"}",
        "status": "ACTIVE",
        "enabled": true,
        "createdBy": "admin",
        "updatedBy": "admin",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 20,
    "totalPages": 1
  },
  "error": null
}
```

#### 测试用例 5.2: 创建故障配置
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-configs`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "name": "订单服务超时注入",
  "description": "对订单服务API注入5秒超时",
  "faultTypeId": 2,
  "targetSystemId": 2,
  "targetApiId": 5,
  "parameters": "{\"timeout\":5000}",
  "scheduleConfig": "{\"type\":\"cron\",\"expression\":\"0 0 12 * * ?\"}",
  "status": "ACTIVE",
  "enabled": true,
  "createdBy": "admin"
}
```

**字段说明**:
- `name` (必需): 配置名称，最大100字符
- `description` (可选): 描述，最大1000字符
- `faultTypeId` (必需): 故障类型ID
- `targetSystemId` (可选): 目标系统ID
- `targetApiId` (可选): 目标API ID
- `targetNodeId` (可选): 目标节点ID，最大100字符
- `parameters` (可选): 参数配置 (JSON格式)
- `scheduleConfig` (可选): 调度配置 (JSON格式)
- `status` (可选): 状态，最大20字符
- `enabled` (可选): 是否启用，默认true
- `createdBy` (可选): 创建者，最大100字符

**预期响应状态码**: 200

#### 测试用例 5.3: 启用/禁用故障配置
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-configs/{configId}/enable`
**请求URL**: `http://localhost:8101/api/fault-configs/{configId}/disable`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

### 3.6 检测任务管理测试用例

#### 测试用例 6.1: 获取检测任务列表
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/detection-tasks?page=1&size=20`
**请求头**:
```
Content-Type: application/json
```

**查询参数**:
- `faultConfigId` (可选): 故障配置ID过滤
- `targetSystemId` (可选): 目标系统ID过滤
- `targetApiId` (可选): 目标API ID过滤
- `status` (可选): 状态过滤 (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
- `createdBy` (可选): 创建者过滤
- `name` (可选): 任务名称过滤
- `startDate` (可选): 开始时间过滤，格式: yyyy-MM-dd HH:mm:ss
- `endDate` (可选): 结束时间过滤，格式: yyyy-MM-dd HH:mm:ss
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认20

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "用户服务故障检测",
        "description": "检测用户服务在延迟注入下的表现",
        "faultConfigId": 1,
        "targetSystemId": 1,
        "targetApiId": 1,
        "detectionConfig": "{\"duration\":300,\"metrics\":[\"response_time\",\"error_rate\"]}",
        "executionPlan": "{\"steps\":[{\"type\":\"inject\",\"duration\":60},{\"type\":\"monitor\",\"duration\":240}]}",
        "status": "PENDING",
        "progress": 0,
        "result": null,
        "errorMessage": null,
        "startedAt": null,
        "completedAt": null,
        "createdBy": "admin",
        "updatedBy": "admin",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 20,
    "totalPages": 1
  },
  "error": null
}
```

#### 测试用例 6.2: 创建检测任务
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/detection-tasks`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "name": "订单服务故障检测",
  "description": "检测订单服务在超时注入下的表现",
  "faultConfigId": 2,
  "targetSystemId": 2,
  "targetApiId": 5,
  "detectionConfig": "{\"duration\":600,\"metrics\":[\"response_time\",\"error_rate\",\"throughput\"]}",
  "executionPlan": "{\"steps\":[{\"type\":\"baseline\",\"duration\":120},{\"type\":\"inject\",\"duration\":180},{\"type\":\"recovery\",\"duration\":300}]}",
  "createdBy": "admin"
}
```

**字段说明**:
- `name` (必需): 任务名称，最大100字符
- `description` (可选): 任务描述，最大1000字符
- `faultConfigId` (必需): 故障配置ID
- `targetSystemId` (可选): 目标系统ID
- `targetApiId` (可选): 目标API ID
- `detectionConfig` (可选): 检测配置 (JSON格式)
- `executionPlan` (可选): 执行计划 (JSON格式)
- `createdBy` (可选): 创建者，最大100字符

**预期响应状态码**: 200

#### 测试用例 6.3: 启动检测任务
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/detection-tasks/{taskId}/start`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "status": "RUNNING",
    "progress": 0,
    "startedAt": "2024-01-01 12:00:00",
    "updatedAt": "2024-01-01 12:00:00"
  },
  "error": null
}
```

#### 测试用例 6.4: 获取运行中任务
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/detection-tasks/running`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

#### 测试用例 6.5: 获取状态统计
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/detection-tasks/statistics/status`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "PENDING": 5,
    "RUNNING": 2,
    "COMPLETED": 15,
    "FAILED": 1,
    "CANCELLED": 0
  },
  "error": null
}
```

### 3.7 故障执行管理测试用例

#### 测试用例 7.1: 获取故障执行列表
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/fault-executions?page=1&size=20`
**请求头**:
```
Content-Type: application/json
```

**查询参数**:
- `faultConfigId` (可选): 故障配置ID过滤
- `detectionTaskId` (可选): 检测任务ID过滤
- `targetSystemId` (可选): 目标系统ID过滤
- `targetApiId` (可选): 目标API ID过滤
- `targetNodeId` (可选): 目标节点ID过滤
- `status` (可选): 状态过滤 (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
- `createdBy` (可选): 创建者过滤
- `startDate` (可选): 开始时间过滤
- `endDate` (可选): 结束时间过滤
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认20

**预期响应状态码**: 200

#### 测试用例 7.2: 创建故障执行
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-executions`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "executionId": "exec_20240101_001",
  "faultConfigId": 1,
  "detectionTaskId": 1,
  "targetSystemId": 1,
  "targetApiId": 1,
  "executionPlan": "{\"type\":\"delay\",\"parameters\":{\"delay\":1000},\"duration\":300}",
  "createdBy": "admin"
}
```

**字段说明**:
- `executionId` (必需): 执行ID，唯一标识，最大100字符
- `faultConfigId` (必需): 故障配置ID
- `detectionTaskId` (可选): 检测任务ID
- `targetSystemId` (可选): 目标系统ID
- `targetApiId` (可选): 目标API ID
- `targetNodeId` (可选): 目标节点ID，最大100字符
- `executionPlan` (可选): 执行计划 (JSON格式)
- `createdBy` (可选): 创建者，最大100字符

**预期响应状态码**: 200

#### 测试用例 7.3: 开始故障执行
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-executions/{executionId}/start`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

#### 测试用例 7.4: 更新执行进度
**HTTP方法**: PATCH
**请求URL**: `http://localhost:8101/api/fault-executions/{executionId}/progress`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "progress": 50
}
```

**预期响应状态码**: 200

#### 测试用例 7.5: 完成故障执行
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-executions/{executionId}/complete`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "result": "{\"success\":true,\"metrics\":{\"avg_response_time\":1200,\"error_rate\":0.02}}",
  "metrics": "{\"total_requests\":1000,\"failed_requests\":20,\"duration_ms\":300000}"
}
```

**预期响应状态码**: 200

#### 测试用例 7.6: 故障执行失败
**HTTP方法**: POST
**请求URL**: `http://localhost:8101/api/fault-executions/{executionId}/fail`
**请求头**:
```
Content-Type: application/json
```

**请求体示例**:
```json
{
  "errorMessage": "目标服务不可达，无法注入故障"
}
```

**预期响应状态码**: 200

### 3.8 拓扑管理测试用例

#### 测试用例 8.1: 获取拓扑详情
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/topologies/{topologyId}`
**请求头**:
```
Content-Type: application/json
```

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "systemId": 1,
    "name": "用户服务拓扑",
    "description": "用户服务的API调用拓扑图",
    "version": "v1.0",
    "status": "ACTIVE",
    "createdAt": "2024-01-01 10:00:00",
    "updatedAt": "2024-01-01 10:00:00"
  },
  "error": null
}
```

#### 测试用例 8.2: 获取拓扑节点列表
**HTTP方法**: GET
**请求URL**: `http://localhost:8101/api/topologies/{topologyId}/nodes?page=1&size=50`
**请求头**:
```
Content-Type: application/json
```

**查询参数**:
- `nodeType` (可选): 节点类型过滤
- `serviceName` (可选): 服务名称过滤
- `method` (可选): HTTP方法过滤
- `page` (可选): 页码，默认1
- `size` (可选): 每页大小，默认50

**预期响应状态码**: 200

**成功响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "topologyId": 1,
        "nodeId": "user_service_get_user",
        "nodeType": "API",
        "serviceName": "用户服务",
        "endpoint": "/users/{id}",
        "method": "GET",
        "positionX": 100.0,
        "positionY": 200.0,
        "metadata": "{\"timeout\":5000,\"retries\":3}",
        "createdAt": "2024-01-01 10:00:00",
        "updatedAt": "2024-01-01 10:00:00"
      }
    ],
    "totalElements": 1,
    "currentPage": 1,
    "pageSize": 50,
    "totalPages": 1
  },
  "error": null
}
```

## 4. Postman测试指南

### 4.1 环境变量设置

在Postman中创建环境变量：

```
BASE_URL = http://localhost:8101
SYSTEM_ID = 1
API_ID = 1
FAULT_TYPE_ID = 1
FAULT_CONFIG_ID = 1
DETECTION_TASK_ID = 1
EXECUTION_ID = exec_001
```

### 4.2 测试执行顺序

建议按以下顺序执行测试：

1. **服务健康检查** - 确保服务正常运行
2. **系统管理** - 创建测试系统
3. **API管理** - 为系统创建API
4. **故障类型管理** - 创建故障类型
5. **故障配置管理** - 创建故障配置
6. **检测任务管理** - 创建和管理检测任务
7. **故障执行管理** - 执行故障注入

### 4.3 依赖关系说明

- **API管理** 依赖 **系统管理**：需要先创建系统才能创建API
- **拓扑管理** 依赖 **系统管理**：需要先创建系统才能创建拓扑
- **故障配置** 依赖 **故障类型**：需要先创建故障类型
- **检测任务** 依赖 **故障配置**：需要先创建故障配置
- **故障执行** 依赖 **故障配置**：需要先创建故障配置

### 4.4 测试数据准备脚本

#### 4.4.1 创建基础测试数据的Postman脚本

**Pre-request Script示例**:
```javascript
// 设置动态变量
pm.environment.set("timestamp", Date.now());
pm.environment.set("random_id", Math.floor(Math.random() * 10000));

// 生成执行ID
const executionId = "exec_" + new Date().toISOString().slice(0,10).replace(/-/g,'') + "_" + Math.floor(Math.random() * 1000);
pm.environment.set("execution_id", executionId);
```

**Test Script示例**:
```javascript
// 验证响应状态
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

// 验证响应结构
pm.test("Response has success field", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('success');
    pm.expect(jsonData.success).to.be.true;
});

// 保存创建的资源ID
if (pm.response.json().data && pm.response.json().data.id) {
    pm.environment.set("created_id", pm.response.json().data.id);
}
```

#### 4.4.2 数据清理脚本

**清理测试数据的请求顺序**:
1. 删除故障执行
2. 删除检测任务
3. 删除故障配置
4. 删除故障类型
5. 删除API
6. 删除拓扑
7. 删除系统

### 4.5 性能测试建议

#### 4.5.1 负载测试场景
- **并发创建系统**: 50个并发请求创建系统
- **批量查询API**: 100个并发请求查询系统API列表
- **故障执行压力测试**: 20个并发故障执行

#### 4.5.2 性能指标监控
- **响应时间**: 平均响应时间 < 500ms
- **吞吐量**: QPS > 100
- **错误率**: < 1%
- **资源使用**: CPU < 80%, 内存 < 2GB

## 5. 错误场景测试

### 5.1 参数验证错误

#### 测试用例: 创建系统时缺少必需参数
**请求体**:
```json
{
  "description": "缺少name字段"
}
```

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "参数验证失败",
    "details": {
      "name": "系统名称不能为空"
    }
  }
}
```

### 5.2 资源不存在错误

#### 测试用例: 获取不存在的系统
**请求URL**: `GET /api/systems/999999`

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SYSTEM_NOT_FOUND",
    "message": "系统不存在: 999999"
  }
}
```

### 5.3 业务逻辑错误

#### 测试用例: 删除正在使用的系统
**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SYSTEM_IN_USE",
    "message": "系统正在使用中，无法删除"
  }
}
```

### 5.4 状态转换错误

#### 测试用例: 重复启动已运行的任务
**请求URL**: `POST /api/detection-tasks/{taskId}/start`
**前置条件**: 任务状态为RUNNING

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_STATE_TRANSITION",
    "message": "任务已在运行中，无法重复启动"
  }
}
```

#### 测试用例: 停止未运行的任务
**请求URL**: `POST /api/detection-tasks/{taskId}/stop`
**前置条件**: 任务状态为PENDING

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_STATE_TRANSITION",
    "message": "任务未运行，无法停止"
  }
}
```

### 5.5 权限和认证错误

#### 测试用例: 未授权访问
**请求头**: 不包含认证信息

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "未授权访问"
  }
}
```

#### 测试用例: 权限不足
**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "FORBIDDEN",
    "message": "权限不足"
  }
}
```

### 5.6 并发冲突错误

#### 测试用例: 并发修改同一资源
**场景**: 两个用户同时修改同一个系统信息

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CONCURRENT_MODIFICATION",
    "message": "资源已被其他用户修改，请刷新后重试"
  }
}
```

### 5.7 数据库连接错误

#### 测试用例: 数据库不可用时的响应
**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "系统内部错误"
  }
}
```

### 5.8 外部服务依赖错误

#### 测试用例: 目标服务不可达
**场景**: 故障注入时目标服务不可达

**预期响应**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "TARGET_SERVICE_UNAVAILABLE",
    "message": "目标服务不可达，无法执行故障注入"
  }
}
```

## 6. 高级测试场景

### 6.1 端到端测试流程

#### 场景: 完整的故障注入检测流程
1. **创建系统** → 获取systemId
2. **创建API** → 获取apiId
3. **创建故障类型** → 获取faultTypeId
4. **创建故障配置** → 获取configId
5. **创建检测任务** → 获取taskId
6. **启动检测任务** → 验证状态变为RUNNING
7. **创建故障执行** → 获取executionId
8. **开始故障执行** → 验证状态变为RUNNING
9. **更新执行进度** → 验证进度更新
10. **完成故障执行** → 验证状态变为COMPLETED
11. **停止检测任务** → 验证状态变为COMPLETED
12. **查看执行结果** → 验证结果数据

### 6.2 数据一致性测试

#### 场景: 级联删除测试
1. 创建系统、API、故障配置、检测任务
2. 删除系统
3. 验证相关的API、故障配置、检测任务是否正确处理

#### 场景: 事务回滚测试
1. 创建包含无效数据的复合请求
2. 验证整个事务是否回滚
3. 确认数据库状态一致性

### 6.3 性能边界测试

#### 场景: 大数据量查询
- 创建1000个系统
- 查询系统列表，验证分页性能
- 验证响应时间在可接受范围内

#### 场景: 并发执行限制
- 同时启动100个检测任务
- 验证系统是否正确限制并发数
- 验证资源使用情况

## 7. 自动化测试建议

### 7.1 CI/CD集成

#### 测试阶段配置
```yaml
# .github/workflows/api-test.yml
name: API Tests
on: [push, pull_request]
jobs:
  api-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Start Services
        run: docker-compose up -d
      - name: Wait for Services
        run: sleep 30
      - name: Run API Tests
        run: |
          newman run postman_collection.json \
            --environment postman_environment.json \
            --reporters cli,junit \
            --reporter-junit-export results.xml
      - name: Publish Test Results
        uses: dorny/test-reporter@v1
        with:
          name: API Test Results
          path: results.xml
          reporter: java-junit
```

### 7.2 测试数据管理

#### 测试数据隔离策略
- 每个测试用例使用独立的数据集
- 测试完成后自动清理数据
- 使用数据库事务确保数据一致性

#### 测试环境配置
```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
logging.level.com.chaosblade=DEBUG
```

---

## 附录

### A. 常用HTTP状态码说明

- **200 OK**: 请求成功
- **400 Bad Request**: 请求参数错误
- **401 Unauthorized**: 未授权
- **403 Forbidden**: 权限不足
- **404 Not Found**: 资源不存在
- **409 Conflict**: 资源冲突
- **500 Internal Server Error**: 服务器内部错误

### B. 业务状态码说明

- **VALIDATION_ERROR**: 参数验证失败
- **SYSTEM_NOT_FOUND**: 系统不存在
- **API_NOT_FOUND**: API不存在
- **FAULT_TYPE_NOT_FOUND**: 故障类型不存在
- **FAULT_CONFIG_NOT_FOUND**: 故障配置不存在
- **DETECTION_TASK_NOT_FOUND**: 检测任务不存在
- **FAULT_EXECUTION_NOT_FOUND**: 故障执行不存在
- **INVALID_STATE_TRANSITION**: 无效的状态转换
- **CONCURRENT_MODIFICATION**: 并发修改冲突
- **TARGET_SERVICE_UNAVAILABLE**: 目标服务不可达
- **INTERNAL_ERROR**: 系统内部错误

### C. 测试数据模板

#### 系统数据模板
```json
{
  "name": "测试系统_{{timestamp}}",
  "description": "自动化测试创建的系统",
  "version": "v1.0.0",
  "status": "ACTIVE"
}
```

#### API数据模板
```json
{
  "operationId": "testApi_{{random_id}}",
  "path": "/test/{{random_id}}",
  "method": "GET",
  "summary": "测试API",
  "description": "自动化测试创建的API",
  "tags": "测试",
  "status": "ACTIVE"
}
```

#### 故障类型数据模板
```json
{
  "name": "测试故障类型_{{timestamp}}",
  "category": "测试",
  "description": "自动化测试创建的故障类型",
  "parametersSchema": "{\"type\":\"object\",\"properties\":{\"delay\":{\"type\":\"integer\"}}}",
  "severityLevel": "LOW",
  "status": "ACTIVE"
}
```

---

**注意事项**:
1. 所有时间字段格式为 `yyyy-MM-dd HH:mm:ss`
2. 分页参数 `page` 从1开始
3. JSON字段（如schema）需要转义为字符串格式
4. 测试前确保数据库连接正常
5. 建议使用事务回滚保证测试数据的清理
6. 并发测试时注意资源限制和系统负载
7. 定期更新测试用例以覆盖新功能
8. 保持测试环境与生产环境的一致性
