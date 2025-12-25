## 1. 概述

MicroGraph 是一个用于展示 Kubernetes 资源拓扑关系的可视化系统。系统采用**分域设计**，将 K8s 资源按功能职责划分为四大领域，每个领域包含相关的资源类型，通过边关系描述资源间的依赖和管理关系。

---

## 2. 领域划分

系统将 Kubernetes 资源划分为以下四个领域：

| 领域 Key       | 领域名称   | 说明                                                         |
| -------------- | ---------- | ------------------------------------------------------------ |
| `k8s.infra`    | 基础设施   | 集群级基础资源，如节点、命名空间、持久卷、存储类             |
| `k8s.workload` | 工作负载   | 应用运行载体，如 Deployment、Pod、Job 等                     |
| `k8s.network`  | 网络       | 网络通信相关资源，如 Service、Ingress、NetworkPolicy         |
| `k8s.config`   | 配置与安全 | 配置、存储声明与访问控制，如 ConfigMap、Secret、ServiceAccount |


### 2.1 领域数据结构

```typescript
interface Domain {
  key: string;        // 领域唯一标识，如 'k8s.workload'
  name: string;       // 显示名称，如 '工作负载'
  color: string;      // 主题颜色，如 '#10B981'
  icon: string;       // 图标，如 '⚙️'
  entityCount: number;// 该领域下的实体数量
}
```

---

## 3. 节点类型定义（按领域分类）

### 3.1 基础设施域

**领域标识**：`k8s.infra`

| 资源类型           | Type 标识                      | 作用域 | 说明                                         |
| ------------------ | ------------------------------ | ------ | -------------------------------------------- |
| Namespace          | `k8s.infra.namespace`          | 集群级 | 命名空间，资源隔离边界，用于多租户隔离       |
| Node               | `k8s.infra.node`               | 集群级 | 集群节点，Pod 运行的物理/虚拟主机            |
| PersistentVolume   | `k8s.infra.persistentvolume`   | 集群级 | 持久卷，集群级存储资源，由管理员预先配置     |
| StorageClass       | `k8s.infra.storageclass`       | 集群级 | 存储类，定义动态存储供应的配置模板           |
| ClusterRole        | `k8s.infra.clusterrole`        | 集群级 | 集群角色，定义集群级别的权限规则             |
| ClusterRoleBinding | `k8s.infra.clusterrolebinding` | 集群级 | 集群角色绑定，将集群角色绑定到用户或服务账号 |


#### Namespace 详细属性

| 属性     | 类型                   | 说明         |
| -------- | ---------------------- | ------------ |
| `name`   | string                 | 命名空间名称 |
| `labels` | Record<string, string> | 标签         |
| `status` | NodeStatus             | 状态         |


#### Node 详细属性

| 属性               | 类型            | 说明                      |
| ------------------ | --------------- | ------------------------- |
| `name`             | string          | 节点名称                  |
| `status`           | string          | 节点状态                  |
| `roles`            | string[]        | 节点角色（master/worker） |
| `version`          | string          | Kubernetes 版本           |
| `osImage`          | string          | 操作系统镜像              |
| `kernelVersion`    | string          | 内核版本                  |
| `containerRuntime` | string          | 容器运行时                |
| `capacity`         | NodeResources   | 节点容量                  |
| `allocatable`      | NodeResources   | 可分配资源                |
| `conditions`       | NodeCondition[] | 节点状况                  |
| `addresses`        | NodeAddress[]   | 节点地址                  |


```typescript
interface NodeResources {
  cpu: string;              // CPU 数量
  memory: string;           // 内存大小
  pods: string;             // 最大 Pod 数
  ephemeralStorage?: string;// 临时存储
}
```

---

### 3.2 工作负载域

**领域标识**：`k8s.workload`

| 资源类型              | Type 标识                            | 作用域     | 说明                                              |
| --------------------- | ------------------------------------ | ---------- | ------------------------------------------------- |
| Deployment            | `k8s.workload.deployment`            | 命名空间级 | 无状态应用部署控制器，支持滚动更新和回滚          |
| ReplicaSet            | `k8s.workload.replicaset`            | 命名空间级 | 副本集，维护 Pod 副本数量，通常由 Deployment 管理 |
| StatefulSet           | `k8s.workload.statefulset`           | 命名空间级 | 有状态应用部署控制器，保证 Pod 的顺序性和唯一性   |
| DaemonSet             | `k8s.workload.daemonset`             | 命名空间级 | 守护进程集，确保每个节点运行一个 Pod 副本         |
| Job                   | `k8s.workload.job`                   | 命名空间级 | 一次性任务，确保 Pod 成功运行至完成               |
| CronJob               | `k8s.workload.cronjob`               | 命名空间级 | 定时任务，按 Cron 表达式周期性创建 Job            |
| Pod                   | `k8s.workload.pod`                   | 命名空间级 | 最小调度单元，一个或多个容器的集合                |
| ReplicationController | `k8s.workload.replicationcontroller` | 命名空间级 | 副本控制器（已废弃），ReplicaSet 的前身           |


#### Deployment 详细属性

| 属性                | 类型                   | 说明         |
| ------------------- | ---------------------- | ------------ |
| `name`              | string                 | 名称         |
| `namespace`         | string                 | 命名空间     |
| `replicas`          | number                 | 期望副本数   |
| `availableReplicas` | number                 | 可用副本数   |
| `readyReplicas`     | number                 | 就绪副本数   |
| `updatedReplicas`   | number                 | 已更新副本数 |
| `labels`            | Record<string, string> | 标签         |
| `selector`          | Record<string, string> | Pod 选择器   |
| `strategy`          | string                 | 更新策略     |
| `creationTimestamp` | string                 | 创建时间     |


#### Pod 详细属性

| 属性                | 类型            | 说明                                     |
| ------------------- | --------------- | ---------------------------------------- |
| `name`              | string          | Pod 名称                                 |
| `namespace`         | string          | 命名空间                                 |
| `status`            | string          | 状态                                     |
| `phase`             | string          | 阶段（Pending/Running/Succeeded/Failed） |
| `podIP`             | string          | Pod IP 地址                              |
| `hostIP`            | string          | 宿主机 IP                                |
| `nodeName`          | string          | 所在节点名称                             |
| `containers`        | ContainerInfo[] | 容器列表                                 |
| `restartCount`      | number          | 重启次数                                 |
| `creationTimestamp` | string          | 创建时间                                 |


```typescript
interface ContainerInfo {
  name: string;        // 容器名称
  image: string;       // 镜像
  ready: boolean;      // 是否就绪
  restartCount: number;// 重启次数
  state: string;       // 运行状态
}
```

#### Job 详细属性

| 属性             | 类型   | 说明       |
| ---------------- | ------ | ---------- |
| `name`           | string | 名称       |
| `namespace`      | string | 命名空间   |
| `completions`    | number | 期望完成数 |
| `parallelism`    | number | 并行度     |
| `succeeded`      | number | 成功完成数 |
| `failed`         | number | 失败数     |
| `startTime`      | string | 开始时间   |
| `completionTime` | string | 完成时间   |


#### CronJob 详细属性

| 属性                         | 类型    | 说明                             |
| ---------------------------- | ------- | -------------------------------- |
| `name`                       | string  | 名称                             |
| `namespace`                  | string  | 命名空间                         |
| `schedule`                   | string  | Cron 表达式（如 `*/5 * * * *`）  |
| `suspend`                    | boolean | 是否暂停                         |
| `concurrencyPolicy`          | string  | 并发策略（Allow/Forbid/Replace） |
| `successfulJobsHistoryLimit` | number  | 保留成功 Job 历史数量            |
| `failedJobsHistoryLimit`     | number  | 保留失败 Job 历史数量            |
| `lastScheduleTime`           | string  | 上次调度时间                     |
| `lastSuccessfulTime`         | string  | 上次成功时间                     |
| `activeJobs`                 | number  | 活跃 Job 数量                    |


#### ReplicaSet 详细属性

| 属性                   | 类型                   | 说明                          |
| ---------------------- | ---------------------- | ----------------------------- |
| `name`                 | string                 | 名称                          |
| `namespace`            | string                 | 命名空间                      |
| `replicas`             | number                 | 期望副本数                    |
| `availableReplicas`    | number                 | 可用副本数                    |
| `readyReplicas`        | number                 | 就绪副本数                    |
| `fullyLabeledReplicas` | number                 | 完全标记的副本数              |
| `selector`             | Record<string, string> | Pod 选择器                    |
| `ownerReferences`      | OwnerReference[]       | 所有者引用（指向 Deployment） |
| `creationTimestamp`    | string                 | 创建时间                      |


```typescript
interface OwnerReference {
  apiVersion: string;     // API 版本
  kind: string;           // 资源类型（如 Deployment）
  name: string;           // 所有者名称
  uid: string;            // 所有者 UID
  controller?: boolean;   // 是否为控制器
  blockOwnerDeletion?: boolean; // 是否阻止级联删除
}
```

#### StatefulSet 详细属性

| 属性                   | 类型                   | 说明                                  |
| ---------------------- | ---------------------- | ------------------------------------- |
| `name`                 | string                 | 名称                                  |
| `namespace`            | string                 | 命名空间                              |
| `replicas`             | number                 | 期望副本数                            |
| `readyReplicas`        | number                 | 就绪副本数                            |
| `currentReplicas`      | number                 | 当前副本数                            |
| `updatedReplicas`      | number                 | 已更新副本数                          |
| `serviceName`          | string                 | 关联的 Headless Service 名称          |
| `selector`             | Record<string, string> | Pod 选择器                            |
| `podManagementPolicy`  | string                 | Pod 管理策略（OrderedReady/Parallel） |
| `updateStrategy`       | UpdateStrategy         | 更新策略                              |
| `volumeClaimTemplates` | PVCTemplate[]          | PVC 模板列表                          |
| `currentRevision`      | string                 | 当前修订版本                          |
| `updateRevision`       | string                 | 更新修订版本                          |
| `collisionCount`       | number                 | 哈希冲突计数                          |


```typescript
interface UpdateStrategy {
  type: string;           // 类型（RollingUpdate/OnDelete）
  rollingUpdate?: {
    partition?: number;   // 分区，只更新序号 >= partition 的 Pod
    maxUnavailable?: number | string; // 最大不可用数
  };
}

interface PVCTemplate {
  metadata: {
    name: string;         // PVC 名称前缀
    labels?: Record<string, string>;
  };
  spec: {
    accessModes: string[];      // 访问模式
    storageClassName?: string;  // 存储类
    resources: {
      requests: {
        storage: string;        // 存储容量
      };
    };
  };
}
```

#### DaemonSet 详细属性

| 属性                     | 类型                    | 说明                             |
| ------------------------ | ----------------------- | -------------------------------- |
| `name`                   | string                  | 名称                             |
| `namespace`              | string                  | 命名空间                         |
| `desiredNumberScheduled` | number                  | 期望调度数（应该运行的节点数）   |
| `currentNumberScheduled` | number                  | 当前调度数                       |
| `numberReady`            | number                  | 就绪数                           |
| `numberAvailable`        | number                  | 可用数                           |
| `numberUnavailable`      | number                  | 不可用数                         |
| `numberMisscheduled`     | number                  | 错误调度数（不应运行但正在运行） |
| `updatedNumberScheduled` | number                  | 已更新的调度数                   |
| `selector`               | Record<string, string>  | Pod 选择器                       |
| `updateStrategy`         | DaemonSetUpdateStrategy | 更新策略                         |
| `nodeSelector`           | Record<string, string>  | 节点选择器                       |
| `tolerations`            | Toleration[]            | 容忍度配置                       |


```typescript
interface DaemonSetUpdateStrategy {
  type: string;           // 类型（RollingUpdate/OnDelete）
  rollingUpdate?: {
    maxUnavailable?: number | string;  // 最大不可用数
    maxSurge?: number | string;        // 最大超出数
  };
}

interface Toleration {
  key?: string;           // 污点键
  operator?: string;      // 操作符（Exists/Equal）
  value?: string;         // 污点值
  effect?: string;        // 效果（NoSchedule/PreferNoSchedule/NoExecute）
  tolerationSeconds?: number; // 容忍时间（秒）
}
```

#### ReplicationController 详细属性

| 属性                   | 类型                   | 说明             |
| ---------------------- | ---------------------- | ---------------- |
| `name`                 | string                 | 名称             |
| `namespace`            | string                 | 命名空间         |
| `replicas`             | number                 | 期望副本数       |
| `readyReplicas`        | number                 | 就绪副本数       |
| `availableReplicas`    | number                 | 可用副本数       |
| `fullyLabeledReplicas` | number                 | 完全标记的副本数 |
| `selector`             | Record<string, string> | Pod 选择器       |
| `creationTimestamp`    | string                 | 创建时间         |


> **注意**：ReplicationController 已被废弃，建议使用 Deployment + ReplicaSet 替代。

---

### 3.3 网络域

**领域标识**：`k8s.network`

| 资源类型      | Type 标识                   | 作用域     | 说明                                          |
| ------------- | --------------------------- | ---------- | --------------------------------------------- |
| Service       | `k8s.network.service`       | 命名空间级 | 服务发现与负载均衡，为 Pod 提供稳定的访问入口 |
| Ingress       | `k8s.network.ingress`       | 命名空间级 | HTTP/HTTPS 路由入口，提供外部访问能力         |
| IngressClass  | `k8s.network.ingressclass`  | 集群级     | Ingress 控制器类型定义                        |
| NetworkPolicy | `k8s.network.networkpolicy` | 命名空间级 | 网络策略，控制 Pod 间的网络流量               |
| Endpoints     | `k8s.network.endpoints`     | 命名空间级 | 端点，Service 后端 Pod 的 IP 和端口列表       |
| EndpointSlice | `k8s.network.endpointslice` | 命名空间级 | 端点切片，Endpoints 的可扩展替代方案          |


#### Service 详细属性

| 属性          | 类型                   | 说明                                                 |
| ------------- | ---------------------- | ---------------------------------------------------- |
| `name`        | string                 | 服务名称                                             |
| `namespace`   | string                 | 命名空间                                             |
| `type`        | string                 | 类型（ClusterIP/NodePort/LoadBalancer/ExternalName） |
| `clusterIP`   | string                 | 集群 IP                                              |
| `externalIPs` | string[]               | 外部 IP 列表                                         |
| `ports`       | ServicePort[]          | 端口配置                                             |
| `selector`    | Record<string, string> | Pod 选择器                                           |


```typescript
interface ServicePort {
  name?: string;              // 端口名称
  protocol: string;           // 协议（TCP/UDP/SCTP）
  port: number;               // 服务端口
  targetPort: number | string;// 目标端口
  nodePort?: number;          // 节点端口
}
```

#### Ingress 详细属性

| 属性               | 类型          | 说明          |
| ------------------ | ------------- | ------------- |
| `name`             | string        | 名称          |
| `namespace`        | string        | 命名空间      |
| `ingressClassName` | string        | Ingress Class |
| `rules`            | IngressRule[] | 路由规则      |
| `tls`              | IngressTLS[]  | TLS 配置      |


```typescript
interface IngressRule {
  host?: string;        // 主机名
  paths: IngressPath[]; // 路径规则
}

interface IngressPath {
  path: string;         // 路径
  pathType: string;     // 匹配类型（Exact/Prefix/ImplementationSpecific）
  backend: {
    serviceName: string;// 后端服务名
    servicePort: number | string;
  };
}
```

#### NetworkPolicy 详细属性

| 属性          | 类型                   | 说明                       |
| ------------- | ---------------------- | -------------------------- |
| `name`        | string                 | 名称                       |
| `namespace`   | string                 | 命名空间                   |
| `podSelector` | Record<string, string> | 目标 Pod 选择器            |
| `policyTypes` | string[]               | 策略类型（Ingress/Egress） |
| `ingress`     | IngressRule[]          | 入站规则                   |
| `egress`      | EgressRule[]           | 出站规则                   |


---

### 3.4 配置与安全域

**领域标识**：`k8s.config`

| 资源类型                | Type 标识                            | 作用域     | 说明                                        |
| ----------------------- | ------------------------------------ | ---------- | ------------------------------------------- |
| ConfigMap               | `k8s.config.configmap`               | 命名空间级 | 配置数据存储，以键值对形式保存非敏感配置    |
| Secret                  | `k8s.config.secret`                  | 命名空间级 | 敏感数据存储，保存密码、令牌、证书等        |
| PersistentVolumeClaim   | `k8s.config.persistentvolumeclaim`   | 命名空间级 | 持久卷声明，申请存储资源                    |
| ServiceAccount          | `k8s.config.serviceaccount`          | 命名空间级 | 服务账号，为 Pod 提供身份标识               |
| Role                    | `k8s.config.role`                    | 命名空间级 | 角色，定义命名空间级别的权限规则            |
| RoleBinding             | `k8s.config.rolebinding`             | 命名空间级 | 角色绑定，将角色绑定到用户或服务账号        |
| LimitRange              | `k8s.config.limitrange`              | 命名空间级 | 限制范围，定义命名空间内资源的默认限制      |
| ResourceQuota           | `k8s.config.resourcequota`           | 命名空间级 | 资源配额，限制命名空间的资源使用总量        |
| HorizontalPodAutoscaler | `k8s.config.horizontalpodautoscaler` | 命名空间级 | 水平 Pod 自动扩缩器，根据指标自动调整副本数 |


#### ConfigMap 详细属性

| 属性         | 类型                   | 说明       |
| ------------ | ---------------------- | ---------- |
| `name`       | string                 | 名称       |
| `namespace`  | string                 | 命名空间   |
| `data`       | Record<string, string> | 配置数据   |
| `binaryData` | Record<string, string> | 二进制数据 |


#### Secret 详细属性

| 属性        | 类型                   | 说明                                                         |
| ----------- | ---------------------- | ------------------------------------------------------------ |
| `name`      | string                 | 名称                                                         |
| `namespace` | string                 | 命名空间                                                     |
| `type`      | string                 | 类型（Opaque/kubernetes.io/tls/kubernetes.io/dockerconfigjson 等） |
| `data`      | Record<string, string> | 数据（Base64 编码）                                          |


#### PVC 详细属性

| 属性               | 类型                   | 说明                                                 |
| ------------------ | ---------------------- | ---------------------------------------------------- |
| `name`             | string                 | 名称                                                 |
| `namespace`        | string                 | 命名空间                                             |
| `status`           | string                 | 状态（Pending/Bound/Lost）                           |
| `volumeName`       | string                 | 绑定的 PV 名称                                       |
| `storageClassName` | string                 | 存储类                                               |
| `accessModes`      | string[]               | 访问模式（ReadWriteOnce/ReadOnlyMany/ReadWriteMany） |
| `capacity`         | Record<string, string> | 容量                                                 |


#### ServiceAccount 详细属性

| 属性                           | 类型                   | 说明               |
| ------------------------------ | ---------------------- | ------------------ |
| `name`                         | string                 | 名称               |
| `namespace`                    | string                 | 命名空间           |
| `secrets`                      | ObjectReference[]      | 关联的 Secret 列表 |
| `imagePullSecrets`             | LocalObjectReference[] | 镜像拉取凭证       |
| `automountServiceAccountToken` | boolean                | 是否自动挂载令牌   |


---

## 4. 核心数据模型

### 4.1 节点

| 字段         | 类型                   | 必填 | 说明                       | 示例                                 |
| ------------ | ---------------------- | ---- | -------------------------- | ------------------------------------ |
| `id`         | string                 | ✅    | 节点唯一标识               | `k8s.workload.pod/default/nginx-abc` |
| `type`       | string                 | ✅    | 资源类型                   | `k8s.workload.pod`                   |
| `domain`     | string                 | ✅    | 所属领域                   | `k8s.workload`                       |
| `name`       | string                 | ✅    | 资源名称                   | `nginx-abc`                          |
| `namespace`  | string                 | ❌    | 命名空间（集群级资源可选） | `default`                            |
| `labels`     | Record<string, string> | ✅    | K8s 标签                   | `{"app": "nginx"}`                   |
| `properties` | Record<string, any>    | ✅    | 扩展属性（资源特有属性）   | `{"replicas": 3}`                    |
| `status`     | NodeStatus             | ✅    | 节点状态                   | `running`                            |


### 4.2 边

| 字段         | 类型                | 必填 | 说明               | 示例                                       |
| ------------ | ------------------- | ---- | ------------------ | ------------------------------------------ |
| `id`         | string              | ✅    | 边唯一标识         | `edge-001`                                 |
| `type`       | EdgeType            | ✅    | 边类型（关系类型） | `owns`                                     |
| `source`     | string              | ✅    | 源节点 ID          | `k8s.workload.deployment/default/nginx`    |
| `target`     | string              | ✅    | 目标节点 ID        | `k8s.workload.replicaset/default/nginx-rs` |
| `properties` | Record<string, any> | ✅    | 扩展属性           | `{"weight": 1}`                            |


---

## 5. 枚举定义

### 5.1 节点状态

| 值           | 说明   | 边框颜色       | 发光效果                   |
| ------------ | ------ | -------------- | -------------------------- |
| `running`    | 运行中 | `#10B981` 绿色 | `rgba(16, 185, 129, 0.3)`  |
| `warning`    | 警告   | `#F59E0B` 黄色 | `rgba(245, 158, 11, 0.3)`  |
| `error`      | 错误   | `#EF4444` 红色 | `rgba(239, 68, 68, 0.5)`   |
| `pending`    | 等待中 | `#6B7280` 灰色 | `rgba(107, 114, 128, 0.2)` |
| `terminated` | 已终止 | `#9CA3AF` 浅灰 | `rgba(156, 163, 175, 0.1)` |


### 5.2 边类型

#### contains（包含）

**语义含义**：表示一个资源在逻辑上包含另一个资源，被包含的资源存在于包含者的范围内。

**方向性**：源节点（包含者）→ 目标节点（被包含者）

**应用场景**：

+ Namespace 包含其下的所有资源（Deployment、Service、Pod 等）
+ Node 包含运行在其上的 Pod（从节点视角）

**示例**：

+ `Namespace:default` → `Deployment:nginx-deployment`
+ `Namespace:kube-system` → `Service:kube-dns`

---

#### owns（拥有）

**语义含义**：表示资源间的所有权关系，拥有者负责被拥有资源的生命周期管理。当拥有者被删除时，被拥有资源也会被级联删除（通过 OwnerReferences 实现）。

**方向性**：源节点（拥有者）→ 目标节点（被拥有者）

**应用场景**：

+ Deployment 拥有其创建的 ReplicaSet
+ StatefulSet 拥有其创建的 Pod
+ ReplicaSet 拥有其创建的 Pod

**示例**：

+ `Deployment:nginx` → `ReplicaSet:nginx-5d4b8c7d9`
+ `StatefulSet:mysql` → `Pod:mysql-0`

---

#### manages（管理）

**语义含义**：表示资源间的管理关系，管理者负责监控和维护被管理资源的期望状态，但不一定有严格的所有权关系。

**方向性**：源节点（管理者）→ 目标节点（被管理者）

**应用场景**：

+ ReplicaSet 管理 Pod 副本，确保副本数量符合期望
+ DaemonSet 管理各节点上的 Pod
+ StatefulSet 管理有状态 Pod 的创建和删除顺序

**示例**：

+ `ReplicaSet:nginx-5d4b8c7d9` → `Pod:nginx-5d4b8c7d9-abc12`
+ `DaemonSet:fluentd` → `Pod:fluentd-node1`

---

#### creates（创建）

**语义含义**：表示一个资源负责创建另一个资源，强调的是创建行为和触发关系。

**方向性**：源节点（创建者）→ 目标节点（被创建者）

**应用场景**：

+ Job 创建 Pod 来执行一次性任务
+ CronJob 按计划创建 Job
+ HorizontalPodAutoscaler 触发 Deployment 创建新的 Pod

**示例**：

+ `Job:data-migration` → `Pod:data-migration-xyz`
+ `CronJob:backup` → `Job:backup-1638360000`

---

#### selects（选择）

**语义含义**：表示通过标签选择器（Label Selector）建立的动态关联关系。选择关系是松耦合的，只要标签匹配就会建立关联。

**方向性**：源节点（选择者）→ 目标节点（被选择者）

**应用场景**：

+ Service 通过 selector 选择匹配的 Pod 作为后端
+ NetworkPolicy 通过 podSelector 选择受策略影响的 Pod
+ ReplicaSet 通过 selector 识别其管理的 Pod

**示例**：

+ `Service:nginx-svc` → `Pod:nginx-pod-1`（selector: app=nginx）
+ `NetworkPolicy:deny-all` → `Pod:web-app`（podSelector: role=web）

---

#### routes_to（路由到）

**语义含义**：表示网络流量的路由关系，通常用于描述外部流量如何进入集群并到达目标服务。

**方向性**：源节点（路由入口）→ 目标节点（路由目标）

**应用场景**：

+ Ingress 将外部 HTTP/HTTPS 请求路由到 Service
+ Service（LoadBalancer 类型）路由外部流量到 Pod
+ Gateway（Istio 等）路由流量到 VirtualService

**示例**：

+ `Ingress:web-ingress` → `Service:frontend-svc`（host: [www.example.com）](http://www.example.com）)
+ `Ingress:api-ingress` → `Service:api-svc`（path: /api/*）

---

#### runs_on（运行于）

**语义含义**：表示工作负载的调度位置关系，即 Pod 被调度到哪个节点上运行。

**方向性**：源节点（工作负载）→ 目标节点（运行环境）

**应用场景**：

+ Pod 运行在指定的 Node 上
+ Pod 运行在特定的可用区或机房

**示例**：

+ `Pod:nginx-abc` → `Node:worker-node-1`
+ `Pod:mysql-0` → `Node:worker-node-2`

---

#### mounts（挂载）

**语义含义**：表示工作负载挂载存储或配置资源的关系，Pod 可以将外部数据源挂载为容器内的文件系统。

**方向性**：源节点（挂载者）→ 目标节点（被挂载资源）

**应用场景**：

+ Pod 挂载 PersistentVolume 获取持久化存储
+ Pod 挂载 ConfigMap 注入配置文件
+ Pod 挂载 Secret 注入敏感信息（密码、证书等）

**示例**：

+ `Pod:mysql-0` → `PersistentVolume:mysql-data`
+ `Pod:nginx` → `ConfigMap:nginx-config`
+ `Pod:app` → `Secret:db-credentials`

---

#### claims（声明）

**语义含义**：表示资源声明和绑定关系，用于描述存储资源的申请与分配。

**方向性**：源节点（声明者）→ 目标节点（被声明资源）

**应用场景**：

+ PersistentVolumeClaim 声明并绑定 PersistentVolume
+ StorageClass 定义动态存储供应规则

**示例**：

+ `PersistentVolumeClaim:mysql-pvc` → `PersistentVolume:pv-001`
+ `PersistentVolumeClaim:data-claim` → `PersistentVolume:nfs-pv`

---

#### calls（调用）

**语义含义**：表示服务间的调用关系，通常通过服务网格或应用追踪发现。这是一种运行时的动态关系。

**方向性**：源节点（调用方）→ 目标节点（被调用方）

**应用场景**：

+ 微服务间的 HTTP/gRPC 调用
+ Service 到 Service 的依赖关系
+ 通过 Istio/Linkerd 等服务网格发现的调用链

**示例**：

+ `Service:frontend` → `Service:backend-api`
+ `Service:order-service` → `Service:payment-service`
+ `Service:api-gateway` → `Service:user-service`

---

#### binds_to（绑定到）

**语义含义**：表示访问控制中的绑定关系，将权限规则绑定到主体（用户、组、服务账号）。

**方向性**：源节点（绑定资源）→ 目标节点（权限规则）

**应用场景**：

+ RoleBinding 将 Role 绑定到 ServiceAccount
+ ClusterRoleBinding 将 ClusterRole 绑定到用户或组
+ ServiceAccount 与 Secret（Token）的绑定

**示例**：

+ `RoleBinding:read-pods` → `Role:pod-reader`
+ `ClusterRoleBinding:admin-binding` → `ClusterRole:cluster-admin`

---

#### uses（使用）

**语义含义**：表示资源使用另一个资源的关系，是一种通用的依赖关系。

**方向性**：源节点（使用者）→ 目标节点（被使用资源）

**应用场景**：

+ Pod 使用 ServiceAccount 进行身份认证
+ Deployment 使用 HorizontalPodAutoscaler 进行自动扩缩
+ Ingress 使用 Secret 存储 TLS 证书

**示例**：

+ `Pod:app` → `ServiceAccount:app-sa`
+ `Deployment:web` → `HorizontalPodAutoscaler:web-hpa`
+ `Ingress:secure-ingress` → `Secret:tls-cert`

---

## 6. 复合数据结构

### 6.1 图数据

```typescript
interface GraphData {
  nodes: MicroGraphNode[];  // 所有节点
  edges: MicroGraphEdge[];  // 所有边
  domains: Domain[];        // 领域定义
}
```

### 6.2 过滤配置

| 字段            | 类型         | 说明           |
| --------------- | ------------ | -------------- |
| `domains`       | string[]     | 筛选的领域列表 |
| `namespaces`    | string[]     | 筛选的命名空间 |
| `resourceTypes` | string[]     | 筛选的资源类型 |
| `statuses`      | NodeStatus[] | 筛选的状态     |
| `searchText`    | string       | 搜索关键词     |


### 6.3 统计信息

| 字段             | 类型                       | 说明           |
| ---------------- | -------------------------- | -------------- |
| `totalNodes`     | number                     | 节点总数       |
| `totalEdges`     | number                     | 边总数         |
| `domainStats`    | Record<string, number>     | 按领域统计     |
| `statusStats`    | Record<NodeStatus, number> | 按状态统计     |
| `namespaceStats` | Record<string, number>     | 按命名空间统计 |


---

## 7. 节点 ID 命名规范

```plain
{type}/{namespace}/{name}
```

其中 `type` 格式为：`{domain}.{resourceType}`

**示例：**

+ `k8s.workload.pod/default/nginx-pod-abc` - 工作负载域的 Pod
+ `k8s.network.service/kube-system/coredns` - 网络域的 Service
+ `k8s.infra.node/node-1` - 基础设施域的 Node（集群级无 namespace）
+ `k8s.infra.persistentvolume//my-pv` - PV（集群级无 namespace）

---

## 8. 领域关系全景图

![](https://cdn.nlark.com/yuque/0/2025/png/12488790/1764848205301-0ef03416-003d-4af3-b131-a2159740a442.png)

---

## 9. 资源类型速查表

| 领域       | 资源类型                | Type 标识                            | 作用域   |
| ---------- | ----------------------- | ------------------------------------ | -------- |
| 基础设施   | Namespace               | `k8s.infra.namespace`                | 集群     |
| 基础设施   | Node                    | `k8s.infra.node`                     | 集群     |
| 基础设施   | PersistentVolume        | `k8s.infra.persistentvolume`         | 集群     |
| 基础设施   | StorageClass            | `k8s.infra.storageclass`             | 集群     |
| 基础设施   | ClusterRole             | `k8s.infra.clusterrole`              | 集群     |
| 基础设施   | ClusterRoleBinding      | `k8s.infra.clusterrolebinding`       | 集群     |
| 工作负载   | Deployment              | `k8s.workload.deployment`            | 命名空间 |
| 工作负载   | ReplicaSet              | `k8s.workload.replicaset`            | 命名空间 |
| 工作负载   | StatefulSet             | `k8s.workload.statefulset`           | 命名空间 |
| 工作负载   | DaemonSet               | `k8s.workload.daemonset`             | 命名空间 |
| 工作负载   | Job                     | `k8s.workload.job`                   | 命名空间 |
| 工作负载   | CronJob                 | `k8s.workload.cronjob`               | 命名空间 |
| 工作负载   | Pod                     | `k8s.workload.pod`                   | 命名空间 |
| 工作负载   | ReplicationController   | `k8s.workload.replicationcontroller` | 命名空间 |
| 网络       | Service                 | `k8s.network.service`                | 命名空间 |
| 网络       | Ingress                 | `k8s.network.ingress`                | 命名空间 |
| 网络       | IngressClass            | `k8s.network.ingressclass`           | 集群     |
| 网络       | NetworkPolicy           | `k8s.network.networkpolicy`          | 命名空间 |
| 网络       | Endpoints               | `k8s.network.endpoints`              | 命名空间 |
| 网络       | EndpointSlice           | `k8s.network.endpointslice`          | 命名空间 |
| 配置与安全 | ConfigMap               | `k8s.config.configmap`               | 命名空间 |
| 配置与安全 | Secret                  | `k8s.config.secret`                  | 命名空间 |
| 配置与安全 | PersistentVolumeClaim   | `k8s.config.persistentvolumeclaim`   | 命名空间 |
| 配置与安全 | ServiceAccount          | `k8s.config.serviceaccount`          | 命名空间 |
| 配置与安全 | Role                    | `k8s.config.role`                    | 命名空间 |
| 配置与安全 | RoleBinding             | `k8s.config.rolebinding`             | 命名空间 |
| 配置与安全 | LimitRange              | `k8s.config.limitrange`              | 命名空间 |
| 配置与安全 | ResourceQuota           | `k8s.config.resourcequota`           | 命名空间 |
| 配置与安全 | HorizontalPodAutoscaler | `k8s.config.horizontalpodautoscaler` | 命名空间 |

