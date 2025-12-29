# svc-k8s-graph API 接口文档

## 概述

svc-k8s-graph 服务提供 K8s 资源拓扑图、资源详情查询、资源关系分析以及链路追踪等功能。

- **基础地址**: `http://1.94.151.57:8106`
- **API 前缀**: `/api/k8s-graph`

### 支持的资源类型

#### 基础设施资源（集群级，无需 namespace）

| 资源类型 | Type 标识 | 说明 |
|---------|----------|------|
| `namespace` | k8s.infra.namespace | 命名空间 |
| `node` | k8s.infra.node | 集群节点 |
| `persistentvolume` | k8s.infra.persistentvolume | 持久卷 |
| `storageclass` | k8s.infra.storageclass | 存储类 |
| `clusterrole` | k8s.infra.clusterrole | 集群角色 |
| `clusterrolebinding` | k8s.infra.clusterrolebinding | 集群角色绑定 |
| `ingressclass` | k8s.network.ingressclass | Ingress 类 |

#### 工作负载资源（命名空间级，需要 namespace）

| 资源类型 | Type 标识 | 说明 |
|---------|----------|------|
| `deployment` | k8s.workload.deployment | 部署 |
| `replicaset` | k8s.workload.replicaset | 副本集 |
| `statefulset` | k8s.workload.statefulset | 有状态副本集 |
| `daemonset` | k8s.workload.daemonset | 守护进程集 |
| `job` | k8s.workload.job | 任务 |
| `cronjob` | k8s.workload.cronjob | 定时任务 |
| `pod` | k8s.workload.pod | 容器组 |
| `replicationcontroller` | k8s.workload.replicationcontroller | 副本控制器 |

#### 网络资源

| 资源类型 | Type 标识 | 作用域 | 说明 |
|---------|----------|-------|------|
| `service` | k8s.network.service | 命名空间 | 服务 |
| `ingress` | k8s.network.ingress | 命名空间 | 入口 |
| `networkpolicy` | k8s.network.networkpolicy | 命名空间 | 网络策略 |
| `endpoints` | k8s.network.endpoints | 命名空间 | 端点 |
| `endpointslice` | k8s.network.endpointslice | 命名空间 | 端点切片 |

#### 配置与安全资源（命名空间级，需要 namespace）

| 资源类型 | Type 标识 | 说明 |
|---------|----------|------|
| `configmap` | k8s.config.configmap | 配置映射 |
| `secret` | k8s.config.secret | 密钥 |
| `persistentvolumeclaim` | k8s.config.persistentvolumeclaim | 持久卷声明 |
| `serviceaccount` | k8s.config.serviceaccount | 服务账户 |
| `role` | k8s.config.role | 角色 |
| `rolebinding` | k8s.config.rolebinding | 角色绑定 |
| `limitrange` | k8s.config.limitrange | 限制范围 |
| `resourcequota` | k8s.config.resourcequota | 资源配额 |
| `horizontalpodautoscaler` | k8s.config.horizontalpodautoscaler | 水平 Pod 自动扩缩容 |

### 通用响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": "200",
  "message": "success",
  "data": { ... }
}
```

### 错误响应格式

```json
{
  "code": "500",
  "message": "错误描述信息",
  "data": null
}
```

---

## 一、资源详情接口

### 1.1 获取资源基本详情

获取指定 K8s 资源的基本信息，包括元数据、标签、状态等。

**请求**

```
GET /api/k8s-graph/{resourceType}/{resourceName}?namespace={namespace}
```

**路径参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| resourceType | string | ✅ | 资源类型，支持以下值（不区分大小写）：<br>**基础设施**: namespace, node, persistentvolume, storageclass, clusterrole, clusterrolebinding, ingressclass<br>**工作负载**: deployment, replicaset, statefulset, daemonset, job, cronjob, pod, replicationcontroller<br>**网络**: service, ingress, networkpolicy, endpoints, endpointslice<br>**配置与安全**: configmap, secret, persistentvolumeclaim, serviceaccount, role, rolebinding, limitrange, resourcequota, horizontalpodautoscaler |
| resourceName | string | ✅ | 资源名称 |

**查询参数**

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|-----|-------|------|
| namespace | string | 否 | default | 命名空间。集群级资源（namespace, node, persistentvolume, storageclass, clusterrole, clusterrolebinding, ingressclass）可忽略此参数 |

**请求示例**

```bash
# ========== 网络资源 ==========
# 获取 Service 详情
curl "http://1.94.151.57:8106/api/k8s-graph/service/ts-travel-service?namespace=default"

# 获取 Ingress 详情
curl "http://1.94.151.57:8106/api/k8s-graph/ingress/ts-ingress?namespace=default"

# 获取 Endpoints 详情
curl "http://1.94.151.57:8106/api/k8s-graph/endpoints/ts-travel-service?namespace=default"

# ========== 工作负载资源 ==========
# 获取 Pod 详情
curl "http://1.94.151.57:8106/api/k8s-graph/pod/ts-travel-service-6d8b9c7f5-abc12?namespace=default"

# 获取 Deployment 详情
curl "http://1.94.151.57:8106/api/k8s-graph/deployment/ts-travel-service?namespace=default"

# 获取 StatefulSet 详情
curl "http://1.94.151.57:8106/api/k8s-graph/statefulset/mysql?namespace=default"

# 获取 CronJob 详情
curl "http://1.94.151.57:8106/api/k8s-graph/cronjob/backup-job?namespace=default"

# ========== 基础设施资源（无需 namespace）==========
# 获取 Node 详情
curl "http://1.94.151.57:8106/api/k8s-graph/node/node-1"

# 获取 Namespace 详情
curl "http://1.94.151.57:8106/api/k8s-graph/namespace/default"

# 获取 PersistentVolume 详情
curl "http://1.94.151.57:8106/api/k8s-graph/persistentvolume/pv-data-01"

# 获取 StorageClass 详情
curl "http://1.94.151.57:8106/api/k8s-graph/storageclass/standard"

# ========== 配置与安全资源 ==========
# 获取 ConfigMap 详情
curl "http://1.94.151.57:8106/api/k8s-graph/configmap/app-config?namespace=default"

# 获取 Secret 详情
curl "http://1.94.151.57:8106/api/k8s-graph/secret/db-credentials?namespace=default"

# 获取 PersistentVolumeClaim 详情
curl "http://1.94.151.57:8106/api/k8s-graph/persistentvolumeclaim/data-pvc?namespace=default"

# 获取 HorizontalPodAutoscaler 详情
curl "http://1.94.151.57:8106/api/k8s-graph/horizontalpodautoscaler/ts-travel-hpa?namespace=default"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "resourceType": "service",
    "resourceName": "ts-travel-service",
    "namespace": "default",
    "uid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "creationTimestamp": "2024-01-15T10:30:00Z",
    "status": "Active",
    "labels": {
      "app": "ts-travel-service",
      "version": "v1"
    },
    "annotations": {
      "prometheus.io/scrape": "true"
    },
    "resourceNode": {
      "id": "k8s.service/default/ts-travel-service",
      "type": "k8s.service",
      "domain": "k8s",
      "name": "ts-travel-service",
      "namespace": "default"
    },
    "properties": {
      "type": "ClusterIP",
      "clusterIP": "10.96.100.50",
      "ports": [
        { "port": 80, "targetPort": 8080, "protocol": "TCP" }
      ],
      "selector": { "app": "ts-travel-service" }
    }
  }
}
```

**不同资源类型的 properties 字段**

**基础设施资源**

| 资源类型 | properties 包含的字段 |
|---------|---------------------|
| namespace | phase（状态：Active/Terminating） |
| node | InternalIP, Hostname, osImage, kubeletVersion, containerRuntime, allocatable, capacity |
| persistentvolume | capacity, accessModes, storageClassName, persistentVolumeReclaimPolicy, phase |
| storageclass | provisioner, reclaimPolicy, volumeBindingMode, allowVolumeExpansion |
| clusterrole | rules（权限规则列表） |
| clusterrolebinding | roleRef, subjects |
| ingressclass | controller, isDefault |

**工作负载资源**

| 资源类型 | properties 包含的字段 |
|---------|---------------------|
| deployment | replicas, readyReplicas, availableReplicas, updatedReplicas, strategy |
| replicaset | replicas, readyReplicas, availableReplicas |
| statefulset | replicas, readyReplicas, currentReplicas, serviceName, podManagementPolicy |
| daemonset | desiredNumberScheduled, currentNumberScheduled, numberReady, numberAvailable |
| job | completions, parallelism, succeeded, failed, active, startTime, completionTime |
| cronjob | schedule, lastScheduleTime, suspend, activeJobs |
| pod | nodeName, podIP, hostIP, containers, initContainers, phase, startTime, restartPolicy |
| replicationcontroller | replicas, readyReplicas, availableReplicas |

**网络资源**

| 资源类型 | properties 包含的字段 |
|---------|---------------------|
| service | type, clusterIP, externalIPs, ports, selector, sessionAffinity |
| ingress | ingressClassName, rules, tls, defaultBackend |
| networkpolicy | podSelector, policyTypes, ingress, egress |
| endpoints | subsets（包含 addresses 和 ports） |
| endpointslice | addressType, endpoints, ports |

**配置与安全资源**

| 资源类型 | properties 包含的字段 |
|---------|---------------------|
| configmap | dataKeys（配置键列表）, binaryDataKeys |
| secret | type, dataKeys（密钥键列表，不返回实际值） |
| persistentvolumeclaim | accessModes, storageClassName, volumeName, capacity, phase |
| serviceaccount | secrets, automountServiceAccountToken |
| role | rules（权限规则列表） |
| rolebinding | roleRef, subjects |
| limitrange | limits（限制规则列表） |
| resourcequota | hard（硬限制）, used（已使用量） |
| horizontalpodautoscaler | minReplicas, maxReplicas, currentReplicas, desiredReplicas, metrics, currentMetrics |

---

### 1.2 获取资源 YAML 定义

获取指定资源的完整 YAML 定义。

> **注意**: 当前仅支持 Service 类型资源的 YAML 获取。

**请求**

```
GET /api/k8s-graph/service/{namespace}/{serviceName}/yaml
```

**路径参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| namespace | string | ✅ | 命名空间 |
| serviceName | string | ✅ | Service 名称 |

**请求示例**

```bash
curl "http://1.94.151.57:8106/api/k8s-graph/service/default/ts-travel-service/yaml"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": "apiVersion: v1\nkind: Service\nmetadata:\n  name: ts-travel-service\n  namespace: default\nspec:\n  selector:\n    app: ts-travel-service\n  ports:\n    - port: 80\n      targetPort: 8080\n  type: ClusterIP\n"
}
```

---

### 1.3 获取资源关系

获取指定资源与其他 K8s 资源之间的关联关系（技术层面）。

**请求**

```
GET /api/k8s-graph/{resourceType}/{resourceName}/relations?namespace={namespace}
```

**路径参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| resourceType | string | ✅ | 资源类型，支持以下值（不区分大小写）：<br>**基础设施**: namespace, node, persistentvolume, storageclass, clusterrole, clusterrolebinding, ingressclass<br>**工作负载**: deployment, replicaset, statefulset, daemonset, job, cronjob, pod, replicationcontroller<br>**网络**: service, ingress, networkpolicy, endpoints, endpointslice<br>**配置与安全**: configmap, secret, persistentvolumeclaim, serviceaccount, role, rolebinding, limitrange, resourcequota, horizontalpodautoscaler |
| resourceName | string | ✅ | 资源名称 |

**查询参数**

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|-----|-------|------|
| namespace | string | 否 | default | 命名空间。集群级资源（namespace, node, persistentvolume, storageclass, clusterrole, clusterrolebinding, ingressclass）可忽略此参数 |

**请求示例**

```bash
# ========== 网络资源关系 ==========
# 获取 Service 关联的资源（Pod、Ingress、Endpoints）
curl "http://1.94.151.57:8106/api/k8s-graph/service/ts-travel-service/relations?namespace=default"

# 获取 Ingress 关联的 Service
curl "http://1.94.151.57:8106/api/k8s-graph/ingress/ts-ingress/relations?namespace=default"

# ========== 工作负载资源关系 ==========
# 获取 Pod 关联的资源（Node、Owner、ConfigMap、Secret、PVC）
curl "http://1.94.151.57:8106/api/k8s-graph/pod/ts-travel-service-6d8b9c7f5-abc12/relations?namespace=default"

# 获取 Deployment 关联的 Pod 和 ReplicaSet
curl "http://1.94.151.57:8106/api/k8s-graph/deployment/ts-travel-service/relations?namespace=default"

# 获取 StatefulSet 关联的 Pod 和 PVC
curl "http://1.94.151.57:8106/api/k8s-graph/statefulset/mysql/relations?namespace=default"

# 获取 CronJob 关联的 Job
curl "http://1.94.151.57:8106/api/k8s-graph/cronjob/backup-job/relations?namespace=default"

# ========== 基础设施资源关系（无需 namespace）==========
# 获取 Node 上运行的所有 Pod
curl "http://1.94.151.57:8106/api/k8s-graph/node/node-1/relations"

# 获取 Namespace 中的所有资源
curl "http://1.94.151.57:8106/api/k8s-graph/namespace/default/relations"

# 获取 PersistentVolume 关联的 PVC
curl "http://1.94.151.57:8106/api/k8s-graph/persistentvolume/pv-data-01/relations"

# ========== 配置与安全资源关系 ==========
# 获取 ConfigMap 被哪些 Pod 使用
curl "http://1.94.151.57:8106/api/k8s-graph/configmap/app-config/relations?namespace=default"

# 获取 PVC 关联的 PV 和使用它的 Pod
curl "http://1.94.151.57:8106/api/k8s-graph/persistentvolumeclaim/data-pvc/relations?namespace=default"

# 获取 ServiceAccount 关联的 Pod 和 RoleBinding
curl "http://1.94.151.57:8106/api/k8s-graph/serviceaccount/default/relations?namespace=default"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "resourceType": "service",
    "resourceName": "ts-travel-service",
    "namespace": "default",
    "resourceNode": {
      "id": "k8s.service/default/ts-travel-service",
      "type": "k8s.service",
      "domain": "k8s",
      "name": "ts-travel-service",
      "namespace": "default"
    },
    "relatedNodes": [
      {
        "id": "k8s.pod/default/ts-travel-service-6d8b9c7f5-abc12",
        "type": "k8s.pod",
        "domain": "k8s",
        "name": "ts-travel-service-6d8b9c7f5-abc12",
        "namespace": "default"
      },
      {
        "id": "k8s.ingress/default/ts-ingress",
        "type": "k8s.ingress",
        "domain": "k8s",
        "name": "ts-ingress",
        "namespace": "default"
      }
    ],
    "edges": [
      {
        "id": "edge-uuid-1",
        "type": "SELECTS",
        "source": "k8s.service/default/ts-travel-service",
        "target": "k8s.pod/default/ts-travel-service-6d8b9c7f5-abc12"
      },
      {
        "id": "edge-uuid-2",
        "type": "ROUTES_TO",
        "source": "k8s.ingress/default/ts-ingress",
        "target": "k8s.service/default/ts-travel-service"
      }
    ]
  }
}
```

**各资源类型返回的关系**

**基础设施资源**

| 资源类型 | 关联资源 | 关系类型 |
|---------|---------|---------|
| namespace | 命名空间内的所有资源（Pod、Service、Deployment 等） | CONTAINS |
| node | Pod（运行在节点上的） | RUNS_ON |
| persistentvolume | PersistentVolumeClaim（绑定的） | BINDS |
| storageclass | PersistentVolume（使用该存储类的） | PROVISIONS |
| clusterrole | ClusterRoleBinding（引用该角色的） | BINDS |
| clusterrolebinding | ClusterRole（绑定的角色）、Subject（绑定的主体） | BINDS |
| ingressclass | Ingress（使用该类的） | USES |

**工作负载资源**

| 资源类型 | 关联资源 | 关系类型 |
|---------|---------|---------|
| deployment | ReplicaSet（创建的）、Pod（管理的） | OWNS |
| replicaset | Pod（管理的）、Deployment（所属的） | OWNS |
| statefulset | Pod（管理的）、PVC（关联的） | OWNS |
| daemonset | Pod（管理的） | OWNS |
| job | Pod（创建的）、CronJob（所属的） | OWNS |
| cronjob | Job（创建的） | OWNS |
| pod | Node（所在节点）、Owner（Deployment/RS/STS/DS/Job）、ConfigMap、Secret、PVC | RUNS_ON, OWNS, USES |
| replicationcontroller | Pod（管理的） | OWNS |

**网络资源**

| 资源类型 | 关联资源 | 关系类型 |
|---------|---------|---------|
| service | Pod（被选中的）、Ingress（路由到该服务的）、Endpoints | SELECTS, ROUTES_TO |
| ingress | Service（路由到的）、IngressClass | ROUTES_TO, USES |
| networkpolicy | Pod（影响的） | APPLIES_TO |
| endpoints | Service（所属的）、Pod（端点地址） | BELONGS_TO |
| endpointslice | Service（所属的）、Pod（端点地址） | BELONGS_TO |

**配置与安全资源**

| 资源类型 | 关联资源 | 关系类型 |
|---------|---------|---------|
| configmap | Pod（使用该配置的） | USED_BY |
| secret | Pod（使用该密钥的）、ServiceAccount | USED_BY |
| persistentvolumeclaim | PersistentVolume（绑定的）、Pod（使用的） | BINDS, USED_BY |
| serviceaccount | Pod（使用该账户的）、Secret、RoleBinding/ClusterRoleBinding | USED_BY, BINDS |
| role | RoleBinding（引用该角色的） | BINDS |
| rolebinding | Role（绑定的角色）、Subject（绑定的主体） | BINDS |
| limitrange | Namespace（所属的） | APPLIES_TO |
| resourcequota | Namespace（所属的） | APPLIES_TO |
| horizontalpodautoscaler | Deployment/StatefulSet（扩缩的目标） | SCALES |

**EdgeType 边类型说明**

| 类型 | 说明 |
|-----|------|
| OWNS | 所有权关系（如 Deployment → ReplicaSet → Pod） |
| SELECTS | 选择关系（如 Service → Pod） |
| RUNS_ON | 运行关系（如 Pod → Node） |
| ROUTES_TO | 路由关系（如 Ingress → Service） |
| CALLS | 调用关系（服务间调用，来自链路追踪） |
| CONTAINS | 包含关系（如 Namespace → 资源） |
| BINDS | 绑定关系（如 PVC → PV、RoleBinding → Role） |
| USES | 使用关系（如 Pod → ConfigMap/Secret/PVC） |
| USED_BY | 被使用关系（反向引用） |
| PROVISIONS | 供应关系（如 StorageClass → PV） |
| BELONGS_TO | 从属关系（如 Endpoints → Service） |
| APPLIES_TO | 应用于关系（如 NetworkPolicy → Pod） |
| SCALES | 扩缩关系（如 HPA → Deployment） |

---

## 二、追踪接口（仅限 Service 资源）

> ⚠️ **重要提醒**: 以下追踪相关接口仅适用于 **service** 类型的资源。其他资源类型（pod, deployment, statefulset, daemonset, node, ingress）不支持追踪和监控功能。追踪数据来源于分布式链路追踪系统，仅与服务级别的调用相关。

### 2.1 获取服务追踪列表

获取指定服务在特定时间范围内的追踪记录列表。

**请求**

```
GET /api/k8s-graph/traces?serviceName={serviceName}&from={timestamp}&to={timestamp}
```

**查询参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| serviceName | string | ✅ | 服务名称（仅支持 service 类型资源） |
| from | long | ✅ | 开始时间（毫秒时间戳） |
| to | long | ✅ | 结束时间（毫秒时间戳） |

**请求示例**

```bash
# 获取最近1小时的追踪记录
curl "http://1.94.151.57:8106/api/k8s-graph/traces?serviceName=ts-travel-service&from=1703520000000&to=1703523600000"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "traces": [
      {
        "traceId": "abc123def456789",
        "duration": 1250000000,
        "startTime": 1703520100000000000,
        "endTime": 1703520101250000000,
        "statusCode": 200,
        "serviceName": "ts-travel-service",
        "operationName": "GET /api/v1/travelservice/trips",
        "spanCount": 5,
        "hasError": false,
        "httpMethod": "GET",
        "httpUrl": "/api/v1/travelservice/trips"
      },
      {
        "traceId": "xyz789abc123456",
        "duration": 3500000000,
        "startTime": 1703520200000000000,
        "endTime": 1703520203500000000,
        "statusCode": 500,
        "serviceName": "ts-travel-service",
        "operationName": "POST /api/v1/travelservice/trips",
        "spanCount": 8,
        "hasError": true,
        "httpMethod": "POST",
        "httpUrl": "/api/v1/travelservice/trips"
      }
    ],
    "total": 2,
    "hasMore": false
  }
}
```

**TraceInfo 字段说明**

| 字段 | 类型 | 说明 |
|-----|------|------|
| traceId | string | 追踪ID |
| duration | long | 持续时间（纳秒） |
| startTime | long | 开始时间（纳秒时间戳） |
| endTime | long | 结束时间（纳秒时间戳） |
| statusCode | int | HTTP 状态码 |
| serviceName | string | 服务名称 |
| operationName | string | 操作名称 |
| spanCount | int | Span 数量 |
| hasError | boolean | 是否有错误 |
| httpMethod | string | HTTP 方法 |
| httpUrl | string | HTTP URL |

---

### 2.2 获取追踪详情

获取指定 Trace ID 的详细信息，包括所有 Span 数据。

**请求**

```
GET /api/k8s-graph/traces/{traceId}
```

**路径参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| traceId | string | ✅ | 追踪ID |

**请求示例**

```bash
curl "http://1.94.151.57:8106/api/k8s-graph/traces/abc123def456789"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "batches": [
      {
        "resource": {
          "attributes": [
            { "key": "service.name", "value": { "stringValue": "ts-travel-service" } }
          ]
        },
        "scopeSpans": [
          {
            "spans": [
              {
                "traceId": "abc123def456789",
                "spanId": "span001",
                "parentSpanId": "",
                "name": "GET /api/v1/travelservice/trips",
                "startTimeUnixNano": 1703520100000000000,
                "endTimeUnixNano": 1703520101250000000,
                "attributes": [...]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

**错误响应（Trace 不存在）**

```json
{
  "code": "404",
  "message": "Trace not found: abc123def456789",
  "data": null
}
```

---

## 三、监控指标接口（仅限 Service 资源）

> ⚠️ **重要提醒**: 以下监控指标接口仅适用于 **service** 类型的资源。其他资源类型（pod, deployment, statefulset, daemonset, node, ingress）不支持追踪和监控功能。监控数据基于分布式追踪系统的链路数据聚合计算得出。

### 3.1 获取服务监控指标

获取指定服务在特定时间范围内的性能指标。

**请求**

```
GET /api/k8s-graph/service/{serviceName}/metrics?from={timestamp}&to={timestamp}
```

**路径参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| serviceName | string | ✅ | 服务名称（仅支持 service 类型资源） |

**查询参数**

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| from | long | ✅ | 开始时间（毫秒时间戳） |
| to | long | ✅ | 结束时间（毫秒时间戳） |

**请求示例**

```bash
# 获取最近1小时的服务指标
curl "http://1.94.151.57:8106/api/k8s-graph/service/ts-travel-service/metrics?from=1703520000000&to=1703523600000"
```

**响应示例**

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "serviceName": "ts-travel-service",
    "requestsPerSecond": 125.5,
    "errorRate": 2.3,
    "p50Latency": 45.2,
    "p95Latency": 180.5,
    "p99Latency": 350.8,
    "avgLatency": 68.3,
    "totalRequests": 451800,
    "errorRequests": 10391,
    "successRequests": 441409,
    "fromTime": 1703520000000,
    "toTime": 1703523600000
  }
}
```

**ServiceMetrics 字段说明**

| 字段 | 类型 | 说明 |
|-----|------|------|
| serviceName | string | 服务名称 |
| requestsPerSecond | double | 每秒请求数 (RPS) |
| errorRate | double | 错误率 (0-100%) |
| p50Latency | double | P50 延迟（毫秒） |
| p95Latency | double | P95 延迟（毫秒） |
| p99Latency | double | P99 延迟（毫秒） |
| avgLatency | double | 平均延迟（毫秒） |
| totalRequests | long | 总请求数 |
| errorRequests | long | 错误请求数 |
| successRequests | long | 成功请求数 |
| fromTime | long | 时间区间开始（毫秒时间戳） |
| toTime | long | 时间区间结束（毫秒时间戳） |

---

## 四、错误码说明

| 错误码 | 说明 |
|-------|------|
| 200 | 请求成功 |
| 404 | 资源不存在（如 Trace 未找到） |
| 500 | 服务器内部错误 |

---

## 五、附录

### 时间戳转换

接口中使用的时间戳均为**毫秒级**时间戳（13位数字）。

**JavaScript 示例**

```javascript
// 获取当前时间戳（毫秒）
const now = Date.now();

// 1小时前
const oneHourAgo = now - 3600000;

// 构建请求 URL
const url = `http://1.94.151.57:8106/api/k8s-graph/traces?serviceName=ts-travel-service&from=${oneHourAgo}&to=${now}`;
```

**Python 示例**

```python
import time

# 获取当前时间戳（毫秒）
now = int(time.time() * 1000)

# 1小时前
one_hour_ago = now - 3600000

url = f"http://1.94.151.57:8106/api/k8s-graph/traces?serviceName=ts-travel-service&from={one_hour_ago}&to={now}"
```

### GraphNode ID 格式

节点 ID 格式为 `{type}/{namespace}/{name}`，对于集群级资源（如 Node），格式为 `{type}//{name}`。

**示例**

- Service: `k8s.service/default/ts-travel-service`
- Pod: `k8s.pod/default/ts-travel-service-6d8b9c7f5-abc12`
- Node: `k8s.node//node-1`
- Deployment: `k8s.deployment/default/ts-travel-service`

