# ChaosBlade Space Exploration Helm Chart

这个 Helm Chart 用于在 Kubernetes 集群中部署 ChaosBlade Space Exploration 微服务项目。

## 概述

ChaosBlade Space Exploration 是一个基于 Spring Boot 的微服务项目，用于混沌工程空间探索。该项目包含以下服务：

- **svc-task-resource** (8101): 任务资源管理服务
- **svc-task-executor** (8102): 任务执行服务
- **svc-fault-scheduler** (8103): 故障注入调度服务
- **svc-result-processor** (8104): 结果处理服务
- **svc-reqrsp-proxy** (8105): 请求/响应代理服务
- **svc-topo** (8106): 拓扑感知服务

## 前置条件

- Kubernetes 1.24+
- Helm 3.0+
- 可访问的 MySQL 数据库（可选，Chart 包含内置 MySQL）
- 可访问的 Redis 实例（可选，Chart 包含内置 Redis）
- 适当的 RBAC 权限（用于 Kubernetes API 访问）

## 安装

### 1. 添加依赖

```bash
# 更新 Helm 依赖
helm dependency update ./chaosblade-space-exploration
```

### 2. 基本安装

```bash
# 使用默认配置安装
helm install chaosblade-space-exploration ./chaosblade-space-exploration

# 指定命名空间
helm install chaosblade-space-exploration ./chaosblade-space-exploration -n chaosblade --create-namespace

# 使用自定义 values 文件
helm install chaosblade-space-exploration ./chaosblade-space-exploration -f values-production.yaml
```

### 3. 生产环境安装

```bash
# 使用生产环境配置
helm install chaosblade-space-exploration ./chaosblade-space-exploration \
  -f values-production.yaml \
  -n chaosblade \
  --create-namespace
```

## 配置

### 主要配置参数

| 参数 | 描述 | 默认值 |
|------|------|--------|
| `replicaCount` | 每个服务的副本数 | `1` |
| `image.repository` | 镜像仓库 | `chaosblade-io/chaosblade-space-exploration` |
| `image.tag` | 镜像标签 | `1.0.0-SNAPSHOT` |
| `service.type` | 服务类型 | `ClusterIP` |
| `ingress.enabled` | 是否启用 Ingress | `false` |
| `mysql.enabled` | 是否启用内置 MySQL | `true` |
| `redis.enabled` | 是否启用内置 Redis | `true` |

### 数据库配置

```yaml
database:
  type: mysql
  host: "mysql.example.com"
  port: 3306
  name: spaceexploration
  username: chaosblade
  password: "your-password"
  existingSecret: "chaosblade-db-secret"  # 使用现有 Secret
```

### Redis 配置

```yaml
redis:
  host: "redis.example.com"
  port: 6379
  database: 0
  password: "your-redis-password"
  existingSecret: "chaosblade-redis-secret"  # 使用现有 Secret
```

### Kubernetes API 配置

```yaml
kubernetes:
  apiUrl: "https://kubernetes.default.svc"
  token: "your-k8s-token"
  verifySsl: true
  existingSecret: "chaosblade-k8s-secret"  # 使用现有 Secret
```

### 服务特定配置

```yaml
services:
  taskResource:
    enabled: true
    resources:
      limits:
        cpu: 1000m
        memory: 2Gi
      requests:
        cpu: 500m
        memory: 1Gi
```

## 使用外部数据库和 Redis

### 1. 创建 Secret

```bash
# 数据库 Secret
kubectl create secret generic chaosblade-db-secret \
  --from-literal=username=chaosblade \
  --from-literal=password=your-password \
  --from-literal=url="jdbc:mysql://mysql.example.com:3306/spaceexploration?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  -n chaosblade

# Redis Secret
kubectl create secret generic chaosblade-redis-secret \
  --from-literal=password=your-redis-password \
  -n chaosblade

# Kubernetes API Secret
kubectl create secret generic chaosblade-k8s-secret \
  --from-literal=token=your-k8s-token \
  -n chaosblade

# LLM API Secret
kubectl create secret generic chaosblade-llm-secret \
  --from-literal=key=your-llm-api-key \
  -n chaosblade
```

### 2. 更新 values.yaml

```yaml
# 禁用内置数据库和 Redis
mysql:
  enabled: false

redis:
  enabled: false

# 配置外部服务
database:
  existingSecret: "chaosblade-db-secret"

redis:
  existingSecret: "chaosblade-redis-secret"

kubernetes:
  existingSecret: "chaosblade-k8s-secret"

llm:
  existingSecret: "chaosblade-llm-secret"
```

## RBAC 权限

Chart 会自动创建必要的 RBAC 资源：

- **ClusterRole**: 包含以下权限
  - `pods`, `pods/exec`, `configmaps`, `services`, `events` 的 `get`, `list`, `watch`, `create`, `update`, `patch`
  - `deployments` 的 `get`, `list`, `watch`, `patch`
  - `chaosblade.io/chaosblades` 的 `get`, `list`, `watch`, `create`, `delete`

- **ClusterRoleBinding**: 将权限绑定到 ServiceAccount

## 监控和日志

### 健康检查

每个服务都提供健康检查端点：

```bash
# 检查服务健康状态
curl http://<service-ip>:<port>/actuator/health
```

### 日志查看

```bash
# 查看所有服务日志
kubectl logs -l app.kubernetes.io/name=chaosblade-space-exploration -n chaosblade -f

# 查看特定服务日志
kubectl logs -l app.kubernetes.io/component=task-resource -n chaosblade -f
```

### Prometheus 监控

如果启用了 ServiceMonitor，可以通过 Prometheus 监控服务指标：

```yaml
serviceMonitor:
  enabled: true
  interval: 30s
  scrapeTimeout: 10s
```

## 故障排除

### 1. 检查 Pod 状态

```bash
kubectl get pods -n chaosblade
kubectl describe pod <pod-name> -n chaosblade
```

### 2. 检查服务状态

```bash
kubectl get svc -n chaosblade
kubectl describe svc <service-name> -n chaosblade
```

### 3. 检查 RBAC 权限

```bash
kubectl get clusterrole,clusterrolebinding | grep chaosblade
kubectl describe clusterrole <role-name>
```

### 4. 检查 Secret 和 ConfigMap

```bash
kubectl get secret,configmap -n chaosblade
kubectl describe secret <secret-name> -n chaosblade
```

### 5. 常见问题

- **Pod 启动失败**: 检查资源限制、镜像拉取权限、环境变量配置
- **数据库连接失败**: 检查数据库 Secret 和网络连接
- **Kubernetes API 访问失败**: 检查 RBAC 权限和 Token 配置
- **服务间通信失败**: 检查 Service 和 DNS 配置

## 升级和回滚

### 升级

```bash
# 升级到新版本
helm upgrade chaosblade-space-exploration ./chaosblade-space-exploration

# 使用新配置升级
helm upgrade chaosblade-space-exploration ./chaosblade-space-exploration -f values-production.yaml
```

### 回滚

```bash
# 查看发布历史
helm history chaosblade-space-exploration

# 回滚到上一个版本
helm rollback chaosblade-space-exploration

# 回滚到指定版本
helm rollback chaosblade-space-exploration <revision-number>
```

## 卸载

```bash
# 卸载 Chart
helm uninstall chaosblade-space-exploration -n chaosblade

# 删除命名空间（可选）
kubectl delete namespace chaosblade
```

## 开发

### 本地开发

```bash
# 模板渲染测试
helm template chaosblade-space-exploration ./chaosblade-space-exploration

# 语法检查
helm lint ./chaosblade-space-exploration

# 依赖更新
helm dependency update ./chaosblade-space-exploration
```

### 自定义配置

1. 复制 `values.yaml` 到 `my-values.yaml`
2. 修改所需配置
3. 使用自定义配置安装：

```bash
helm install chaosblade-space-exploration ./chaosblade-space-exploration -f my-values.yaml
```

## 贡献

欢迎提交 Issue 和 Pull Request 来改进这个 Helm Chart。

## 许可证

本项目使用 Apache License 2.0 许可证。
