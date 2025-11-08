# svc-topo 架构改造与 Prometheus 集成方案

## 文档版本
- **版本**: v1.0
- **日期**: 2025-11-08
- **作者**: Architecture Team

---

## 目录
1. [当前系统分析](#1-当前系统分析)
2. [拓扑结构重新设计](#2-拓扑结构重新设计)
3. [数据模型扩展](#3-数据模型扩展)
4. [Prometheus 集成方案](#4-prometheus-集成方案)
5. [数据源集成](#5-数据源集成)
6. [实施计划](#6-实施计划)

---

## 1. 当前系统分析

### 1.1 现有架构概览

**技术栈**：
- 后端：Spring Boot 2.7.18 + Java 1.8
- 图处理：JGraphT 1.4.0
- 数据源：Jaeger (OpenTelemetry Trace)
- 前端：React 18 + XFlow

**现有三级实体模型**：
```
1级实体（抽象服务层）
├── NAMESPACE - 命名空间
├── SERVICE - 服务/应用
├── EXTERNAL_SERVICE - 外部服务
└── MIDDLEWARE - 中间件

2级实体（运行时实例层）
├── POD - 应用实例（已定义但未实现）
├── INSTANCE - 中间件实例
└── HOST - 主机/节点（已定义但未实现）

3级实体（接口调用层）
├── RPC - 远程过程调用
└── RPC_GROUP - RPC分组
```

**现有关系类型**：
- `CONTAINS` - 包含关系（如 Namespace → Service）
- `DEPENDS_ON` - 依赖关系（如 Service → Service）
- `RUNS_ON` - 运行关系（如 Pod → Host）
- `INVOKES` - 调用关系（如 Service → RPC）

### 1.2 当前系统限制

#### 1.2.1 数据源限制
- ✅ **已实现**: Jaeger Trace 数据采集（仅服务调用链路）
- ❌ **缺失**: Kubernetes API 集成（Pod、Node、Container 元数据）
- ❌ **缺失**: Prometheus 集成（资源指标、性能指标）
- ❌ **缺失**: 容器运行时数据（Container 级别信息）

#### 1.2.2 实体实现缺失
虽然代码中定义了 `POD` 和 `HOST` 实体类型，但实际没有：
- Pod 实例的创建和管理逻辑
- Host/Node 节点的数据采集
- Container 容器的表示（完全缺失）
- Pod-Node、Container-Pod 关系的建立

#### 1.2.3 指标数据限制
- ✅ **已实现**: RED 指标（Rate, Errors, Duration）- 仅从 Trace 计算
- ❌ **缺失**: 资源指标（CPU、内存、网络、磁盘）
- ❌ **缺失**: 容器级别指标
- ❌ **缺失**: 节点级别指标
- ❌ **缺失**: 自定义业务指标

---

## 2. 拓扑结构重新设计

### 2.1 新的四级实体模型

为了支持 Kubernetes 环境和 Prometheus 监控，我们将现有的三级模型扩展为**四级模型**：

```
0级实体（集群层）- 新增
└── CLUSTER - Kubernetes 集群

1级实体（抽象服务层）
├── NAMESPACE - 命名空间
├── SERVICE - 服务/应用
├── EXTERNAL_SERVICE - 外部服务
└── MIDDLEWARE - 中间件

2级实体（运行时实例层）- 扩展
├── NODE - Kubernetes 节点（原 HOST，重命名）
├── POD - Pod 实例（完善实现）
├── CONTAINER - 容器实例（新增）
└── INSTANCE - 中间件实例

3级实体（接口调用层）
├── RPC - 远程过程调用
└── RPC_GROUP - RPC分组

4级实体（指标层）- 新增
├── METRIC - Prometheus 指标
└── METRIC_GROUP - 指标分组
```

### 2.2 新增实体类型定义

#### 2.2.1 CLUSTER（集群实体）
```java
/**
 * Kubernetes 集群，代表一个完整的 K8s 集群
 * 用途：多集群环境下的顶层组织单元
 */
CLUSTER("Cluster")
```

**属性**：
- `clusterId`: 集群唯一标识
- `clusterName`: 集群名称
- `version`: Kubernetes 版本
- `provider`: 云服务提供商（AWS、GCP、阿里云等）
- `region`: 地域
- `nodeCount`: 节点数量
- `podCount`: Pod 总数

#### 2.2.2 NODE（节点实体）
```java
/**
 * Kubernetes 节点，代表一个物理机或虚拟机
 * 原 HOST 实体，重命名为 NODE 以更好地反映 K8s 概念
 */
NODE("Node")
```

**属性**：
- `nodeId`: 节点唯一标识（k8s.node.name）
- `nodeName`: 节点名称
- `nodeIP`: 节点 IP 地址
- `nodeType`: 节点类型（master、worker）
- `instanceType`: 实例类型（如 t3.medium）
- `osImage`: 操作系统镜像
- `kernelVersion`: 内核版本
- `kubeletVersion`: Kubelet 版本
- `capacity`: 节点容量（CPU、内存、Pod 数量）
- `allocatable`: 可分配资源
- `conditions`: 节点状态（Ready、MemoryPressure、DiskPressure 等）

#### 2.2.3 CONTAINER（容器实体）
```java
/**
 * 容器实例，代表 Pod 中的一个容器
 * 新增实体类型
 */
CONTAINER("Container")
```

**属性**：
- `containerId`: 容器唯一标识
- `containerName`: 容器名称
- `image`: 容器镜像
- `imageTag`: 镜像标签
- `restartCount`: 重启次数
- `state`: 容器状态（Running、Waiting、Terminated）
- `ports`: 暴露的端口列表
- `resources`: 资源请求和限制

#### 2.2.4 METRIC（指标实体）
```java
/**
 * Prometheus 指标，代表一个具体的监控指标
 * 新增实体类型
 */
METRIC("Metric")
```

**属性**：
- `metricId`: 指标唯一标识
- `metricName`: 指标名称（如 container_cpu_usage_seconds_total）
- `metricType`: 指标类型（Counter、Gauge、Histogram、Summary）
- `labels`: 标签集合
- `value`: 当前值
- `timestamp`: 时间戳

#### 2.2.5 METRIC_GROUP（指标分组实体）
```java
/**
 * 指标分组，将相关指标聚合在一起
 * 新增实体类型
 */
METRIC_GROUP("MetricGroup")
```

**属性**：
- `groupId`: 分组唯一标识
- `groupName`: 分组名称（如 "CPU Metrics"、"Memory Metrics"）
- `category`: 分类（Resource、Performance、Business）
- `metricCount`: 包含的指标数量

### 2.3 新增关系类型

#### 2.3.1 BELONGS_TO（归属关系）
```java
/**
 * 归属关系 - 实体归属于某个集群或命名空间
 * 例如：Node → Cluster, Namespace → Cluster
 */
BELONGS_TO("BELONGS_TO", "belongs_to")
```

#### 2.3.2 HOSTS（托管关系）
```java
/**
 * 托管关系 - 节点托管 Pod
 * 例如：Node → Pod（一个节点托管多个 Pod）
 */
HOSTS("HOSTS", "hosts")
```

#### 2.3.3 INCLUDES（包含关系 - 容器级别）
```java
/**
 * 包含关系 - Pod 包含 Container
 * 例如：Pod → Container（一个 Pod 包含多个容器）
 */
INCLUDES("INCLUDES", "includes")
```

#### 2.3.4 EXPOSES（暴露关系）
```java
/**
 * 暴露关系 - 实体暴露指标
 * 例如：Pod → Metric, Node → Metric, Container → Metric
 */
EXPOSES("EXPOSES", "exposes")
```

#### 2.3.5 AGGREGATES（聚合关系）
```java
/**
 * 聚合关系 - 指标分组聚合指标
 * 例如：MetricGroup → Metric
 */
AGGREGATES("AGGREGATES", "aggregates")
```

### 2.4 完整的拓扑层次结构

```
Cluster (0级)
  ├─ BELONGS_TO → Namespace (1级)
  │    └─ CONTAINS → Service (1级)
  │         ├─ CONTAINS → Pod (2级)
  │         │    ├─ INCLUDES → Container (2级)
  │         │    │    └─ EXPOSES → Metric (4级)
  │         │    ├─ EXPOSES → Metric (4级)
  │         │    └─ INVOKES → RPC (3级)
  │         └─ DEPENDS_ON → Service/ExternalService/Middleware (1级)
  │
  └─ BELONGS_TO → Node (2级)
       ├─ HOSTS → Pod (2级)
       └─ EXPOSES → Metric (4级)

MetricGroup (4级)
  └─ AGGREGATES → Metric (4级)
```

---

## 3. 数据模型扩展

### 3.1 EntityType 枚举扩展

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/EntityType.java`

```java
public enum EntityType {
    
    // ========== 0级实体（集群层） ==========
    
    /**
     * Kubernetes 集群
     */
    CLUSTER("Cluster"),
    
    // ========== 1级实体（抽象服务实体） ==========
    
    NAMESPACE("Namespace"),
    SERVICE("Service"),
    EXTERNAL_SERVICE("ExternalService"),
    MIDDLEWARE("Middleware"),
    
    // ========== 2级实体（运行时实例实体） ==========
    
    /**
     * Kubernetes 节点（原 HOST，重命名）
     */
    NODE("Node"),
    
    /**
     * Pod 实例
     */
    POD("Pod"),
    
    /**
     * 容器实例（新增）
     */
    CONTAINER("Container"),
    
    /**
     * 中间件实例
     */
    INSTANCE("Instance"),
    
    // ========== 3级实体（接口与调用实体） ==========
    
    RPC("RPC"),
    RPC_GROUP("RPCGroup"),
    
    // ========== 4级实体（指标层） ==========
    
    /**
     * Prometheus 指标（新增）
     */
    METRIC("Metric"),
    
    /**
     * 指标分组（新增）
     */
    METRIC_GROUP("MetricGroup");
    
    // ... 其他方法保持不变
    
    /**
     * 获取实体级别
     * @return 0, 1, 2, 3, 或 4
     */
    public int getLevel() {
        switch (this) {
            case CLUSTER:
                return 0;
            case NAMESPACE:
            case SERVICE:
            case EXTERNAL_SERVICE:
            case MIDDLEWARE:
                return 1;
            case NODE:
            case POD:
            case CONTAINER:
            case INSTANCE:
                return 2;
            case RPC:
            case RPC_GROUP:
                return 3;
            case METRIC:
            case METRIC_GROUP:
                return 4;
            default:
                return -1;
        }
    }
    
    /**
     * 判断是否为基础设施实体
     */
    public boolean isInfrastructure() {
        return this == CLUSTER || this == NODE || this == POD || this == CONTAINER;
    }
    
    /**
     * 判断是否为指标实体
     */
    public boolean isMetric() {
        return this == METRIC || this == METRIC_GROUP;
    }
}
```

### 3.2 RelationType 枚举扩展

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/RelationType.java`

```java
public enum RelationType {
    
    // 现有关系类型
    CONTAINS("CONTAINS", "contains"),
    DEPENDS_ON("DEPENDS_ON", "depends_on"),
    RUNS_ON("RUNS_ON", "runs_on"),
    INVOKES("INVOKES", "invokes"),
    
    // 新增关系类型
    
    /**
     * 归属关系
     */
    BELONGS_TO("BELONGS_TO", "belongs_to"),
    
    /**
     * 托管关系
     */
    HOSTS("HOSTS", "hosts"),
    
    /**
     * 包含关系（容器级别）
     */
    INCLUDES("INCLUDES", "includes"),
    
    /**
     * 暴露关系
     */
    EXPOSES("EXPOSES", "exposes"),
    
    /**
     * 聚合关系
     */
    AGGREGATES("AGGREGATES", "aggregates");
    
    // ... 其他方法

    /**
     * 判断是否为基础设施关系
     */
    public boolean isInfrastructure() {
        return this == BELONGS_TO || this == HOSTS || this == INCLUDES;
    }

    /**
     * 判断是否为指标关系
     */
    public boolean isMetricRelation() {
        return this == EXPOSES || this == AGGREGATES;
    }
}
```

### 3.3 新增数据模型类

#### 3.3.1 PrometheusMetrics（Prometheus 指标数据）

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/model/metrics/PrometheusMetrics.java`

```java
package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Prometheus 指标数据模型
 * 用于存储从 Prometheus 查询的指标数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrometheusMetrics {

    /**
     * 资源使用指标
     */
    @JsonProperty("resources")
    private ResourceMetrics resources;

    /**
     * 性能指标
     */
    @JsonProperty("performance")
    private PerformanceMetrics performance;

    /**
     * 健康状态指标
     */
    @JsonProperty("health")
    private HealthMetrics health;

    /**
     * 自定义业务指标
     */
    @JsonProperty("custom")
    private Map<String, Object> custom;

    /**
     * 指标采集时间戳
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    public PrometheusMetrics() {
        this.custom = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public ResourceMetrics getResources() { return resources; }
    public void setResources(ResourceMetrics resources) { this.resources = resources; }

    public PerformanceMetrics getPerformance() { return performance; }
    public void setPerformance(PerformanceMetrics performance) { this.performance = performance; }

    public HealthMetrics getHealth() { return health; }
    public void setHealth(HealthMetrics health) { this.health = health; }

    public Map<String, Object> getCustom() { return custom; }
    public void setCustom(Map<String, Object> custom) { this.custom = custom; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public void addCustomMetric(String key, Object value) {
        if (this.custom == null) {
            this.custom = new HashMap<>();
        }
        this.custom.put(key, value);
    }
}
```

#### 3.3.2 ResourceMetrics（资源指标）

```java
package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 资源使用指标
 * 包括 CPU、内存、网络、磁盘等资源的使用情况
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceMetrics {

    // CPU 指标
    @JsonProperty("cpuUsagePercent")
    private Double cpuUsagePercent;

    @JsonProperty("cpuUsageCores")
    private Double cpuUsageCores;

    @JsonProperty("cpuThrottlingPercent")
    private Double cpuThrottlingPercent;

    // 内存指标
    @JsonProperty("memoryUsageBytes")
    private Long memoryUsageBytes;

    @JsonProperty("memoryUsagePercent")
    private Double memoryUsagePercent;

    @JsonProperty("memoryWorkingSetBytes")
    private Long memoryWorkingSetBytes;

    @JsonProperty("memoryRssBytes")
    private Long memoryRssBytes;

    @JsonProperty("memoryCacheBytes")
    private Long memoryCacheBytes;

    // 网络指标
    @JsonProperty("networkReceiveBytesPerSec")
    private Double networkReceiveBytesPerSec;

    @JsonProperty("networkTransmitBytesPerSec")
    private Double networkTransmitBytesPerSec;

    @JsonProperty("networkReceivePacketsPerSec")
    private Double networkReceivePacketsPerSec;

    @JsonProperty("networkTransmitPacketsPerSec")
    private Double networkTransmitPacketsPerSec;

    @JsonProperty("networkErrorsPerSec")
    private Double networkErrorsPerSec;

    // 磁盘指标
    @JsonProperty("diskUsageBytes")
    private Long diskUsageBytes;

    @JsonProperty("diskUsagePercent")
    private Double diskUsagePercent;

    @JsonProperty("diskReadBytesPerSec")
    private Double diskReadBytesPerSec;

    @JsonProperty("diskWriteBytesPerSec")
    private Double diskWriteBytesPerSec;

    @JsonProperty("diskIoTimePercent")
    private Double diskIoTimePercent;

    // 资源配额
    @JsonProperty("quota")
    private ResourceQuota quota;

    // Getters and Setters
    // ... (省略，按需生成)

    /**
     * 资源配额
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceQuota {
        @JsonProperty("cpu")
        private String cpu; // 如 "1000m" 或 "2"

        @JsonProperty("memory")
        private String memory; // 如 "1Gi" 或 "512Mi"

        @JsonProperty("storage")
        private String storage; // 如 "10Gi"

        // Getters and Setters
        public String getCpu() { return cpu; }
        public void setCpu(String cpu) { this.cpu = cpu; }

        public String getMemory() { return memory; }
        public void setMemory(String memory) { this.memory = memory; }

        public String getStorage() { return storage; }
        public void setStorage(String storage) { this.storage = storage; }
    }
}
```

#### 3.3.3 PerformanceMetrics（性能指标）

```java
package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 性能指标
 * 包括请求处理、延迟、吞吐量等性能相关指标
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceMetrics {

    // 请求指标
    @JsonProperty("requestsPerSecond")
    private Double requestsPerSecond;

    @JsonProperty("requestsTotal")
    private Long requestsTotal;

    @JsonProperty("requestDurationP50")
    private Double requestDurationP50;

    @JsonProperty("requestDurationP95")
    private Double requestDurationP95;

    @JsonProperty("requestDurationP99")
    private Double requestDurationP99;

    // 错误指标
    @JsonProperty("errorRate")
    private Double errorRate;

    @JsonProperty("errorsTotal")
    private Long errorsTotal;

    @JsonProperty("errors4xxTotal")
    private Long errors4xxTotal;

    @JsonProperty("errors5xxTotal")
    private Long errors5xxTotal;

    // 并发指标
    @JsonProperty("concurrentConnections")
    private Integer concurrentConnections;

    @JsonProperty("activeRequests")
    private Integer activeRequests;

    // 队列指标
    @JsonProperty("queueDepth")
    private Integer queueDepth;

    @JsonProperty("queueLatency")
    private Double queueLatency;

    // Getters and Setters
    // ... (省略，按需生成)
}
```

#### 3.3.4 HealthMetrics（健康状态指标）

```java
package com.chaosblade.svc.topo.model.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康状态指标
 * 包括健康检查、就绪状态、存活状态等
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthMetrics {

    /**
     * 整体健康状态
     */
    @JsonProperty("status")
    private HealthStatus status;

    /**
     * 健康检查结果
     */
    @JsonProperty("checks")
    private Map<String, Boolean> checks;

    /**
     * 重启次数
     */
    @JsonProperty("restartCount")
    private Integer restartCount;

    /**
     * 运行时长（秒）
     */
    @JsonProperty("uptimeSeconds")
    private Long uptimeSeconds;

    /**
     * 最后重启时间
     */
    @JsonProperty("lastRestartTime")
    private Long lastRestartTime;

    public HealthMetrics() {
        this.checks = new HashMap<>();
        this.status = HealthStatus.UNKNOWN;
    }

    // Getters and Setters
    public HealthStatus getStatus() { return status; }
    public void setStatus(HealthStatus status) { this.status = status; }

    public Map<String, Boolean> getChecks() { return checks; }
    public void setChecks(Map<String, Boolean> checks) { this.checks = checks; }

    public Integer getRestartCount() { return restartCount; }
    public void setRestartCount(Integer restartCount) { this.restartCount = restartCount; }

    public Long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(Long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }

    public Long getLastRestartTime() { return lastRestartTime; }
    public void setLastRestartTime(Long lastRestartTime) { this.lastRestartTime = lastRestartTime; }

    public void addCheck(String checkName, boolean passed) {
        if (this.checks == null) {
            this.checks = new HashMap<>();
        }
        this.checks.put(checkName, passed);
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("healthy"),
        DEGRADED("degraded"),
        UNHEALTHY("unhealthy"),
        UNKNOWN("unknown");

        private final String value;

        HealthStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
```

### 3.4 扩展 NodeAttributes

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/Node.java`

在 `NodeAttributes` 类中添加 Prometheus 指标字段：

```java
public static class NodeAttributes {

    /**
     * RED指标数据（现有）
     */
    @JsonProperty("RED")
    private RedMetrics red;

    /**
     * Prometheus 指标数据（新增）
     */
    @JsonProperty("prometheus")
    private PrometheusMetrics prometheus;

    /**
     * Kubernetes 元数据（新增）
     */
    @JsonProperty("kubernetes")
    private KubernetesMetadata kubernetes;

    /**
     * 扩展属性（现有）
     */
    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    // Getters and Setters
    public PrometheusMetrics getPrometheus() { return prometheus; }
    public void setPrometheus(PrometheusMetrics prometheus) { this.prometheus = prometheus; }

    public KubernetesMetadata getKubernetes() { return kubernetes; }
    public void setKubernetes(KubernetesMetadata kubernetes) { this.kubernetes = kubernetes; }

    // ... 其他方法
}
```

#### 3.4.1 KubernetesMetadata（Kubernetes 元数据）

```java
package com.chaosblade.svc.topo.model.k8s;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 元数据
 * 存储从 K8s API 获取的资源元数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KubernetesMetadata {

    /**
     * 资源类型（Pod、Node、Service 等）
     */
    @JsonProperty("kind")
    private String kind;

    /**
     * API 版本
     */
    @JsonProperty("apiVersion")
    private String apiVersion;

    /**
     * 命名空间
     */
    @JsonProperty("namespace")
    private String namespace;

    /**
     * 资源名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * UID
     */
    @JsonProperty("uid")
    private String uid;

    /**
     * 标签
     */
    @JsonProperty("labels")
    private Map<String, String> labels;

    /**
     * 注解
     */
    @JsonProperty("annotations")
    private Map<String, String> annotations;

    /**
     * 所有者引用
     */
    @JsonProperty("ownerReferences")
    private List<OwnerReference> ownerReferences;

    /**
     * 创建时间
     */
    @JsonProperty("creationTimestamp")
    private String creationTimestamp;

    /**
     * 资源版本
     */
    @JsonProperty("resourceVersion")
    private String resourceVersion;

    public KubernetesMetadata() {
        this.labels = new HashMap<>();
        this.annotations = new HashMap<>();
    }

    // Getters and Setters
    // ... (省略，按需生成)

    /**
     * 所有者引用
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OwnerReference {
        @JsonProperty("kind")
        private String kind;

        @JsonProperty("name")
        private String name;

        @JsonProperty("uid")
        private String uid;

        @JsonProperty("controller")
        private Boolean controller;

        // Getters and Setters
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUid() { return uid; }
        public void setUid(String uid) { this.uid = uid; }

        public Boolean getController() { return controller; }
        public void setController(Boolean controller) { this.controller = controller; }
    }
}
```

---

## 4. Prometheus 集成方案

### 4.1 Prometheus 数据采集架构

```
┌─────────────────────────────────────────────────────────────┐
│                    svc-topo Application                      │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐ │
│  │          PrometheusQueryService                        │ │
│  │  - 查询 Prometheus 指标                                 │ │
│  │  - 支持 PromQL 查询                                     │ │
│  │  - 批量查询优化                                         │ │
│  └────────────────┬───────────────────────────────────────┘ │
│                   │                                           │
│  ┌────────────────▼───────────────────────────────────────┐ │
│  │       MetricsConverterService                          │ │
│  │  - 将 Prometheus 数据转换为拓扑指标                     │ │
│  │  - 数据聚合和计算                                       │ │
│  └────────────────┬───────────────────────────────────────┘ │
│                   │                                           │
│  ┌────────────────▼───────────────────────────────────────┐ │
│  │          MetricsCacheService                           │ │
│  │  - 指标数据缓存                                         │ │
│  │  - TTL 管理                                             │ │
│  └────────────────┬───────────────────────────────────────┘ │
│                   │                                           │
│  ┌────────────────▼───────────────────────────────────────┐ │
│  │       TopologyEnricherService                          │ │
│  │  - 将指标数据附加到拓扑节点                             │ │
│  │  - 节点属性更新                                         │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ HTTP API
                           ▼
                  ┌─────────────────┐
                  │   Prometheus    │
                  │     Server      │
                  └─────────────────┘
```

### 4.2 需要采集的 Prometheus 指标

#### 4.2.1 Pod 级别指标

**CPU 指标**：
```promql
# CPU 使用率（核心数）
container_cpu_usage_seconds_total{pod="<pod_name>", namespace="<namespace>"}

# CPU 限制
container_spec_cpu_quota{pod="<pod_name>", namespace="<namespace>"}

# CPU 节流
container_cpu_cfs_throttled_seconds_total{pod="<pod_name>", namespace="<namespace>"}
```

**内存指标**：
```promql
# 内存使用量（字节）
container_memory_usage_bytes{pod="<pod_name>", namespace="<namespace>"}

# 内存工作集
container_memory_working_set_bytes{pod="<pod_name>", namespace="<namespace>"}

# 内存限制
container_spec_memory_limit_bytes{pod="<pod_name>", namespace="<namespace>"}

# 内存 RSS
container_memory_rss{pod="<pod_name>", namespace="<namespace>"}

# 内存缓存
container_memory_cache{pod="<pod_name>", namespace="<namespace>"}
```

**网络指标**：
```promql
# 网络接收字节数
container_network_receive_bytes_total{pod="<pod_name>", namespace="<namespace>"}

# 网络发送字节数
container_network_transmit_bytes_total{pod="<pod_name>", namespace="<namespace>"}

# 网络接收包数
container_network_receive_packets_total{pod="<pod_name>", namespace="<namespace>"}

# 网络发送包数
container_network_transmit_packets_total{pod="<pod_name>", namespace="<namespace>"}

# 网络错误
container_network_receive_errors_total{pod="<pod_name>", namespace="<namespace>"}
container_network_transmit_errors_total{pod="<pod_name>", namespace="<namespace>"}
```

**磁盘指标**：
```promql
# 文件系统使用量
container_fs_usage_bytes{pod="<pod_name>", namespace="<namespace>"}

# 文件系统限制
container_fs_limit_bytes{pod="<pod_name>", namespace="<namespace>"}

# 磁盘读取字节数
container_fs_reads_bytes_total{pod="<pod_name>", namespace="<namespace>"}

# 磁盘写入字节数
container_fs_writes_bytes_total{pod="<pod_name>", namespace="<namespace>"}
```

**健康状态指标**：
```promql
# Pod 重启次数
kube_pod_container_status_restarts_total{pod="<pod_name>", namespace="<namespace>"}

# Pod 状态
kube_pod_status_phase{pod="<pod_name>", namespace="<namespace>"}

# 容器就绪状态
kube_pod_container_status_ready{pod="<pod_name>", namespace="<namespace>"}
```

#### 4.2.2 Node 级别指标

**CPU 指标**：
```promql
# 节点 CPU 使用率
node_cpu_seconds_total{instance="<node_name>"}

# 节点 CPU 容量
kube_node_status_capacity_cpu_cores{node="<node_name>"}

# 节点 CPU 可分配
kube_node_status_allocatable_cpu_cores{node="<node_name>"}
```

**内存指标**：
```promql
# 节点内存使用
node_memory_MemTotal_bytes{instance="<node_name>"}
node_memory_MemAvailable_bytes{instance="<node_name>"}

# 节点内存容量
kube_node_status_capacity_memory_bytes{node="<node_name>"}

# 节点内存可分配
kube_node_status_allocatable_memory_bytes{node="<node_name>"}
```

**磁盘指标**：
```promql
# 节点磁盘使用
node_filesystem_size_bytes{instance="<node_name>"}
node_filesystem_avail_bytes{instance="<node_name>"}

# 磁盘 I/O
node_disk_read_bytes_total{instance="<node_name>"}
node_disk_written_bytes_total{instance="<node_name>"}
```

**网络指标**：
```promql
# 节点网络流量
node_network_receive_bytes_total{instance="<node_name>"}
node_network_transmit_bytes_total{instance="<node_name>"}
```

**节点状态指标**：
```promql
# 节点状态
kube_node_status_condition{node="<node_name>", condition="Ready"}

# 节点信息
kube_node_info{node="<node_name>"}
```

#### 4.2.3 Container 级别指标

**资源使用指标**：
```promql
# 容器 CPU 使用
container_cpu_usage_seconds_total{container="<container_name>", pod="<pod_name>"}

# 容器内存使用
container_memory_usage_bytes{container="<container_name>", pod="<pod_name>"}

# 容器网络流量
container_network_receive_bytes_total{container="<container_name>", pod="<pod_name>"}
container_network_transmit_bytes_total{container="<container_name>", pod="<pod_name>"}
```

**容器状态指标**：
```promql
# 容器重启次数
kube_pod_container_status_restarts_total{container="<container_name>", pod="<pod_name>"}

# 容器状态
kube_pod_container_status_running{container="<container_name>", pod="<pod_name>"}
```

### 4.3 指标查询优化策略

#### 4.3.1 批量查询
使用 Prometheus 的向量查询一次性获取多个实体的指标：

```promql
# 查询命名空间下所有 Pod 的 CPU 使用率
sum(rate(container_cpu_usage_seconds_total{namespace="<namespace>"}[5m])) by (pod)

# 查询所有节点的内存使用率
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
```

#### 4.3.2 时间范围优化
- 实时数据：使用即时查询（instant query）
- 历史趋势：使用范围查询（range query）
- 默认时间窗口：5 分钟（可配置）

#### 4.3.3 缓存策略
- 指标数据缓存 TTL：30 秒（可配置）
- 使用 LRU 缓存淘汰策略
- 支持手动刷新缓存

### 4.4 服务实现

#### 4.4.1 PrometheusQueryService

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/service/PrometheusQueryService.java`

```java
package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Prometheus 查询服务
 * 负责从 Prometheus 查询指标数据
 */
@Service
public class PrometheusQueryService {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusQueryService.class);

    @Value("${prometheus.host:localhost}")
    private String prometheusHost;

    @Value("${prometheus.port:9090}")
    private int prometheusPort;

    @Value("${prometheus.query-timeout:30}")
    private int queryTimeout;

    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public PrometheusQueryService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * 查询 Pod 的 Prometheus 指标
     */
    public PrometheusMetrics queryPodMetrics(String namespace, String podName) {
        logger.info("Querying Prometheus metrics for pod: {}/{}", namespace, podName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询资源指标
            ResourceMetrics resourceMetrics = queryPodResourceMetrics(namespace, podName);
            metrics.setResources(resourceMetrics);

            // 查询性能指标
            PerformanceMetrics performanceMetrics = queryPodPerformanceMetrics(namespace, podName);
            metrics.setPerformance(performanceMetrics);

            // 查询健康状态
            HealthMetrics healthMetrics = queryPodHealthMetrics(namespace, podName);
            metrics.setHealth(healthMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for pod: {}/{}", namespace, podName, e);
        }

        return metrics;
    }

    /**
     * 查询 Node 的 Prometheus 指标
     */
    public PrometheusMetrics queryNodeMetrics(String nodeName) {
        logger.info("Querying Prometheus metrics for node: {}", nodeName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询节点资源指标
            ResourceMetrics resourceMetrics = queryNodeResourceMetrics(nodeName);
            metrics.setResources(resourceMetrics);

            // 查询节点健康状态
            HealthMetrics healthMetrics = queryNodeHealthMetrics(nodeName);
            metrics.setHealth(healthMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for node: {}", nodeName, e);
        }

        return metrics;
    }

    /**
     * 查询 Container 的 Prometheus 指标
     */
    public PrometheusMetrics queryContainerMetrics(String namespace, String podName, String containerName) {
        logger.info("Querying Prometheus metrics for container: {}/{}/{}", namespace, podName, containerName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询容器资源指标
            ResourceMetrics resourceMetrics = queryContainerResourceMetrics(namespace, podName, containerName);
            metrics.setResources(resourceMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for container: {}/{}/{}",
                namespace, podName, containerName, e);
        }

        return metrics;
    }

    /**
     * 查询 Pod 资源指标
     */
    private ResourceMetrics queryPodResourceMetrics(String namespace, String podName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // CPU 使用率
        String cpuQuery = String.format(
            "sum(rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=\"%s\",container!=\"\"}[5m]))",
            namespace, podName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        metrics.setCpuUsageCores(cpuUsage);

        // CPU 限制
        String cpuLimitQuery = String.format(
            "sum(container_spec_cpu_quota{namespace=\"%s\",pod=\"%s\"}/100000)",
            namespace, podName
        );
        Double cpuLimit = executeQuery(cpuLimitQuery);
        if (cpuLimit != null && cpuLimit > 0) {
            metrics.setCpuUsagePercent((cpuUsage / cpuLimit) * 100);
        }

        // 内存使用
        String memoryQuery = String.format(
            "sum(container_memory_usage_bytes{namespace=\"%s\",pod=\"%s\",container!=\"\"})",
            namespace, podName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        metrics.setMemoryUsageBytes(memoryUsage != null ? memoryUsage.longValue() : 0L);

        // 内存限制
        String memoryLimitQuery = String.format(
            "sum(container_spec_memory_limit_bytes{namespace=\"%s\",pod=\"%s\"})",
            namespace, podName
        );
        Double memoryLimit = executeQuery(memoryLimitQuery);
        if (memoryLimit != null && memoryLimit > 0) {
            metrics.setMemoryUsagePercent((memoryUsage / memoryLimit) * 100);
        }

        // 网络接收速率
        String networkRxQuery = String.format(
            "sum(rate(container_network_receive_bytes_total{namespace=\"%s\",pod=\"%s\"}[5m]))",
            namespace, podName
        );
        Double networkRx = executeQuery(networkRxQuery);
        metrics.setNetworkReceiveBytesPerSec(networkRx);

        // 网络发送速率
        String networkTxQuery = String.format(
            "sum(rate(container_network_transmit_bytes_total{namespace=\"%s\",pod=\"%s\"}[5m]))",
            namespace, podName
        );
        Double networkTx = executeQuery(networkTxQuery);
        metrics.setNetworkTransmitBytesPerSec(networkTx);

        return metrics;
    }

    /**
     * 查询 Pod 性能指标
     */
    private PerformanceMetrics queryPodPerformanceMetrics(String namespace, String podName) throws Exception {
        PerformanceMetrics metrics = new PerformanceMetrics();

        // 这里可以查询应用级别的指标，如果应用暴露了 Prometheus 指标
        // 例如：http_requests_total, http_request_duration_seconds 等

        return metrics;
    }

    /**
     * 查询 Pod 健康状态
     */
    private HealthMetrics queryPodHealthMetrics(String namespace, String podName) throws Exception {
        HealthMetrics metrics = new HealthMetrics();

        // 重启次数
        String restartQuery = String.format(
            "sum(kube_pod_container_status_restarts_total{namespace=\"%s\",pod=\"%s\"})",
            namespace, podName
        );
        Double restartCount = executeQuery(restartQuery);
        metrics.setRestartCount(restartCount != null ? restartCount.intValue() : 0);

        // Pod 状态
        String statusQuery = String.format(
            "kube_pod_status_phase{namespace=\"%s\",pod=\"%s\",phase=\"Running\"}",
            namespace, podName
        );
        Double isRunning = executeQuery(statusQuery);

        if (isRunning != null && isRunning > 0) {
            metrics.setStatus(HealthMetrics.HealthStatus.HEALTHY);
        } else {
            metrics.setStatus(HealthMetrics.HealthStatus.UNHEALTHY);
        }

        return metrics;
    }

    /**
     * 查询 Node 资源指标
     */
    private ResourceMetrics queryNodeResourceMetrics(String nodeName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // 节点 CPU 使用率
        String cpuQuery = String.format(
            "100 - (avg(irate(node_cpu_seconds_total{mode=\"idle\",instance=~\"%s.*\"}[5m])) * 100)",
            nodeName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        metrics.setCpuUsagePercent(cpuUsage);

        // 节点内存使用率
        String memoryQuery = String.format(
            "(1 - (node_memory_MemAvailable_bytes{instance=~\"%s.*\"} / node_memory_MemTotal_bytes{instance=~\"%s.*\"})) * 100",
            nodeName, nodeName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        metrics.setMemoryUsagePercent(memoryUsage);

        // 节点磁盘使用率
        String diskQuery = String.format(
            "(1 - (node_filesystem_avail_bytes{instance=~\"%s.*\",mountpoint=\"/\"} / node_filesystem_size_bytes{instance=~\"%s.*\",mountpoint=\"/\"})) * 100",
            nodeName, nodeName
        );
        Double diskUsage = executeQuery(diskQuery);
        metrics.setDiskUsagePercent(diskUsage);

        return metrics;
    }

    /**
     * 查询 Node 健康状态
     */
    private HealthMetrics queryNodeHealthMetrics(String nodeName) throws Exception {
        HealthMetrics metrics = new HealthMetrics();

        // 节点就绪状态
        String readyQuery = String.format(
            "kube_node_status_condition{node=\"%s\",condition=\"Ready\",status=\"true\"}",
            nodeName
        );
        Double isReady = executeQuery(readyQuery);

        if (isReady != null && isReady > 0) {
            metrics.setStatus(HealthMetrics.HealthStatus.HEALTHY);
            metrics.addCheck("ready", true);
        } else {
            metrics.setStatus(HealthMetrics.HealthStatus.UNHEALTHY);
            metrics.addCheck("ready", false);
        }

        return metrics;
    }

    /**
     * 查询 Container 资源指标
     */
    private ResourceMetrics queryContainerResourceMetrics(String namespace, String podName, String containerName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // 容器 CPU 使用
        String cpuQuery = String.format(
            "rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=\"%s\",container=\"%s\"}[5m])",
            namespace, podName, containerName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        metrics.setCpuUsageCores(cpuUsage);

        // 容器内存使用
        String memoryQuery = String.format(
            "container_memory_usage_bytes{namespace=\"%s\",pod=\"%s\",container=\"%s\"}",
            namespace, podName, containerName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        metrics.setMemoryUsageBytes(memoryUsage != null ? memoryUsage.longValue() : 0L);

        return metrics;
    }

    /**
     * 执行 Prometheus 查询
     */
    private Double executeQuery(String query) throws Exception {
        String url = String.format("http://%s:%d/api/v1/query?query=%s",
            prometheusHost, prometheusPort, java.net.URLEncoder.encode(query, "UTF-8"));

        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                logger.warn("Prometheus query failed with status code: {}", statusCode);
                return null;
            }

            String content = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(content);

            JsonNode result = root.path("data").path("result");
            if (result.isArray() && result.size() > 0) {
                JsonNode value = result.get(0).path("value");
                if (value.isArray() && value.size() > 1) {
                    return value.get(1).asDouble();
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Failed to execute Prometheus query: {}", query, e);
            throw e;
        }
    }

    /**
     * 批量查询命名空间下所有 Pod 的指标
     */
    public Map<String, PrometheusMetrics> queryNamespacePodMetrics(String namespace) {
        logger.info("Querying Prometheus metrics for all pods in namespace: {}", namespace);

        Map<String, PrometheusMetrics> metricsMap = new HashMap<>();

        try {
            // 首先获取命名空间下所有 Pod 的列表
            List<String> podNames = getPodNamesInNamespace(namespace);

            // 批量查询每个 Pod 的指标
            for (String podName : podNames) {
                PrometheusMetrics metrics = queryPodMetrics(namespace, podName);
                metricsMap.put(podName, metrics);
            }

        } catch (Exception e) {
            logger.error("Failed to query namespace pod metrics: {}", namespace, e);
        }

        return metricsMap;
    }

    /**
     * 获取命名空间下所有 Pod 名称
     */
    private List<String> getPodNamesInNamespace(String namespace) throws Exception {
        List<String> podNames = new ArrayList<>();

        String query = String.format("kube_pod_info{namespace=\"%s\"}", namespace);
        String url = String.format("http://%s:%d/api/v1/query?query=%s",
            prometheusHost, prometheusPort, java.net.URLEncoder.encode(query, "UTF-8"));

        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            String content = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(content);

            JsonNode result = root.path("data").path("result");
            if (result.isArray()) {
                for (JsonNode item : result) {
                    String podName = item.path("metric").path("pod").asText();
                    if (!podName.isEmpty()) {
                        podNames.add(podName);
                    }
                }
            }
        }

        return podNames;
    }
}
```

#### 4.4.2 MetricsConverterService

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/service/MetricsConverterService.java`

```java
package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 指标转换服务
 * 将 Prometheus 指标数据转换并附加到拓扑节点
 */
@Service
public class MetricsConverterService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsConverterService.class);

    /**
     * 将 Prometheus 指标附加到节点
     */
    public void attachMetricsToNode(Node node, PrometheusMetrics metrics) {
        if (node == null || metrics == null) {
            return;
        }

        logger.debug("Attaching Prometheus metrics to node: {}", node.getNodeId());

        // 获取或创建节点属性
        Node.NodeAttributes attrs = node.getAttrs();
        if (attrs == null) {
            attrs = new Node.NodeAttributes();
            node.setAttrs(attrs);
        }

        // 设置 Prometheus 指标
        attrs.setPrometheus(metrics);

        // 可以在这里进行额外的数据转换或聚合
        // 例如：将资源使用率转换为健康评分
        calculateHealthScore(node, metrics);
    }

    /**
     * 计算节点健康评分
     */
    private void calculateHealthScore(Node node, PrometheusMetrics metrics) {
        if (metrics.getResources() == null) {
            return;
        }

        double healthScore = 100.0;

        // 根据 CPU 使用率扣分
        Double cpuUsage = metrics.getResources().getCpuUsagePercent();
        if (cpuUsage != null) {
            if (cpuUsage > 90) {
                healthScore -= 30;
            } else if (cpuUsage > 70) {
                healthScore -= 15;
            }
        }

        // 根据内存使用率扣分
        Double memoryUsage = metrics.getResources().getMemoryUsagePercent();
        if (memoryUsage != null) {
            if (memoryUsage > 90) {
                healthScore -= 30;
            } else if (memoryUsage > 70) {
                healthScore -= 15;
            }
        }

        // 根据重启次数扣分
        if (metrics.getHealth() != null && metrics.getHealth().getRestartCount() != null) {
            int restarts = metrics.getHealth().getRestartCount();
            if (restarts > 10) {
                healthScore -= 20;
            } else if (restarts > 5) {
                healthScore -= 10;
            }
        }

        // 将健康评分存储到扩展属性
        node.getAttrs().addExtension("healthScore", Math.max(0, healthScore));
    }
}
```

### 4.5 新增 API 接口

#### 4.5.1 PrometheusMetricsController

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/controller/PrometheusMetricsController.java`

```java
package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import com.chaosblade.svc.topo.service.PrometheusQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Prometheus 指标查询 API 控制器
 */
@RestController
@RequestMapping("/api/metrics")
public class PrometheusMetricsController {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsController.class);

    @Autowired
    private PrometheusQueryService prometheusQueryService;

    /**
     * 查询 Pod 指标
     * GET /api/metrics/pod/{namespace}/{podName}
     */
    @GetMapping("/pod/{namespace}/{podName}")
    public ResponseEntity<Map<String, Object>> getPodMetrics(
            @PathVariable String namespace,
            @PathVariable String podName) {

        logger.info("API request: Get Pod metrics for {}/{}", namespace, podName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryPodMetrics(namespace, podName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("podName", podName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Pod metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Pod metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询 Node 指标
     * GET /api/metrics/node/{nodeName}
     */
    @GetMapping("/node/{nodeName}")
    public ResponseEntity<Map<String, Object>> getNodeMetrics(@PathVariable String nodeName) {

        logger.info("API request: Get Node metrics for {}", nodeName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryNodeMetrics(nodeName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodeName", nodeName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Node metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Node metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询 Container 指标
     * GET /api/metrics/container/{namespace}/{podName}/{containerName}
     */
    @GetMapping("/container/{namespace}/{podName}/{containerName}")
    public ResponseEntity<Map<String, Object>> getContainerMetrics(
            @PathVariable String namespace,
            @PathVariable String podName,
            @PathVariable String containerName) {

        logger.info("API request: Get Container metrics for {}/{}/{}", namespace, podName, containerName);

        try {
            PrometheusMetrics metrics = prometheusQueryService.queryContainerMetrics(
                namespace, podName, containerName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("podName", podName);
            response.put("containerName", containerName);
            response.put("metrics", metrics);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get Container metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query Container metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 批量查询命名空间下所有 Pod 的指标
     * GET /api/metrics/namespace/{namespace}/pods
     */
    @GetMapping("/namespace/{namespace}/pods")
    public ResponseEntity<Map<String, Object>> getNamespacePodMetrics(@PathVariable String namespace) {

        logger.info("API request: Get all Pod metrics in namespace {}", namespace);

        try {
            Map<String, PrometheusMetrics> metricsMap =
                prometheusQueryService.queryNamespacePodMetrics(namespace);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("podCount", metricsMap.size());
            response.put("metrics", metricsMap);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get namespace Pod metrics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to query namespace Pod metrics");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
```

---

## 5. 数据源集成

### 5.1 数据源架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      svc-topo Application                        │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              DataSourceOrchestrator                        │ │
│  │  - 协调多个数据源的数据采集                                 │ │
│  │  - 数据合并和一致性保证                                     │ │
│  └────────┬──────────────┬──────────────┬─────────────────────┘ │
│           │              │              │                         │
│  ┌────────▼────┐  ┌──────▼──────┐  ┌───▼──────────┐            │
│  │   Jaeger    │  │ Kubernetes  │  │ Prometheus   │            │
│  │   Service   │  │   Service   │  │   Service    │            │
│  └─────────────┘  └─────────────┘  └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘
         │                  │                  │
         │                  │                  │
         ▼                  ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐
   │  Jaeger  │      │   K8s    │      │Prometheus│
   │  Server  │      │   API    │      │  Server  │
   └──────────┘      └──────────┘      └──────────┘
```

### 5.2 Kubernetes API 集成

#### 5.2.1 KubernetesQueryService

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/service/KubernetesQueryService.java`

```java
package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.k8s.KubernetesMetadata;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kubernetes API 查询服务
 * 负责从 Kubernetes API 获取资源元数据
 */
@Service
public class KubernetesQueryService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesQueryService.class);

    @Value("${kubernetes.api-url:https://kubernetes.default.svc}")
    private String kubernetesApiUrl;

    @Value("${kubernetes.token:}")
    private String kubernetesToken;

    @Value("${kubernetes.verify-ssl:false}")
    private boolean verifySSL;

    @Value("${kubernetes.enabled:false}")
    private boolean enabled;

    private KubernetesClient client;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Kubernetes integration is disabled");
            return;
        }

        try {
            logger.info("Initializing Kubernetes client with API URL: {}", kubernetesApiUrl);

            Config config = new ConfigBuilder()
                    .withMasterUrl(kubernetesApiUrl)
                    .withOauthToken(kubernetesToken)
                    .withTrustCerts(!verifySSL)
                    .withConnectionTimeout(30000)
                    .withRequestTimeout(60000)
                    .build();

            this.client = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();

            // 测试连接
            String version = client.getKubernetesVersion().getGitVersion();
            logger.info("Successfully connected to Kubernetes cluster, version: {}", version);

        } catch (Exception e) {
            logger.error("Failed to initialize Kubernetes client", e);
            this.enabled = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 查询 Pod 元数据
     */
    public KubernetesMetadata queryPodMetadata(String namespace, String podName) {
        if (!enabled || client == null) {
            logger.warn("Kubernetes client is not available");
            return null;
        }

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                logger.warn("Pod not found: {}/{}", namespace, podName);
                return null;
            }

            return convertPodToMetadata(pod);

        } catch (Exception e) {
            logger.error("Failed to query Pod metadata: {}/{}", namespace, podName, e);
            return null;
        }
    }

    /**
     * 查询 Node 元数据
     */
    public KubernetesMetadata queryNodeMetadata(String nodeName) {
        if (!enabled || client == null) {
            logger.warn("Kubernetes client is not available");
            return null;
        }

        try {
            io.fabric8.kubernetes.api.model.Node node = client.nodes().withName(nodeName).get();
            if (node == null) {
                logger.warn("Node not found: {}", nodeName);
                return null;
            }

            return convertNodeToMetadata(node);

        } catch (Exception e) {
            logger.error("Failed to query Node metadata: {}", nodeName, e);
            return null;
        }
    }

    /**
     * 查询命名空间下所有 Pod
     */
    public List<Pod> listPodsInNamespace(String namespace) {
        if (!enabled || client == null) {
            return Collections.emptyList();
        }

        try {
            return client.pods().inNamespace(namespace).list().getItems();
        } catch (Exception e) {
            logger.error("Failed to list Pods in namespace: {}", namespace, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询所有 Node
     */
    public List<io.fabric8.kubernetes.api.model.Node> listNodes() {
        if (!enabled || client == null) {
            return Collections.emptyList();
        }

        try {
            return client.nodes().list().getItems();
        } catch (Exception e) {
            logger.error("Failed to list Nodes", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询 Pod 所在的 Node
     */
    public String getPodNodeName(String namespace, String podName) {
        if (!enabled || client == null) {
            return null;
        }

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod != null && pod.getSpec() != null) {
                return pod.getSpec().getNodeName();
            }
        } catch (Exception e) {
            logger.error("Failed to get Pod node name: {}/{}", namespace, podName, e);
        }

        return null;
    }

    /**
     * 查询 Pod 的容器列表
     */
    public List<String> getPodContainerNames(String namespace, String podName) {
        if (!enabled || client == null) {
            return Collections.emptyList();
        }

        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod != null && pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                return pod.getSpec().getContainers().stream()
                        .map(Container::getName)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to get Pod container names: {}/{}", namespace, podName, e);
        }

        return Collections.emptyList();
    }

    /**
     * 转换 Pod 为 KubernetesMetadata
     */
    private KubernetesMetadata convertPodToMetadata(Pod pod) {
        KubernetesMetadata metadata = new KubernetesMetadata();

        ObjectMeta meta = pod.getMetadata();
        if (meta != null) {
            metadata.setKind("Pod");
            metadata.setApiVersion(pod.getApiVersion());
            metadata.setNamespace(meta.getNamespace());
            metadata.setName(meta.getName());
            metadata.setUid(meta.getUid());
            metadata.setCreationTimestamp(meta.getCreationTimestamp());
            metadata.setResourceVersion(meta.getResourceVersion());

            if (meta.getLabels() != null) {
                metadata.setLabels(meta.getLabels());
            }

            if (meta.getAnnotations() != null) {
                metadata.setAnnotations(meta.getAnnotations());
            }

            if (meta.getOwnerReferences() != null) {
                List<KubernetesMetadata.OwnerReference> ownerRefs = meta.getOwnerReferences().stream()
                        .map(this::convertOwnerReference)
                        .collect(Collectors.toList());
                metadata.setOwnerReferences(ownerRefs);
            }
        }

        return metadata;
    }

    /**
     * 转换 Node 为 KubernetesMetadata
     */
    private KubernetesMetadata convertNodeToMetadata(io.fabric8.kubernetes.api.model.Node node) {
        KubernetesMetadata metadata = new KubernetesMetadata();

        ObjectMeta meta = node.getMetadata();
        if (meta != null) {
            metadata.setKind("Node");
            metadata.setApiVersion(node.getApiVersion());
            metadata.setName(meta.getName());
            metadata.setUid(meta.getUid());
            metadata.setCreationTimestamp(meta.getCreationTimestamp());
            metadata.setResourceVersion(meta.getResourceVersion());

            if (meta.getLabels() != null) {
                metadata.setLabels(meta.getLabels());
            }

            if (meta.getAnnotations() != null) {
                metadata.setAnnotations(meta.getAnnotations());
            }
        }

        return metadata;
    }

    /**
     * 转换 OwnerReference
     */
    private KubernetesMetadata.OwnerReference convertOwnerReference(OwnerReference ref) {
        KubernetesMetadata.OwnerReference ownerRef = new KubernetesMetadata.OwnerReference();
        ownerRef.setKind(ref.getKind());
        ownerRef.setName(ref.getName());
        ownerRef.setUid(ref.getUid());
        ownerRef.setController(ref.getController());
        return ownerRef;
    }
}
```

### 5.3 数据源编排服务

#### 5.3.1 DataSourceOrchestrator

**文件**: `svc-topo/src/main/java/com/chaosblade/svc/topo/service/DataSourceOrchestrator.java`

```java
package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.k8s.KubernetesMetadata;
import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据源编排服务
 * 协调 Jaeger、Kubernetes、Prometheus 三个数据源的数据采集和合并
 */
@Service
public class DataSourceOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceOrchestrator.class);

    @Autowired
    private JaegerQueryService jaegerQueryService;

    @Autowired
    private KubernetesQueryService kubernetesQueryService;

    @Autowired
    private PrometheusQueryService prometheusQueryService;

    @Autowired
    private MetricsConverterService metricsConverterService;

    /**
     * 构建完整的拓扑图（包含所有数据源的数据）
     */
    public TopologyGraph buildEnrichedTopology(String namespace, String serviceName) {
        logger.info("Building enriched topology for service: {}/{}", namespace, serviceName);

        // 1. 从 Jaeger 获取基础拓扑（服务调用关系）
        TopologyGraph topology = jaegerQueryService.queryTopology(namespace, serviceName);

        // 2. 从 Kubernetes 获取 Pod 和 Node 信息
        enrichWithKubernetesData(topology, namespace);

        // 3. 从 Prometheus 获取指标数据
        enrichWithPrometheusMetrics(topology, namespace);

        logger.info("Enriched topology built successfully. Nodes: {}, Edges: {}",
            topology.getNodeCount(), topology.getEdgeCount());

        return topology;
    }

    /**
     * 使用 Kubernetes 数据丰富拓扑
     */
    private void enrichWithKubernetesData(TopologyGraph topology, String namespace) {
        logger.info("Enriching topology with Kubernetes data");

        try {
            // 获取命名空间下所有 Pod
            List<io.fabric8.kubernetes.api.model.Pod> pods =
                kubernetesQueryService.listPodsInNamespace(namespace);

            for (io.fabric8.kubernetes.api.model.Pod k8sPod : pods) {
                String podName = k8sPod.getMetadata().getName();

                // 创建 Pod 节点
                Node podNode = createPodNode(k8sPod);
                topology.addNode(podNode);

                // 获取 Pod 所在的 Node
                String nodeName = k8sPod.getSpec().getNodeName();
                if (nodeName != null) {
                    // 创建或获取 Node 节点
                    Node nodeNode = topology.getNode("node-" + nodeName);
                    if (nodeNode == null) {
                        nodeNode = createNodeNode(nodeName);
                        topology.addNode(nodeNode);
                    }

                    // 创建 Node → Pod 的 HOSTS 关系
                    Edge hostsEdge = new Edge(
                        "hosts-" + nodeName + "-" + podName,
                        nodeNode.getNodeId(),
                        podNode.getNodeId(),
                        RelationType.HOSTS
                    );
                    topology.addEdge(hostsEdge);
                }

                // 创建 Pod 的容器节点
                if (k8sPod.getSpec().getContainers() != null) {
                    for (io.fabric8.kubernetes.api.model.Container container : k8sPod.getSpec().getContainers()) {
                        Node containerNode = createContainerNode(k8sPod, container);
                        topology.addNode(containerNode);

                        // 创建 Pod → Container 的 INCLUDES 关系
                        Edge includesEdge = new Edge(
                            "includes-" + podName + "-" + container.getName(),
                            podNode.getNodeId(),
                            containerNode.getNodeId(),
                            RelationType.INCLUDES
                        );
                        topology.addEdge(includesEdge);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to enrich topology with Kubernetes data", e);
        }
    }

    /**
     * 使用 Prometheus 指标丰富拓扑
     */
    private void enrichWithPrometheusMetrics(TopologyGraph topology, String namespace) {
        logger.info("Enriching topology with Prometheus metrics");

        try {
            // 为所有 Pod 节点添加指标
            for (Node node : topology.getNodes()) {
                if (node.getEntity() != null && node.getEntity().getType() == EntityType.POD) {
                    String podName = node.getEntity().getName();

                    // 查询 Pod 指标
                    PrometheusMetrics metrics = prometheusQueryService.queryPodMetrics(namespace, podName);

                    // 附加指标到节点
                    metricsConverterService.attachMetricsToNode(node, metrics);
                }

                // 为 Node 节点添加指标
                if (node.getEntity() != null && node.getEntity().getType() == EntityType.NODE) {
                    String nodeName = node.getEntity().getName();

                    // 查询 Node 指标
                    PrometheusMetrics metrics = prometheusQueryService.queryNodeMetrics(nodeName);

                    // 附加指标到节点
                    metricsConverterService.attachMetricsToNode(node, metrics);
                }

                // 为 Container 节点添加指标
                if (node.getEntity() != null && node.getEntity().getType() == EntityType.CONTAINER) {
                    // 从节点 ID 中提取信息
                    String[] parts = node.getNodeId().split("-");
                    if (parts.length >= 3) {
                        String podName = parts[1];
                        String containerName = parts[2];

                        // 查询 Container 指标
                        PrometheusMetrics metrics = prometheusQueryService.queryContainerMetrics(
                            namespace, podName, containerName);

                        // 附加指标到节点
                        metricsConverterService.attachMetricsToNode(node, metrics);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to enrich topology with Prometheus metrics", e);
        }
    }

    /**
     * 创建 Pod 节点
     */
    private Node createPodNode(io.fabric8.kubernetes.api.model.Pod k8sPod) {
        String podName = k8sPod.getMetadata().getName();
        String namespace = k8sPod.getMetadata().getNamespace();

        Entity entity = new Entity();
        entity.setEntityId("pod-" + namespace + "-" + podName);
        entity.setType(EntityType.POD);
        entity.setName(podName);
        entity.setDisplayName(podName);
        entity.setNamespace(namespace);

        Node node = new Node("pod-" + podName, entity);

        // 添加 Kubernetes 元数据
        KubernetesMetadata k8sMetadata = kubernetesQueryService.queryPodMetadata(namespace, podName);
        if (k8sMetadata != null) {
            node.getAttrs().setKubernetes(k8sMetadata);
        }

        return node;
    }

    /**
     * 创建 Node 节点
     */
    private Node createNodeNode(String nodeName) {
        Entity entity = new Entity();
        entity.setEntityId("node-" + nodeName);
        entity.setType(EntityType.NODE);
        entity.setName(nodeName);
        entity.setDisplayName(nodeName);

        Node node = new Node("node-" + nodeName, entity);

        // 添加 Kubernetes 元数据
        KubernetesMetadata k8sMetadata = kubernetesQueryService.queryNodeMetadata(nodeName);
        if (k8sMetadata != null) {
            node.getAttrs().setKubernetes(k8sMetadata);
        }

        return node;
    }

    /**
     * 创建 Container 节点
     */
    private Node createContainerNode(io.fabric8.kubernetes.api.model.Pod pod,
                                     io.fabric8.kubernetes.api.model.Container container) {
        String podName = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();
        String containerName = container.getName();

        Entity entity = new Entity();
        entity.setEntityId("container-" + namespace + "-" + podName + "-" + containerName);
        entity.setType(EntityType.CONTAINER);
        entity.setName(containerName);
        entity.setDisplayName(containerName);
        entity.setNamespace(namespace);

        Node node = new Node("container-" + podName + "-" + containerName, entity);

        // 添加容器镜像信息到扩展属性
        node.getAttrs().addExtension("image", container.getImage());

        return node;
    }
}
```

### 5.4 配置文件更新

**文件**: `svc-topo/src/main/resources/application.yml`

添加 Kubernetes 和 Prometheus 配置：

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

---

## 6. 实施计划

### 6.1 分阶段实施

#### 阶段 1：基础架构准备（1-2 周）

**目标**：完成数据模型扩展和基础服务框架

**任务**：
1. ✅ 扩展 `EntityType` 枚举（添加 CLUSTER、NODE、CONTAINER、METRIC、METRIC_GROUP）
2. ✅ 扩展 `RelationType` 枚举（添加 BELONGS_TO、HOSTS、INCLUDES、EXPOSES、AGGREGATES）
3. ✅ 创建 Prometheus 指标数据模型类
   - `PrometheusMetrics.java`
   - `ResourceMetrics.java`
   - `PerformanceMetrics.java`
   - `HealthMetrics.java`
4. ✅ 创建 Kubernetes 元数据模型类
   - `KubernetesMetadata.java`
5. ✅ 扩展 `NodeAttributes` 类（添加 prometheus 和 kubernetes 字段）

**修改文件清单**：
- `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/EntityType.java`
- `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/RelationType.java`
- `svc-topo/src/main/java/com/chaosblade/svc/topo/model/entity/Node.java`
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/model/metrics/` 目录下的所有类
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/model/k8s/KubernetesMetadata.java`

**验收标准**：
- 所有新增的枚举和类编译通过
- 单元测试覆盖率 > 80%
- 代码审查通过

#### 阶段 2：Prometheus 集成（2-3 周）

**目标**：实现 Prometheus 数据采集和查询功能

**任务**：
1. ✅ 实现 `PrometheusQueryService`
   - 支持 Pod、Node、Container 指标查询
   - 支持批量查询优化
   - 实现查询缓存机制
2. ✅ 实现 `MetricsConverterService`
   - 指标数据转换
   - 健康评分计算
3. ✅ 实现 `PrometheusMetricsController`
   - 提供 REST API 接口
4. ✅ 配置文件更新
   - 添加 Prometheus 连接配置

**修改文件清单**：
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/service/PrometheusQueryService.java`
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/service/MetricsConverterService.java`
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/controller/PrometheusMetricsController.java`
- 修改：`svc-topo/src/main/resources/application.yml`

**验收标准**：
- 能够成功查询 Prometheus 指标
- API 接口返回正确的指标数据
- 缓存机制正常工作
- 性能测试：单次查询 < 500ms，批量查询 < 2s

#### 阶段 3：Kubernetes 集成（2-3 周）

**目标**：实现 Kubernetes API 集成，获取 Pod、Node、Container 元数据

**任务**：
1. ✅ 实现 `KubernetesQueryService`
   - 查询 Pod 元数据
   - 查询 Node 元数据
   - 查询 Pod-Node 关系
   - 查询 Container 列表
2. ✅ 添加 Fabric8 Kubernetes Client 依赖
3. ✅ 配置 Kubernetes 连接参数

**修改文件清单**：
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/service/KubernetesQueryService.java`
- 修改：`svc-topo/pom.xml`（添加 fabric8 依赖）
- 修改：`svc-topo/src/main/resources/application.yml`

**依赖添加**：
```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-client</artifactId>
    <version>6.0.0</version>
</dependency>
```

**验收标准**：
- 能够成功连接 Kubernetes API
- 能够查询 Pod、Node 元数据
- 能够正确识别 Pod-Node 关系
- 错误处理机制完善

#### 阶段 4：数据源编排（2 周）

**目标**：整合 Jaeger、Kubernetes、Prometheus 三个数据源

**任务**：
1. ✅ 实现 `DataSourceOrchestrator`
   - 协调多数据源查询
   - 数据合并逻辑
   - 拓扑图丰富
2. ✅ 更新 `TopologyGraph` 构建逻辑
   - 支持 Pod、Node、Container 节点
   - 支持新的关系类型
3. ✅ 实现拓扑缓存优化

**修改文件清单**：
- 新建：`svc-topo/src/main/java/com/chaosblade/svc/topo/service/DataSourceOrchestrator.java`
- 修改：`svc-topo/src/main/java/com/chaosblade/svc/topo/service/TraceParserService.java`
- 修改：`svc-topo/src/main/java/com/chaosblade/svc/topo/controller/XFlowController.java`

**验收标准**：
- 拓扑图包含 Pod、Node、Container 节点
- 节点包含 Kubernetes 元数据和 Prometheus 指标
- 数据一致性保证
- 性能测试：完整拓扑构建 < 5s

#### 阶段 5：前端可视化（2-3 周）

**目标**：在前端展示 Pod、Node、Container 节点和 Prometheus 指标

**任务**：
1. 更新前端拓扑渲染逻辑
   - 支持新的节点类型渲染
   - 支持新的边类型渲染
2. 实现指标数据展示
   - 节点悬浮提示显示指标
   - 指标面板展示详细数据
3. 实现节点过滤和搜索
   - 按节点类型过滤
   - 按指标阈值过滤

**修改文件清单**：
- `svc-topo/frontend/src/components/TopologyGraph.jsx`
- `svc-topo/frontend/src/components/NodeRenderer.jsx`
- `svc-topo/frontend/src/components/MetricsPanel.jsx`

**验收标准**：
- 前端能够正确渲染所有节点类型
- 指标数据展示清晰
- 交互流畅，无性能问题

#### 阶段 6：测试和优化（1-2 周）

**目标**：全面测试和性能优化

**任务**：
1. 单元测试
   - 所有新增服务的单元测试
   - 测试覆盖率 > 80%
2. 集成测试
   - 端到端测试
   - 多数据源集成测试
3. 性能优化
   - 查询性能优化
   - 缓存策略优化
   - 并发处理优化
4. 文档完善
   - API 文档
   - 部署文档
   - 用户手册

**验收标准**：
- 所有测试通过
- 性能指标达标
- 文档完整

### 6.2 向后兼容性

**兼容性策略**：
1. **数据模型兼容**：
   - 新增字段使用 `@JsonInclude(JsonInclude.Include.NON_NULL)`
   - 保持现有字段不变
   - 旧版本客户端可以忽略新字段

2. **API 兼容**：
   - 现有 API 接口保持不变
   - 新增 API 使用新的路径（如 `/api/v2/`）
   - 支持版本协商

3. **配置兼容**：
   - Kubernetes 和 Prometheus 集成默认禁用
   - 通过配置开关控制功能启用
   - 不影响现有 Jaeger 集成

4. **前端兼容**：
   - 前端支持降级渲染
   - 如果后端不返回新字段，使用默认值
   - 保持现有功能正常工作

### 6.3 风险和缓解措施

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| Prometheus 查询性能问题 | 高 | 中 | 实现查询缓存、批量查询优化、异步查询 |
| Kubernetes API 权限不足 | 高 | 中 | 提供详细的权限配置文档、支持多种认证方式 |
| 数据一致性问题 | 中 | 中 | 实现数据版本控制、时间戳对齐 |
| 内存占用过高 | 中 | 低 | 实现分页查询、LRU 缓存、定期清理 |
| 前端渲染性能 | 中 | 中 | 虚拟化渲染、节点聚合、按需加载 |

### 6.4 成功指标

**功能指标**：
- ✅ 支持 Pod、Node、Container 节点展示
- ✅ 支持 Prometheus 指标查询和展示
- ✅ 支持 Kubernetes 元数据查询
- ✅ 支持多数据源数据合并

**性能指标**：
- Prometheus 单次查询 < 500ms
- Kubernetes API 查询 < 300ms
- 完整拓扑构建 < 5s
- 前端渲染 1000 节点 < 2s

**质量指标**：
- 单元测试覆盖率 > 80%
- 集成测试覆盖率 > 70%
- 代码审查通过率 100%
- 文档完整性 > 90%

---

## 7. 总结

本方案提供了一个全面的架构改造和 Prometheus 集成计划，主要包括：

1. **拓扑结构扩展**：从三级模型扩展为四级模型，新增 CLUSTER、NODE、CONTAINER、METRIC 等实体类型
2. **数据模型完善**：新增 PrometheusMetrics、KubernetesMetadata 等数据模型类
3. **Prometheus 集成**：实现完整的 Prometheus 指标查询和展示功能
4. **Kubernetes 集成**：实现 Kubernetes API 集成，获取 Pod、Node、Container 元数据
5. **数据源编排**：整合 Jaeger、Kubernetes、Prometheus 三个数据源
6. **分阶段实施**：提供详细的 6 个阶段实施计划，总计 10-15 周

该方案保持向后兼容性，支持渐进式升级，风险可控。


