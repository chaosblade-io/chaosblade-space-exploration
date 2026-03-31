# 阿里云 K8s 部署踩坑总结

> 环境：阿里云 ACK（香港区域）K8s v1.34 + GHCR 镜像仓库
> 时间：2026-03-26 ~ 2026-03-27
> 涉及服务：chaosblade-space-exploration 全套微服务（9 个）

---

## 1. 镜像拉取与缓存问题（出现 6 次）

### 1.1 GHCR 私有镜像 401 Unauthorized

**现象**：Pod 状态 `ImagePullBackOff`，报错 `failed to authorize: 401 Unauthorized`。

**原因**：GHCR 新推送的 package 默认私有，集群节点匿名拉取被拒绝。

**修复**：
- 在目标 namespace 创建 `imagePullSecret`：`kubectl create secret docker-registry ghcr-secret --docker-server=ghcr.io --docker-username=xxx --docker-password=ghp_xxx`
- Helm 模板的 Pod spec 中添加 `imagePullSecrets: [{name: ghcr-secret}]`
- 对于动态创建的 Deployment（如 proxy-agent），代码中也需要显式设置 `imagePullSecrets`

### 1.2 latest 标签导致旧镜像缓存

**现象**：代码已更新推送，但 Pod 运行的还是旧代码（行为不变）。

**原因**：`imagePullPolicy: IfNotPresent` + `latest` 标签，节点缓存了旧镜像不会重新拉取。

**修复**：
- **禁止使用 `latest` 标签**，改用版本化标签如 `v20260327`、`git-sha` 等
- `imagePullPolicy` 设为 `Always`（或使用唯一标签后保持 `IfNotPresent`）
- 临时修复：`kubectl delete pod` 强制重建

### 1.3 动态创建的 Deployment 缺少 imagePullSecrets

**现象**：svc-reqrsp-proxy 动态创建的 proxy-agent Deployment 拉取镜像失败。

**原因**：`K8sProxyManager.deployProxy()` 构建 Deployment 时未设置 `imagePullSecrets`。

**修复**：在 Deployment 的 Pod spec 中添加 `.addNewImagePullSecret().withName("ghcr-secret").endImagePullSecret()`。

---

## 2. Service Selector 劫持未恢复（出现 5 次）

### 2.1 实验结束后 Service Selector 残留

**现象**：被测系统 API 返回 502，`curl` 测试全部失败。

**原因**：proxy-agent 通过 Selector Swap 劫持了 Service 的 selector（如 `frontend-proxy` 的 selector 被改为指向 proxy-agent Pod），但实验结束后未自动恢复，流量打到已停止的 proxy-agent 上。

**修复**：
- 在 `K8sProxyManager.destroyProxy()` 中自动调用 `restoreService()` + `deleteShadowService()`
- 确保无论成功还是异常，都执行完整清理链：恢复 selector → 删除 Deployment → 删除影子 Service
- 手动恢复命令：`kubectl patch svc {name} -n {ns} --type=json -p '[{"op":"replace","path":"/spec/selector","value":{"opentelemetry.io/name":"{name}"}}]'`

### 2.2 分析阶段的 proxy-agent 未被清理

**现象**：executor 只清理了 Stage5（intercept）的 recording，Stage2（analyze）创建的 proxy-agent 残留。

**原因**：executor cleanup 阶段只遍历 `interceptRecordId`，不包含 analyze 阶段的 recording IDs。

**修复**：在 `destroyProxy()` 中内置完整清理逻辑，任何路径调用都能自动恢复。

---

## 3. 数据库 Schema 不一致（出现 3 次）

### 3.1 缺少 `max_fault_services` 列

**现象**：executor 查询 `detection_tasks` 表时报 `Unknown column 'max_fault_services'`。

**原因**：Java 实体类新增了字段，但建表 SQL（init-db.sql）未同步更新。

### 3.2 `intercept_record_id` 列太短

**现象**：写入 `task_execution` 表时报 `Data too long for column 'intercept_record_id'`。

**原因**：列定义为 `VARCHAR(64)`，实际值（多个 recording ID 逗号拼接）超过 64 字符。

### 3.3 `proxy_instance` 表在错误的数据库中

**现象**：svc-reqrsp-proxy 报 `Table 'spaceexploration.proxy_instance' doesn't exist`。

**原因**：init-db.sql 把 `proxy_instance`、`proxy_snapshot`、`fixtures` 表建在了 `chaosblade` 数据库，但 svc-reqrsp-proxy 连接的是 `spaceexploration` 数据库。

**通用修复**：每次修改 Java 实体类后，同步更新 `init-db.sql` 和 `spaceexploration.sql`。

---

## 4. 环境变量与配置映射错误（出现 2 次）

### 4.1 Spring `@ConfigurationProperties` 前缀不匹配

**现象**：svc-task-resource 无法连接 svc-task-executor（502），使用了硬编码旧 IP `1.94.151.57`。

**原因**：`EndpointsProperties` 使用 `@ConfigurationProperties(prefix = "endpoints")`，绑定属性为 `endpoints.executor-base-url`。Helm 设置的环境变量 `EXECUTOR_BASE_URL` 映射到 `executor.base-url`，缺少 `endpoints.` 前缀。

**修复**：添加环境变量 `ENDPOINTS_EXECUTOR_BASE_URL`（符合 Spring Boot relaxed binding 规则）。

### 4.2 `application.yml` 硬编码覆盖环境变量

**现象**：Helm 注入的环境变量不生效。

**原因**：`application.yml` 中直接写死了值（不含 `${ENV_VAR}` 占位符），Spring Boot 加载 YAML 后不会被环境变量覆盖（除非 prefix 正确匹配 `@ConfigurationProperties`）。

**修复**：使用 `application_template.yml`（含 `${ENV_VAR:default}` 占位符）作为实际配置，或确保环境变量名与 `@ConfigurationProperties` 前缀匹配。

---

## 5. K8s 版本兼容性问题（出现 2 次）

### 5.1 Fabric8 不识别阿里云 K8s v1.34 的 `emulationMajor` 字段

**现象**：svc-fault-scheduler 启动时崩溃，`UnrecognizedPropertyException: Unrecognized field "emulationMajor"`。

**原因**：Fabric8 6.x 的 `VersionInfo` 类不识别阿里云 K8s v1.34 返回的新字段。

**修复**：将 `client.getKubernetesVersion()` 调用包裹在 try-catch 中，版本检测失败不影响客户端初始化。

### 5.2 Helm namespace 资源冲突

**现象**：`helm install` 报 `Namespace "chaosblade" exists and cannot be imported`。

**原因**：Helm chart 模板中包含 `namespace.yaml` 创建 namespace，但该 namespace 已被其他方式创建且没有 Helm 管理标签。

**修复**：在 `namespace.yaml` 中使用 `{{- if not (lookup "v1" "Namespace" "" .Values.namespace) }}` 条件渲染。

---

## 6. 端口冲突（出现 1 次）

### 6.1 proxy-agent control port 与目标服务端口冲突

**现象**：inventory 的 proxy-agent 持续 CrashLoopBackOff，`listen tcp :9090: bind: address already in use`。

**原因**：inventory 服务用 9090 端口（gRPC），proxy-agent 的 control API 也默认用 9090，两者在同一个 Pod 内冲突。

**修复**：在 `K8sProxyManager.deployProxy()` 中检测端口冲突，当 `targetPort == controlPort` 时自动使用 `controlPort + 1`。

---

## 7. RBAC 权限不足（出现 1 次）

### 7.1 ServiceAccount 无权创建 ChaosBlade CRD

**现象**：故障注入失败，`Failed to create ChaosBlade resource`。

**原因**：`chaosblade-space-exploration-sa` ServiceAccount 没有操作 `chaosblades.chaosblade.io` CRD 的权限。

**修复**：创建 ClusterRole + ClusterRoleBinding 授予 ChaosBlade CRD 的 CRUD 权限。

---

## 8. 容器缺少系统工具（出现 1 次）

### 8.1 fault-scheduler 容器内无 kubectl

**现象**：故障注入失败，`Failed to create resource with kubectl`。

**原因**：`eclipse-temurin:21-jre` 基础镜像不含 kubectl，但 `ChaosBladeApi` 通过 `ProcessBuilder` 调用 kubectl 创建 CRD。

**修复**：在 Dockerfile 中安装 kubectl：
```dockerfile
RUN apt-get update && apt-get install -y curl ca-certificates \
    && curl -LO "https://dl.k8s.io/release/v1.34.0/bin/linux/amd64/kubectl" \
    && chmod +x kubectl && mv kubectl /usr/local/bin/
```

---

## 9. 资源不足导致 OOM（出现 1 次）

### 9.1 svc-reqrsp-proxy OOMKilled

**现象**：执行过程中 proxy Pod 被 OOMKilled（Exit Code 137），executor 连接被拒。

**原因**：默认 512Mi 内存不够处理大量 proxy-agent snapshot 的拉取和入库。

**修复**：将 executor 和 proxy 的资源提升到 2 核 / 2Gi 内存。

---

## 10. 背景流量污染录制数据（出现 1 次）

### 10.1 load-engine 自动流量混入测试请求

**现象**：request_patterns 表中混入大量 load-engine 生成的请求（各种随机 sessionId、productId），snapshot 数量达 11000+，导致拉取超时。

**原因**：proxy-agent 在 record 模式下录制所有流量，不区分来源。

**修复**（两层过滤）：
1. **proxy-agent 侧**：新增 `recording-filter` API，设置 baggage filter 和 max_snapshots，只录制包含指定 baggage token 的请求
2. **svc-reqrsp-proxy 侧**：分析阶段按 baggage header 二次过滤录制数据

**效果**：snapshot 数量从 15000+ 降到 1087，过滤后仅 2 条有效数据。

---

## 11. Namespace 配置错误（出现 1 次）

### 11.1 system_key 被当作 K8s namespace

**现象**：executor 在 `ali-otel-demo` namespace 中找不到服务，持续 `No Service found`。

**原因**：`ExecutionOrchestrator` 第 97 行 `te.setNamespace(sys.getSystemKey())`，用了 `systemKey`（`ali-otel-demo`）而不是 `defaultEnvironment`（`cms-demo`）。

**修复**：将数据库中 `system_key` 改为实际 K8s namespace 名称（`cms-demo`）。

---

## 12. 废弃基础镜像（出现 2 次）

### 12.1 openjdk:8 / openjdk:21-jdk-slim 已从 Docker Hub 移除

**现象**：`docker buildx build` 报 `not found`。

**修复**：替换为 Eclipse Temurin：
- `openjdk:8` → `eclipse-temurin:8-jre`
- `openjdk:21-jdk-slim` → `eclipse-temurin:21-jre`

---

## 13. URL 编码缺失（出现 1 次）

### 13.1 Query Params 中的 JSON 未 URL 编码

**现象**：请求发送报 `Not enough variable values available to expand '"streetAddress"'`。

**原因**：`HttpRequestExecutor.buildFullUrl()` 拼接 query params 时未对值进行 URL 编码，JSON 中的 `{`、`"` 被 Spring WebClient 误解析为 URI 模板变量。

**修复**：
1. `buildFullUrl()` 中对 key/value 使用 `URLEncoder.encode(value, "UTF-8")`
2. `executeWebClientRequest()` 中 `.uri(url)` 改为 `.uri(URI.create(url))` 避免模板解析

---

## 14. NodePort 冲突（出现 1 次）

### 14.1 NodePort 30080 已被其他服务占用

**现象**：`helm install` 报 `provided port is already allocated`。

**原因**：cms-demo namespace 的 `frontend-proxy-nodeport` 已占用 30080。

**修复**：在 values.yaml 中将 `chaosbladeBoxFe.nodePort` 改为未占用的端口（如 30880）。

---

## 经验总结

| 类别 | 频率 | 预防措施 |
|------|------|---------|
| 镜像问题 | 最高（6次） | 禁用 latest，版本化标签，所有动态 Deployment 设 imagePullSecrets |
| Selector 残留 | 高（5次） | destroyProxy 内置完整清理链，实验后自动验证 Service selector |
| Schema 不一致 | 中（3次） | CI 校验实体类与 DDL 一致性，Flyway 迁移管理 |
| 配置映射 | 中（2次） | 统一使用 `${ENV_VAR:default}` 模板，测试环境变量覆盖 |
| K8s 兼容性 | 中（2次） | 定期升级 Fabric8 版本，兼容性检查 |
| 其他 | 各1次 | Code Review + 集成测试覆盖 |
