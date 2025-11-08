# svc-topo 架构改造快速开始指南

## 概述

本指南提供了 svc-topo 项目架构改造的快速开始步骤，帮助开发团队快速理解改造内容并开始实施。

完整的技术方案请参考：[PROMETHEUS_INTEGRATION_PLAN.md](./PROMETHEUS_INTEGRATION_PLAN.md)

---

## 改造目标

将 svc-topo 从单一的 Jaeger trace 数据源扩展为支持以下能力：

1. ✅ **Kubernetes 集成** - 获取 Pod、Node、Container 元数据
2. ✅ **Prometheus 集成** - 采集资源指标（CPU、内存、网络、磁盘）
3. ✅ **拓扑扩展** - 支持物理机、Pod、Container 节点展示
4. ✅ **多数据源编排** - 整合 Jaeger + Kubernetes + Prometheus

---

## 核心改动概览

### 1. 新增实体类型（EntityType）

```java
// 0级实体（集群层）
CLUSTER("Cluster")

// 2级实体（运行时实例层）- 重命名和新增
NODE("Node")           // 原 HOST，重命名为 NODE
CONTAINER("Container") // 新增

// 4级实体（指标层）- 新增
METRIC("Metric")
METRIC_GROUP("MetricGroup")
```

### 2. 新增关系类型（RelationType）

```java
BELONGS_TO("BELONGS_TO")   // 集群归属关系
HOSTS("HOSTS")             // Node 托管 Pod
INCLUDES("INCLUDES")       // Pod 包含 Container
EXPOSES("EXPOSES")         // 实体暴露指标
AGGREGATES("AGGREGATES")   // 指标聚合关系
```

### 3. 新增数据模型类

**Prometheus 指标模型**：
- `PrometheusMetrics` - 指标数据容器
- `ResourceMetrics` - 资源指标（CPU、内存、网络、磁盘）
- `PerformanceMetrics` - 性能指标（请求、延迟、错误）
- `HealthMetrics` - 健康状态指标

**Kubernetes 元数据模型**：
- `KubernetesMetadata` - K8s 资源元数据

### 4. 新增服务类

**数据采集服务**：
- `PrometheusQueryService` - Prometheus 指标查询
- `KubernetesQueryService` - Kubernetes API 查询
- `DataSourceOrchestrator` - 多数据源编排

**数据转换服务**：
- `MetricsConverterService` - 指标数据转换和附加

### 5. 新增 API 接口

```
GET /api/metrics/pod/{namespace}/{podName}
GET /api/metrics/node/{nodeName}
GET /api/metrics/container/{namespace}/{podName}/{containerName}
GET /api/metrics/namespace/{namespace}/pods
```

---

## 快速开始

### 步骤 1：添加依赖

编辑 `pom.xml`，添加 Kubernetes 客户端依赖：

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-client</artifactId>
    <version>6.0.0</version>
</dependency>
```

### 步骤 2：更新配置

编辑 `src/main/resources/application.yml`，添加配置：

```yaml
# Kubernetes 配置
kubernetes:
  enabled: true
  api-url: https://kubernetes.default.svc
  token: ${KUBERNETES_TOKEN:}
  verify-ssl: false

# Prometheus 配置
prometheus:
  enabled: true
  host: ${PROMETHEUS_HOST:localhost}
  port: ${PROMETHEUS_PORT:9090}
  query-timeout: 30
  cache:
    enabled: true
    ttl-seconds: 30
    max-size: 1000
```

### 步骤 3：创建数据模型类

按照以下顺序创建类：

1. **指标模型** (`src/main/java/com/chaosblade/svc/topo/model/metrics/`)
   - `PrometheusMetrics.java`
   - `ResourceMetrics.java`
   - `PerformanceMetrics.java`
   - `HealthMetrics.java`

2. **Kubernetes 模型** (`src/main/java/com/chaosblade/svc/topo/model/k8s/`)
   - `KubernetesMetadata.java`

### 步骤 4：扩展现有枚举

修改以下文件：

1. `EntityType.java` - 添加新的实体类型
2. `RelationType.java` - 添加新的关系类型

### 步骤 5：扩展 NodeAttributes

修改 `Node.java`，在 `NodeAttributes` 类中添加：

```java
@JsonProperty("prometheus")
private PrometheusMetrics prometheus;

@JsonProperty("kubernetes")
private KubernetesMetadata kubernetes;
```

### 步骤 6：实现服务类

按照以下顺序实现服务：

1. `PrometheusQueryService.java` - Prometheus 查询
2. `KubernetesQueryService.java` - Kubernetes 查询
3. `MetricsConverterService.java` - 指标转换
4. `DataSourceOrchestrator.java` - 数据源编排

### 步骤 7：实现 API 控制器

创建 `PrometheusMetricsController.java`，提供 REST API。

### 步骤 8：测试

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package -DskipTests

# 运行
java -jar target/svc-topo-1.0.0.jar
```

---

## 关键 Prometheus 指标

### Pod 级别指标

**CPU**：
```promql
container_cpu_usage_seconds_total{pod="<pod_name>", namespace="<namespace>"}
container_spec_cpu_quota{pod="<pod_name>", namespace="<namespace>"}
```

**内存**：
```promql
container_memory_usage_bytes{pod="<pod_name>", namespace="<namespace>"}
container_spec_memory_limit_bytes{pod="<pod_name>", namespace="<namespace>"}
```

**网络**：
```promql
container_network_receive_bytes_total{pod="<pod_name>", namespace="<namespace>"}
container_network_transmit_bytes_total{pod="<pod_name>", namespace="<namespace>"}
```

### Node 级别指标

**CPU**：
```promql
100 - (avg(irate(node_cpu_seconds_total{mode="idle",instance=~"<node>.*"}[5m])) * 100)
```

**内存**：
```promql
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
```

**磁盘**：
```promql
(1 - (node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})) * 100
```

---

## 实施阶段

### 阶段 1：基础架构准备（1-2 周）
- 扩展数据模型
- 创建指标和元数据类

### 阶段 2：Prometheus 集成（2-3 周）
- 实现 PrometheusQueryService
- 实现 API 接口
- 实现缓存机制

### 阶段 3：Kubernetes 集成（2-3 周）
- 实现 KubernetesQueryService
- 查询 Pod、Node 元数据

### 阶段 4：数据源编排（2 周）
- 实现 DataSourceOrchestrator
- 整合三个数据源

### 阶段 5：前端可视化（2-3 周）
- 更新前端渲染逻辑
- 展示新节点类型和指标

### 阶段 6：测试和优化（1-2 周）
- 单元测试和集成测试
- 性能优化

**总计**：10-15 周

---

## 向后兼容性

✅ **保证向后兼容**：
- 新增字段使用 `@JsonInclude(JsonInclude.Include.NON_NULL)`
- 现有 API 接口保持不变
- Kubernetes 和 Prometheus 集成默认禁用
- 通过配置开关控制功能启用

---

## 性能目标

| 指标 | 目标 |
|------|------|
| Prometheus 单次查询 | < 500ms |
| Kubernetes API 查询 | < 300ms |
| 完整拓扑构建 | < 5s |
| 前端渲染 1000 节点 | < 2s |

---

## 常见问题

### Q1: 如何配置 Kubernetes 访问权限？

在 Kubernetes 集群中创建 ServiceAccount 和 ClusterRole：

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: svc-topo
  namespace: default
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: svc-topo-reader
rules:
- apiGroups: [""]
  resources: ["pods", "nodes"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: svc-topo-reader-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: svc-topo-reader
subjects:
- kind: ServiceAccount
  name: svc-topo
  namespace: default
```

### Q2: Prometheus 查询性能如何优化？

1. 使用查询缓存（TTL 30 秒）
2. 批量查询优化（一次查询多个 Pod）
3. 使用 PromQL 聚合函数减少数据量
4. 异步查询，避免阻塞主线程

### Q3: 如何处理大规模集群（1000+ Pod）？

1. 实现分页查询
2. 使用 LRU 缓存淘汰策略
3. 前端虚拟化渲染
4. 节点聚合和按需加载

---

## 参考资料

- [完整技术方案](./PROMETHEUS_INTEGRATION_PLAN.md)
- [Prometheus 查询文档](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Kubernetes API 文档](https://kubernetes.io/docs/reference/kubernetes-api/)
- [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client)

---

## 联系方式

如有问题，请联系架构团队或提交 Issue。

