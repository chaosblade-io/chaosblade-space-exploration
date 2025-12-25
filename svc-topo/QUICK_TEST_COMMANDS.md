# svc-topo 快速测试命令

本文档提供了快速测试 svc-topo 服务的常用命令，可以直接复制粘贴使用。

## 🚀 前提条件

确保服务正在运行：
```bash
# 启动服务
cd svc-topo
mvn spring-boot:run

# 或者如果已经编译
java -jar target/svc-topo-*.jar
```

服务默认运行在: `http://localhost:8106`

---

## 1️⃣ 健康检查

### 基础健康检查
```bash
curl http://localhost:8106/actuator/health
```

### 详细健康信息
```bash
curl -s http://localhost:8106/actuator/health | jq .
```

---

## 2️⃣ Prometheus 指标查询

### 查询 Pod 指标
```bash
# 基础查询
curl http://localhost:8106/api/metrics/pod/default/test-pod

# 格式化输出
curl -s http://localhost:8106/api/metrics/pod/default/test-pod | jq .

# 只显示 CPU 和内存使用率
curl -s http://localhost:8106/api/metrics/pod/default/test-pod | jq '{
  podName: .podName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsagePercent,
  healthStatus: .metrics.health.status
}'
```

### 查询 Node 指标
```bash
# 基础查询
curl http://localhost:8106/api/metrics/node/worker-node-1

# 格式化输出
curl -s http://localhost:8106/api/metrics/node/worker-node-1 | jq .

# 只显示资源使用情况
curl -s http://localhost:8106/api/metrics/node/worker-node-1 | jq '{
  nodeName: .nodeName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsagePercent,
  networkReceive: .metrics.resources.networkReceiveBytesPerSec,
  networkTransmit: .metrics.resources.networkTransmitBytesPerSec
}'
```

### 查询 Container 指标
```bash
# 基础查询
curl http://localhost:8106/api/metrics/container/default/test-pod/app-container

# 格式化输出
curl -s http://localhost:8106/api/metrics/container/default/test-pod/app-container | jq .

# 只显示关键指标
curl -s http://localhost:8106/api/metrics/container/default/test-pod/app-container | jq '{
  containerName: .containerName,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsageMB,
  restartCount: .metrics.health.restartCount
}'
```

---

## 3️⃣ 拓扑可视化

### 获取完整拓扑数据
```bash
# 获取拓扑（输出较大）
curl http://localhost:8106/api/xflow/topology

# 保存到文件
curl -s http://localhost:8106/api/xflow/topology > topology.json

# 查看前100行
curl -s http://localhost:8106/api/xflow/topology | head -100

# 统计节点和边的数量
curl -s http://localhost:8106/api/xflow/topology | jq '{
  nodeCount: (.nodes | length),
  edgeCount: (.edges | length)
}'
```

### 查看拓扑中的实体类型分布
```bash
curl -s http://localhost:8106/api/xflow/topology | jq '
  .nodes | group_by(.data.entityType) | 
  map({entityType: .[0].data.entityType, count: length})
'
```

### 查看包含 Prometheus 指标的节点
```bash
curl -s http://localhost:8106/api/xflow/topology | jq '
  .nodes[] | 
  select(.data.attributes.prometheus != null) | 
  {
    id: .id,
    label: .label,
    entityType: .data.entityType,
    cpuUsage: .data.attributes.prometheus.resources.cpuUsagePercent,
    memoryUsage: .data.attributes.prometheus.resources.memoryUsageMB
  }
' | head -50
```

### 查看 NODE 类型的节点
```bash
curl -s http://localhost:8106/api/xflow/topology | jq '
  .nodes[] | 
  select(.data.entityType == "NODE") | 
  {
    id: .id,
    label: .label,
    level: .data.level,
    hasPrometheus: (.data.attributes.prometheus != null),
    hasKubernetes: (.data.attributes.kubernetes != null)
  }
'
```

### 刷新拓扑数据
```bash
# 手动触发刷新
curl -X POST http://localhost:8106/api/xflow/refresh

# 手动触发刷新（通过 auto-refresh API）
curl -X POST http://localhost:8106/api/xflow/auto-refresh/trigger
```

---

## 4️⃣ 自动刷新管理

### 查看自动刷新状态
```bash
curl -s http://localhost:8106/api/xflow/auto-refresh/status | jq .
```

### 启用自动刷新
```bash
curl -X POST http://localhost:8106/api/xflow/auto-refresh/enable
```

### 禁用自动刷新
```bash
curl -X POST http://localhost:8106/api/xflow/auto-refresh/disable
```

### 更新 Jaeger 配置
```bash
curl -X POST http://localhost:8106/api/xflow/auto-refresh/config \
  -H "Content-Type: application/json" \
  -d '{
    "host": "localhost",
    "port": 16685,
    "serviceName": "frontend",
    "operationName": "all",
    "timeRangeMinutes": 15
  }'
```

---

## 5️⃣ 缓存管理

### 查看缓存统计
```bash
curl http://localhost:8106/v1/cache/stats
```

### 按时间索引查询缓存
```bash
# 查询时间索引 5 的缓存
curl http://localhost:8106/v1/cache/time-index/5

# 查询时间索引 0 的缓存
curl http://localhost:8106/v1/cache/time-index/0
```

### 清空缓存（谨慎使用）
```bash
curl -X DELETE http://localhost:8106/v1/cache/clear
```

---

## 6️⃣ API 查询

### 查询命名空间列表
```bash
curl -s http://localhost:8106/api/namespaces | jq .
```

### 查询系统 API 列表
```bash
# 查询 systemId=1 的 API
curl -s http://localhost:8106/api/topology/1/apis | jq .

# 只显示 API 路径和方法
curl -s http://localhost:8106/api/topology/1/apis | jq '
  .data.apis[] | {
    method: .method,
    path: .path,
    operationId: .operationId
  }
'
```

### 查询根 API 列表
```bash
curl -s http://localhost:8106/api/topology/1/apis/root | jq .
```

### 获取 API 请求负载
```bash
curl -s http://localhost:8106/api/topology/api-requests | jq .
```

---

## 7️⃣ 运行测试脚本

### 运行完整 API 测试
```bash
cd svc-topo/scripts
./test_svc_topo_apis.sh
```

### 运行 Prometheus 集成测试
```bash
cd svc-topo/scripts
./test_prometheus_integration.sh
```

---

## 8️⃣ 组合测试命令

### 测试完整的数据流
```bash
# 1. 检查健康状态
echo "=== 健康检查 ==="
curl -s http://localhost:8106/actuator/health | jq .status

# 2. 触发拓扑刷新
echo -e "\n=== 触发拓扑刷新 ==="
curl -s -X POST http://localhost:8106/api/xflow/auto-refresh/trigger | jq .

# 等待刷新完成
sleep 2

# 3. 获取拓扑统计
echo -e "\n=== 拓扑统计 ==="
curl -s http://localhost:8106/api/xflow/topology | jq '{
  nodeCount: (.nodes | length),
  edgeCount: (.edges | length),
  entityTypes: (.nodes | group_by(.data.entityType) | map({type: .[0].data.entityType, count: length}))
}'

# 4. 查询 Pod 指标
echo -e "\n=== Pod 指标 ==="
curl -s http://localhost:8106/api/metrics/pod/default/test-pod | jq '{
  success: .success,
  cpuUsage: .metrics.resources.cpuUsagePercent,
  memoryUsage: .metrics.resources.memoryUsagePercent
}'

# 5. 查看缓存统计
echo -e "\n=== 缓存统计 ==="
curl -s http://localhost:8106/v1/cache/stats
```

### 监控拓扑变化
```bash
# 每5秒查询一次拓扑节点数量
watch -n 5 'curl -s http://localhost:8106/api/xflow/topology | jq "{nodeCount: (.nodes | length), edgeCount: (.edges | length)}"'
```

### 批量查询多个 Pod 的指标
```bash
# 定义 Pod 列表
PODS=("test-pod" "frontend-pod" "backend-pod")

# 循环查询
for pod in "${PODS[@]}"; do
  echo "=== Pod: $pod ==="
  curl -s "http://localhost:8106/api/metrics/pod/default/$pod" | jq '{
    podName: .podName,
    cpuUsage: .metrics.resources.cpuUsagePercent,
    memoryUsage: .metrics.resources.memoryUsagePercent,
    healthStatus: .metrics.health.status
  }'
  echo ""
done
```

---

## 9️⃣ 调试命令

### 查看详细的 HTTP 响应头
```bash
curl -v http://localhost:8106/api/xflow/topology
```

### 测试 API 响应时间
```bash
time curl -s http://localhost:8106/api/xflow/topology > /dev/null
```

### 查看应用日志（如果使用 Docker）
```bash
docker logs -f svc-topo
```

### 查看应用日志（如果使用 Kubernetes）
```bash
kubectl logs -f deployment/svc-topo -n default
```

---

## 🔟 故障排查

### 检查服务是否运行
```bash
# 检查端口是否监听
netstat -tuln | grep 8106

# 或使用 lsof
lsof -i :8106

# 或使用 ss
ss -tuln | grep 8106
```

### 测试 Prometheus 连接
```bash
# 如果在 Kubernetes 集群内
curl http://prometheus.monitoring.svc.cluster.local:9090/-/healthy

# 如果使用端口转发
kubectl port-forward -n monitoring svc/prometheus 9090:9090 &
curl http://localhost:9090/-/healthy
```

### 测试 Kubernetes API 连接
```bash
# 检查 Kubernetes API 是否可访问
kubectl cluster-info

# 查看当前的 Pods
kubectl get pods -A
```

---

## 📝 注意事项

1. **替换示例数据**
   - 命令中的 `test-pod`、`worker-node-1` 等是示例数据
   - 实际使用时需要替换为真实的 Pod/Node 名称

2. **jq 工具**
   - 大部分格式化命令需要 `jq` 工具
   - 安装：`sudo apt-get install jq` 或 `brew install jq`

3. **集群外运行**
   - 如果在 Kubernetes 集群外运行，Prometheus 和 Kubernetes API 连接可能失败
   - 这是预期行为，可以使用端口转发来测试

4. **性能考虑**
   - 拓扑数据可能很大，建议使用 `head` 或 `jq` 过滤输出
   - 频繁刷新可能影响性能

---

**快速参考**: 
- 完整文档: [API_TESTING_GUIDE.md](docs/API_TESTING_GUIDE.md)
- 测试脚本: [scripts/README.md](scripts/README.md)

**维护者**: ChaosBlade Team  
**最后更新**: 2024-11-08

