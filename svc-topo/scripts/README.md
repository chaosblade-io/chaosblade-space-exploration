# svc-topo 测试脚本使用指南

本目录包含用于测试 svc-topo 服务的各种脚本和工具。

## 📁 文件列表

### 测试脚本

1. **test_svc_topo_apis.sh** - 完整的 API 测试脚本
   - 测试所有 RESTful API 端点
   - 包含健康检查、Prometheus 指标、拓扑可视化、缓存管理等
   - 提供彩色输出和测试统计

2. **test_prometheus_integration.sh** - Prometheus 集成测试脚本
   - 专门测试 Prometheus 和 Kubernetes 集成功能
   - 使用 jq 工具格式化 JSON 输出
   - 提供详细的指标数据展示

### Postman Collection

3. **svc-topo-postman-collection.json** - Postman 测试集合
   - 包含所有 API 端点的 Postman 请求
   - 可直接导入 Postman 使用
   - 支持环境变量配置

## 🚀 快速开始

### 前置条件

1. **确保 svc-topo 服务正在运行**
   ```bash
   # 在 svc-topo 目录下
   cd svc-topo
   mvn spring-boot:run
   ```

2. **安装必要的工具**
   ```bash
   # 安装 jq（用于 JSON 格式化）
   # Ubuntu/Debian
   sudo apt-get install jq
   
   # macOS
   brew install jq
   
   # CentOS/RHEL
   sudo yum install jq
   ```

### 运行测试

#### 1. 运行完整 API 测试

```bash
cd svc-topo/scripts
./test_svc_topo_apis.sh
```

**输出示例**:
```
=========================================
svc-topo API 测试脚本
基础URL: http://localhost:8106
=========================================

=== 1. 健康检查 ===

[测试 1] Actuator 健康检查
请求: GET /actuator/health
✓ 成功 (HTTP 200)
响应: {"status":"UP","components":{"diskSpace":{"status":"UP"...

-----------------------------------------

总测试数: 25
通过: 23
失败: 2
```

#### 2. 运行 Prometheus 集成测试

```bash
cd svc-topo/scripts
./test_prometheus_integration.sh
```

**输出示例**:
```
=========================================
Prometheus 集成功能测试
基础URL: http://localhost:8106
=========================================

1. 测试 Pod 指标查询
-----------------------------------------
✓ Pod 指标查询成功

Pod 指标摘要:
{
  "success": true,
  "namespace": "default",
  "podName": "test-pod",
  "metrics": {
    "cpu": {
      "usagePercent": 45.5,
      "usageCores": 0.455,
      "limitCores": 1
    },
    ...
  }
}
```

#### 3. 使用 Postman

1. 打开 Postman
2. 点击 "Import"
3. 选择 `svc-topo-postman-collection.json` 文件
4. 导入后即可使用所有预定义的 API 请求

**配置环境变量**:
- 在 Postman 中创建一个环境
- 添加变量 `baseUrl`，值为 `http://localhost:8106`
- 选择该环境后即可使用

## 📋 测试覆盖范围

### 1. Prometheus 指标查询 API
- ✅ GET `/api/metrics/pod/{namespace}/{podName}` - 查询 Pod 指标
- ✅ GET `/api/metrics/node/{nodeName}` - 查询 Node 指标
- ✅ GET `/api/metrics/container/{namespace}/{podName}/{containerName}` - 查询 Container 指标

### 2. XFlow 拓扑可视化 API
- ✅ GET `/api/xflow/topology` - 获取拓扑数据
- ✅ POST `/api/xflow/refresh` - 刷新拓扑数据
- ✅ GET `/api/xflow/nodes/{nodeId}` - 获取节点详情
- ✅ POST `/api/xflow/layout` - 应用布局算法

### 3. 自动刷新管理 API
- ✅ GET `/api/xflow/auto-refresh/status` - 获取自动刷新状态
- ✅ POST `/api/xflow/auto-refresh/trigger` - 手动触发刷新
- ✅ POST `/api/xflow/auto-refresh/enable` - 启用自动刷新
- ✅ POST `/api/xflow/auto-refresh/disable` - 禁用自动刷新
- ✅ POST `/api/xflow/auto-refresh/config` - 更新 Jaeger 配置

### 4. 拓扑缓存管理 API
- ✅ GET `/v1/cache/stats` - 获取缓存统计
- ✅ GET `/v1/cache/time-index/{timeIndex}` - 按时间索引查询缓存
- ✅ DELETE `/v1/cache/clear` - 清空缓存

### 5. API 查询 API
- ✅ GET `/api/namespaces` - 查询命名空间列表
- ✅ GET `/api/topology/{systemId}/apis` - 查询系统 API 列表
- ✅ GET `/api/topology/{systemId}/apis/root` - 查询根 API 列表
- ✅ GET `/api/topology/api-requests` - 获取 API 请求负载

### 6. 健康检查 API
- ✅ GET `/actuator/health` - Spring Boot Actuator 健康检查
- ✅ GET `/actuator/info` - 应用信息

## 🔧 自定义测试

### 修改基础 URL

如果服务运行在不同的端口或主机上，可以修改脚本中的 `BASE_URL` 变量：

```bash
# 编辑脚本
vim test_svc_topo_apis.sh

# 修改第5行
BASE_URL="http://your-host:your-port"
```

### 添加新的测试用例

在 `test_svc_topo_apis.sh` 中添加新的测试：

```bash
# 在脚本末尾添加
test_api "测试名称" "HTTP方法" "/api/endpoint" '{"json": "data"}'
```

## 📊 测试结果解读

### 成功的测试
```
✓ 成功 (HTTP 200)
```
- HTTP 状态码在 200-299 范围内
- API 正常工作

### 失败的测试
```
✗ 失败 (HTTP 500)
```
- HTTP 状态码不在 200-299 范围内
- 可能的原因：
  - 服务未启动
  - 配置错误
  - 依赖服务（Prometheus/Kubernetes）不可用

### 常见错误

1. **Connection refused**
   - 服务未启动
   - 端口号错误
   - 解决：检查服务是否运行在正确的端口

2. **404 Not Found**
   - API 端点路径错误
   - 解决：检查 API 路径是否正确

3. **500 Internal Server Error**
   - 服务内部错误
   - 解决：查看应用日志了解详细错误

## 📖 相关文档

- [API 测试指南](../docs/API_TESTING_GUIDE.md) - 完整的 API 文档和测试示例
- [Prometheus 集成计划](../docs/architecture-refactoring/PROMETHEUS_INTEGRATION_PLAN.md) - Prometheus 集成设计文档

## 💡 提示

1. **在 Kubernetes 集群外运行时**
   - Prometheus 和 Kubernetes API 连接可能失败
   - 这是预期行为
   - 可以使用端口转发来测试：
     ```bash
     kubectl port-forward -n monitoring svc/prometheus 9090:9090
     ```

2. **测试数据**
   - 脚本使用示例数据（如 `test-pod`、`worker-node-1`）
   - 实际测试时需要替换为真实的 Pod/Node 名称

3. **性能测试**
   - 这些脚本主要用于功能测试
   - 如需性能测试，建议使用 JMeter 或 Gatling

## 🤝 贡献

如果您发现问题或有改进建议，请：
1. 提交 Issue
2. 创建 Pull Request
3. 联系维护团队

---

**维护者**: ChaosBlade Team  
**最后更新**: 2024-11-08

