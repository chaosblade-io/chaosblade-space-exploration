# 统一镜像部署指南

本文档介绍如何使用统一镜像部署 ChaosBlade Space Exploration 微服务架构。

## 概述

统一镜像方案将原本的6个独立微服务合并为一个镜像，通过环境变量或命令行参数来启动不同的服务。这样可以：

- **减少镜像数量**：从6个镜像减少到1个
- **简化部署**：统一的部署流程和配置
- **降低运维成本**：减少镜像管理和更新工作
- **保持服务独立性**：每个服务仍然可以独立运行和扩展

## 架构对比

### 原始架构（多镜像）
```
svc-task-resource:1.0.0    (端口: 8101)
svc-task-executor:1.0.0    (端口: 8102)
svc-fault-scheduler:1.0.0  (端口: 8103)
svc-result-processor:1.0.0 (端口: 8104)
svc-reqrsp-proxy:1.0.0     (端口: 8105)
svc-topo:1.0.0             (端口: 8106)
```

### 统一架构（单镜像）
```
chaosblade-space-exploration-unified:1.0.0
├── task-resource    (端口: 8101)
├── task-executor    (端口: 8102)
├── fault-scheduler  (端口: 8103)
├── result-processor (端口: 8104)
├── reqrsp-proxy     (端口: 8105)
└── topo             (端口: 8106)
```

## 快速开始

### 1. 构建统一镜像

```bash
# 构建镜像
./scripts/build-unified.sh --tag v1.0.0

# 构建并推送到仓库
./scripts/build-unified.sh --tag v1.0.0 --push
```

### 2. 本地运行

```bash
# 启动任务资源服务
./scripts/start-unified.sh task-resource

# 启动请求响应代理服务
./scripts/start-unified.sh reqrsp-proxy

# 后台运行所有服务
./scripts/start-unified.sh --build --detach task-resource
./scripts/start-unified.sh --detach task-executor
./scripts/start-unified.sh --detach fault-scheduler
./scripts/start-unified.sh --detach result-processor
./scripts/start-unified.sh --detach reqrsp-proxy
./scripts/start-unified.sh --detach topo
```

### 3. Docker 直接运行

```bash
# 启动任务资源服务
docker run --rm -e SERVICE_NAME=task-resource -p 8101:8101 \
  -e DB_HOST=localhost -e DB_USERNAME=root -e DB_PASSWORD=root \
  chaosblade/svc-unified:latest

# 启动请求响应代理服务
docker run --rm -e SERVICE_NAME=reqrsp-proxy -p 8105:8105 \
  -e DB_HOST=localhost -e REDIS_HOST=localhost \
  chaosblade/svc-unified:latest
```

## 服务配置

### 环境变量

| 变量名 | 描述 | 默认值 | 必需 |
|--------|------|--------|------|
| `SERVICE_NAME` | 要启动的服务名 | - | 是 |
| `SERVER_PORT` | 服务端口 | 8080 | 否 |
| `DB_HOST` | 数据库主机 | localhost | 是 |
| `DB_PORT` | 数据库端口 | 3306 | 否 |
| `DB_NAME` | 数据库名 | spaceexploration | 否 |
| `DB_USERNAME` | 数据库用户名 | root | 是 |
| `DB_PASSWORD` | 数据库密码 | root | 是 |
| `REDIS_HOST` | Redis主机 | localhost | 是 |
| `REDIS_PORT` | Redis端口 | 6379 | 否 |
| `KUBERNETES_API_URL` | K8s API地址 | - | 否 |
| `KUBERNETES_TOKEN` | K8s Token | - | 否 |

### 支持的服务

| 服务名 | 端口 | 描述 |
|--------|------|------|
| `task-resource` | 8101 | 任务资源管理服务 |
| `task-executor` | 8102 | 任务执行服务 |
| `fault-scheduler` | 8103 | 故障调度服务 |
| `result-processor` | 8104 | 结果处理服务 |
| `reqrsp-proxy` | 8105 | 请求响应代理服务 |
| `topo` | 8106 | 拓扑服务 |

## Kubernetes 部署

### 使用 Helm Chart

```bash
# 1. 使用统一镜像部署
helm install chaosblade-space-exploration ./helm/chaosblade-space-exploration \
  -f ./helm/chaosblade-space-exploration/values-unified.yaml \
  -n chaosblade --create-namespace

# 2. 检查部署状态
kubectl get pods -n chaosblade

# 3. 查看服务状态
kubectl get svc -n chaosblade
```

### 手动部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chaosblade-task-resource
  namespace: chaosblade
spec:
  replicas: 1
  selector:
    matchLabels:
      app: chaosblade-task-resource
  template:
    metadata:
      labels:
        app: chaosblade-task-resource
    spec:
      containers:
      - name: task-resource
        image: chaosblade/svc-unified:latest
        ports:
        - containerPort: 8101
        env:
        - name: SERVICE_NAME
          value: "task-resource"
        - name: SERVER_PORT
          value: "8101"
        - name: DB_HOST
          value: "mysql-service"
        - name: DB_USERNAME
          value: "root"
        - name: DB_PASSWORD
          value: "root"
        resources:
          limits:
            cpu: 500m
            memory: 1Gi
          requests:
            cpu: 250m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: chaosblade-task-resource
  namespace: chaosblade
spec:
  selector:
    app: chaosblade-task-resource
  ports:
  - port: 8101
    targetPort: 8101
    protocol: TCP
```

## 开发指南

### 本地开发

```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 运行统一应用
cd svc-unified
mvn spring-boot:run -Dspring-boot.run.arguments="--service=task-resource"

# 3. 或者直接运行JAR
java -jar target/svc-unified-*.jar --service=task-resource
```

### 添加新服务

1. 在 `UnifiedApplication.java` 中添加服务映射：
```java
SERVICE_MAPPING.put("new-service", NewServiceApplication.class);
```

2. 在 `values-unified.yaml` 中添加服务配置：
```yaml
services:
  newService:
    enabled: true
    replicas: 1
    resources:
      limits:
        cpu: 500m
        memory: 1Gi
```

3. 在 `start-unified.sh` 中添加端口映射：
```bash
SERVICE_PORTS["new-service"]="8107"
```

## 监控和调试

### 健康检查

```bash
# 检查服务健康状态
curl http://localhost:8101/actuator/health  # task-resource
curl http://localhost:8102/actuator/health  # task-executor
curl http://localhost:8103/actuator/health  # fault-scheduler
curl http://localhost:8104/actuator/health  # result-processor
curl http://localhost:8105/actuator/health  # reqrsp-proxy
curl http://localhost:8106/actuator/health  # topo
```

### 查看日志

```bash
# 查看特定服务日志
./scripts/start-unified.sh --logs task-resource

# 查看所有服务状态
./scripts/start-unified.sh --status

# 停止服务
./scripts/start-unified.sh --stop task-resource
```

### 调试模式

```bash
# 启用调试日志
docker run --rm -e SERVICE_NAME=task-resource \
  -e LOG_LEVEL_CHAOSBLADE=DEBUG \
  -e LOG_LEVEL_SPRING_WEB=DEBUG \
  -p 8101:8101 \
  chaosblade/svc-unified:latest
```

## 性能优化

### 资源限制

```yaml
resources:
  limits:
    cpu: 1000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi
```

### JVM 调优

```bash
# 设置JVM参数
docker run --rm -e SERVICE_NAME=task-resource \
  -e JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC" \
  -p 8101:8101 \
  chaosblade/svc-unified:latest
```

## 故障排除

### 常见问题

1. **服务启动失败**
   - 检查环境变量配置
   - 确认数据库和Redis连接
   - 查看容器日志

2. **端口冲突**
   - 使用不同的端口映射
   - 检查端口是否被占用

3. **内存不足**
   - 调整JVM堆内存设置
   - 增加容器内存限制

### 日志分析

```bash
# 查看详细启动日志
docker logs -f <container-name>

# 查看特定服务的错误日志
docker logs <container-name> 2>&1 | grep ERROR
```

## 迁移指南

### 从多镜像迁移到统一镜像

1. **备份现有配置**
```bash
kubectl get configmap -n chaosblade -o yaml > config-backup.yaml
kubectl get secret -n chaosblade -o yaml > secret-backup.yaml
```

2. **更新Helm配置**
```bash
# 修改values.yaml
unified:
  enabled: true
```

3. **重新部署**
```bash
helm upgrade chaosblade-space-exploration ./helm/chaosblade-space-exploration \
  -f values-unified.yaml -n chaosblade
```

4. **验证服务**
```bash
kubectl get pods -n chaosblade
kubectl logs -f deployment/chaosblade-space-exploration-task-resource -n chaosblade
```

## 最佳实践

1. **使用环境变量**：避免硬编码配置
2. **资源限制**：为每个服务设置合适的资源限制
3. **健康检查**：配置适当的健康检查探针
4. **日志管理**：使用结构化日志和日志聚合
5. **监控告警**：设置服务监控和告警规则
6. **版本管理**：使用语义化版本号管理镜像

## 总结

统一镜像方案提供了更简化的部署和运维体验，同时保持了微服务架构的灵活性。通过合理配置和使用，可以显著降低运维复杂度，提高部署效率。
