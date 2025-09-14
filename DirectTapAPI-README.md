# Direct Tap API 接口文档

## 概述

Direct Tap API 提供了直接从 Kubernetes Pod 文件系统读取 Envoy tap 数据的功能，无需依赖 Redis 存储。这些接口主要用于调试和临时访问 tap 数据。

## 接口列表

### 1. 获取活跃录制 ID 列表

**接口**: `GET /api/direct-tap/recordings/active`

**描述**: 获取所有当前运行中的录制会话 ID

**响应示例**:
```json
{
  "success": true,
  "data": [
    "rec-20250830-172228-3b4ec511",
    "rec-20250830-165057-c7d80faf"
  ],
  "message": null,
  "code": null
}
```

### 2. 获取活跃录制详细信息

**接口**: `GET /api/direct-tap/recordings/active/details`

**描述**: 获取所有当前运行中的录制会话的详细状态信息

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "recordingId": "rec-20250830-172228-3b4ec511",
      "namespace": "train-ticket",
      "serviceName": "ts-travel2-service",
      "status": "RUNNING",
      "startAt": "2025-08-30T17:22:28",
      "endAt": null,
      "rules": [
        {
          "path": "/api/v1/travel2service/trips/left",
          "method": "POST"
        }
      ],
      "errorMessage": null
    }
  ],
  "message": null,
  "code": null
}
```

### 3. 直接读取 Tap 数据

**接口**: `GET /api/direct-tap/{namespace}/{serviceName}/entries`

**描述**: 直接从指定服务的 Pod 中读取 tap 数据

**参数**:
- `namespace`: Kubernetes 命名空间
- `serviceName`: 服务名称
- `offset`: 偏移量 (可选，默认 0)
- `limit`: 限制数量 (可选，默认 50)

**示例**: `GET /api/direct-tap/train-ticket/ts-travel2-service/entries?limit=5`

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": "rec-_879716800976466585",
      "timestamp": "2025-08-30T17:48:41.120024",
      "podName": "ts-travel2-service-77ff89b86d-c66ls",
      "request": {
        "method": "POST",
        "path": "/api/v1/travel2service/trips/left",
        "headers": {
          ":authority": "ts-travel2-service:16346",
          ":method": "POST",
          "content-type": "application/json"
        },
        "body": "{\"startingPlace\":\"Shang Hai\",\"endPlace\":\"Su Zhou\",\"departureTime\":\"2025-08-30\"}"
      },
      "response": {
        "status": 200,
        "headers": {
          ":status": "200",
          "content-type": "application/json;charset=UTF-8"
        },
        "body": "{\"status\":1,\"msg\":\"Success Query\",\"data\":[]}"
      }
    }
  ],
  "message": null,
  "code": null
}
```

### 4. 获取服务 Tap 信息统计

**接口**: `GET /api/direct-tap/{namespace}/{serviceName}/info`

**描述**: 获取指定服务的 Pod 列表和 tap 文件统计信息

**示例**: `GET /api/direct-tap/train-ticket/ts-travel2-service/info`

**响应示例**:
```json
{
  "success": true,
  "data": {
    "namespace": "train-ticket",
    "serviceName": "ts-travel2-service",
    "podCount": 1,
    "totalTapFiles": 4,
    "pods": [
      {
        "podName": "ts-travel2-service-77ff89b86d-c66ls",
        "tapFileCount": 4,
        "tapFiles": [
          "/var/log/envoy/taps/rec-_879716800976466585.json",
          "/var/log/envoy/taps/rec-_15426585734503993206.json",
          "/var/log/envoy/taps/rec-_8092722716532285395.json",
          "/var/log/envoy/taps/rec-_5967679672458541055.json"
        ]
      }
    ]
  },
  "message": null,
  "code": null
}
```

## 使用场景

### 1. 调试录制问题
当原始的录制 API 返回空数据时，可以使用这些接口来：
- 检查是否有活跃的录制会话
- 直接从 Pod 文件系统读取 tap 数据
- 验证 Envoy 是否正确生成了 tap 文件

### 2. 数据恢复
当 Redis 中的数据丢失或损坏时，可以使用直接读取接口来恢复数据。

### 3. 实时监控
可以使用这些接口来实时监控 tap 数据的生成情况。

## 测试脚本

使用提供的测试脚本来验证接口功能：

```bash
chmod +x test-active-recordings-api.sh
./test-active-recordings-api.sh
```

## 注意事项

1. **性能考虑**: 直接读取接口会直接访问 Kubernetes API 和 Pod 文件系统，性能可能不如从 Redis 读取
2. **权限要求**: 需要适当的 Kubernetes RBAC 权限来访问 Pod 和执行命令
3. **数据一致性**: 直接读取的数据可能与 Redis 中的数据不完全一致
4. **调试用途**: 这些接口主要用于调试和故障排除，不建议在生产环境中频繁使用

## 错误处理

所有接口都会返回统一的错误格式：

```json
{
  "success": false,
  "data": null,
  "message": "错误描述",
  "code": "500"
}
```

常见错误：
- `500`: 服务内部错误
- `404`: 资源未找到
- `403`: 权限不足
