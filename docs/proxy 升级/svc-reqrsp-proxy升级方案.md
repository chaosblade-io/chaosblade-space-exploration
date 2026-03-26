# svc-reqrsp-proxy 整改方案

## 一、现状问题分析

### 1.1 当前架构的核心路径

```
每次录制/拦截操作 →
  1. Mustache 渲染 Envoy TAP/Route 配置
  2. K8s API: 创建 ConfigMap 挂载配置
  3. K8s API: Patch Deployment 注入 Envoy sidecar 容器
  4. K8s API: 重定向 Service 端口到 Envoy (15006)
  5. Envoy TAP Filter 捕获流量写入文件
  6. TapCollector 每 30s 收集 tap 数据
  7. 停止时: 恢复 Deployment/Service, 清理 ConfigMap
```

每次操作涉及 3 类 K8s 资源变更（ConfigMap + Deployment + Service），Deployment patch 会触发 Pod 重建，操作周期长、失败面广。

### 1.2 具体痛点

| 痛点 | 影响 | 严重度 |
|------|------|--------|
| Envoy sidecar 注入需要 patch Deployment，触发 Pod 重建 | 录制启动耗时 30s+，期间服务不可用 | 高 |
| Envoy 配置模板管理（Mustache .tmpl.yaml）复杂 | TAP/Route/Cluster 配置组合爆炸，维护困难 | 高 |
| 仅支持 HTTP/1.1，不支持 gRPC | 无法在 otel-demo（gRPC 调用）上使用 | 高 |
| TapCollector 定期采集（30s 间隔）有延迟 | 录制数据不实时，停止录制时可能丢数据 | 中 |
| Envoy sidecar 占用资源 | 每个被录制的 Pod 额外消耗 50-100MB 内存 | 中 |
| Service 端口重定向影响全局流量 | 录制期间所有流量都经过 Envoy，非测试请求也被影响 | 中 |
| 停止录制时恢复操作复杂 | Deployment/Service/ConfigMap 三个资源需要回滚，任一失败导致残留 | 中 |

### 1.3 需要保留的设计

当前架构中以下部分设计合理，应在整改中保留：

- **RecordingState / TaskStateManager** — Redis 状态管理模型成熟
- **REST API 的分层设计** — Recording / Interception / Hybrid / Fixture 的 API 分组清晰
- **ProxyClient 调用方式** — svc-task-executor 通过 HTTP 调用 proxy 的模式不变
- **MySQL 持久化 RequestPattern / HttpReqDef / Fixture** — 数据模型可复用
- **AuthenticationService** — 服务间认证逻辑保留

---

## 二、目标架构

### 2.1 核心变化：Envoy sidecar → 独立代理 Deployment + Endpoint 劫持

```
录制操作（新架构）→
  1. 部署独立的 Proxy Pod（Deployment，不是 sidecar）
  2. K8s API: 切换目标 Service 的 Endpoints → Proxy Pod IP
  3. Proxy Pod 内部直接完成流量录制/拦截/重放
  4. 停止时: 恢复 Endpoints, 删除 Proxy Deployment
```

K8s 操作从 3 类资源（ConfigMap + Deployment patch + Service 重定向）简化为 1 类（Endpoints 切换），且不触发目标 Pod 重建。

### 2.2 架构对比

```
┌─────────────────────────────────────────────────────┐
│  BEFORE (Envoy sidecar)                              │
│                                                       │
│  svc-reqrsp-proxy ──K8s API──→ Target Pod             │
│       │                        ┌──────────────┐      │
│       │ create ConfigMap       │ App Container │      │
│       │ patch Deployment       │ + Envoy       │      │
│       │ redirect Service       │   (injected)  │      │
│       │                        └──────┬───────┘      │
│       │ TapCollector ←── 30s poll ────┘              │
│                                                       │
├───────────────────────────────────────────────────────┤
│  AFTER (独立代理 + Endpoint 劫持)                      │
│                                                       │
│  svc-reqrsp-proxy ──K8s API──→ Endpoints 切换         │
│       │                                               │
│       │ deploy proxy pod       ┌───────────────┐     │
│       │ switch endpoints       │  Proxy Pod     │     │
│       │                        │  (独立部署)     │     │
│       │                        │  record/replay │     │
│       │ control API ←─────────→│  engine        │     │
│       │                        └───────┬───────┘     │
│       │                                │ forward     │
│       │                        ┌───────┴───────┐     │
│       │                        │  Target Pod    │     │
│       │                        │  (未被修改)     │     │
│       │                        └───────────────┘     │
│                                                       │
└───────────────────────────────────────────────────────┘
```

### 2.3 Proxy Pod 内部架构

Proxy Pod 是一个 Go 编写的轻量代理，核心组件：

| 组件 | 职责 | 替代的原组件 |
|------|------|-------------|
| cmux detector | 监听单端口，自动区分 HTTP/1.1 和 gRPC (HTTP/2) | 无（新增 gRPC 支持） |
| HTTP handler | 基于 httputil.ReverseProxy 的 HTTP 录制/拦截/重放 | Envoy TAP Filter + TapCollector |
| gRPC handler | 基于 UnknownServiceHandler 的 gRPC 透明代理 | 无（新增） |
| Record/Replay engine | 请求签名、快照存储、模式切换 | Envoy TAP 配置 + TapCollector 采集 |
| Snapshot store | 本地文件 + Redis 的快照存储 | Envoy TAP 输出文件 |
| Control API | HTTP 接口，接收 svc-reqrsp-proxy 的指令 | Envoy 配置热更新（ConfigMap） |

---

## 三、模块级整改计划

### 3.1 需要重写的模块

#### K8sTapManager → K8sProxyManager

**当前职责**：ConfigMap 创建、Deployment patch（注入 Envoy）、Service 端口重定向、资源清理

**整改为**：

```java
public class K8sProxyManager {

    // 部署独立的 Proxy Pod
    // 替代: createConfigMap + patchDeployment (注入sidecar)
    public ProxyInstance deployProxy(String namespace, String targetService,
                                     ProxyConfig config) {
        // 1. 创建 Proxy Deployment (使用预构建的 Go 代理镜像)
        // 2. 等待 Pod Ready
        // 3. 返回 ProxyInstance (包含 podIP, controlPort)
    }

    // Endpoint 劫持
    // 替代: redirectServicePort
    public EndpointBackup hijackEndpoints(String namespace, String serviceName,
                                          String proxyPodIP) {
        // 1. 读取并保存原始 Endpoints
        // 2. 将 Endpoints 切换到 proxyPodIP
        // 3. 返回 EndpointBackup (用于恢复)
    }

    // 恢复 Endpoints
    // 替代: restoreDeployment + restoreService + deleteConfigMap
    public void restoreEndpoints(EndpointBackup backup) {
        // 1. 恢复原始 Endpoints (单一操作)
    }

    // 清理 Proxy Pod
    public void destroyProxy(ProxyInstance instance) {
        // 1. 删除 Proxy Deployment
    }
}
```

**关键改进**：
- 整改前：3 类 K8s 资源变更，其中 Deployment patch 触发 Pod 重建
- 整改后：Endpoints 切换是即时生效的（kube-proxy 秒级更新 iptables/IPVS），不触发任何 Pod 重建

#### TemplateBasedTapConfigRenderer / HybridConfigRenderer → 删除

Envoy 配置模板全部删除，相关 Mustache .tmpl.yaml 文件删除。代理的行为通过 Control API 动态配置，不需要预渲染配置文件。

#### TapCollector → 删除

不再需要从 Pod 中定期采集 TAP 数据。Proxy Pod 实时将录制数据通过 Control API 推送回 svc-reqrsp-proxy，或直接写入共享存储。

### 3.2 需要适配的模块

#### RecordingService

```
当前流程:
  startRecording →
    renderTapConfig → createConfigMap → patchDeployment → redirectService
  stopRecording →
    restoreDeployment → restoreService → deleteConfigMap → collectFinalData

整改为:
  startRecording →
    deployProxy → hijackEndpoints → setProxyMode(RECORD)
  stopRecording →
    getRecordedData → restoreEndpoints → destroyProxy
```

核心逻辑（RecordingState 管理、Redis 状态持久化、recordingId 生成）不变。变化的只是底层操作从"注入 Envoy sidecar"变成"部署独立代理 + 切换 Endpoints"。

#### InterceptionService

```
当前流程:
  startInterception →
    检测已有录制 → 渲染拦截规则到 Envoy Route 配置 → 更新 ConfigMap

整改为:
  startInterception →
    检测已有代理 → 调用 Proxy Control API 添加拦截规则
```

"智能检测已有录制，决定新建还是追加规则"的逻辑保留，但下发规则的方式从"更新 Envoy ConfigMap"变为"调用 Proxy Pod 的 Control API"。

#### HybridService

混合模式（录制 + 拦截同时进行）的编排逻辑不变。底层操作对齐 RecordingService 和 InterceptionService 的整改。

#### ReplayService

```
当前流程:
  replay → 从录制数据中提取请求 → 直接向目标服务发送 HTTP 请求

整改为:
  replay →
    情况A (有代理在运行): 调用 Proxy Control API 设置重放模式
    情况B (无代理): 保持原有的直接请求方式
```

#### RequestPatternService

模式分析逻辑不变，但数据来源从"TapCollector 采集的 Envoy TAP 数据"变为"Proxy Pod 实时推送的录制数据"。

### 3.3 不需要改动的模块

| 模块 | 原因 |
|------|------|
| RecordingStateService | Redis 状态管理，与底层代理实现无关 |
| TaskStateManager | 异步任务状态管理，与底层代理实现无关 |
| AuthenticationService | 服务间认证，不变 |
| FixtureController / FixtureService | Fixture CRUD，数据模型不变 |
| 所有 JPA Repository | 数据库操作不变 |
| 所有 DTO | API 契约不变（除新增的 Proxy Control 相关 DTO） |

### 3.4 API 层面的变化

**对外 API（svc-task-executor 调用的）保持完全不变**：

```
POST /api/recordings/start        — 不变
POST /api/recordings/{id}/stop    — 不变
GET  /api/recordings/{id}         — 不变
GET  /api/recordings/{id}/entries — 不变
POST /api/interceptions/start     — 不变
POST /api/request-patterns/analyze — 不变
POST /api/replay                  — 不变
...
```

ProxyClient.java 零改动。所有变化封装在 svc-reqrsp-proxy 内部。

**新增内部 API（svc-reqrsp-proxy → Proxy Pod 的 Control API）**：

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/control/mode` | 设置代理模式（record/replay/intercept/passthrough） |
| POST | `/control/rules` | 添加拦截/重放规则 |
| DELETE | `/control/rules/{id}` | 移除规则 |
| GET | `/control/snapshots` | 获取录制的快照数据 |
| GET | `/control/health` | 健康检查 |
| GET | `/control/stats` | 运行统计（录制条数、匹配命中率等） |

---

## 四、新增组件：Go 轻量代理

### 4.1 项目结构

```
proxy-agent/
├── cmd/
│   └── proxy/main.go            # 入口，cmux 端口监听
├── internal/
│   ├── mux/detector.go           # HTTP/1.1 vs HTTP/2 协议检测
│   ├── http/handler.go           # HTTP 录制/拦截/重放
│   ├── grpc/handler.go           # gRPC 透明代理 + 录制/重放
│   ├── engine/
│   │   ├── engine.go             # 核心引擎：模式切换、请求匹配
│   │   ├── signature.go          # 请求签名计算
│   │   └── snapshot.go           # 快照存储（本地文件 + Redis）
│   ├── control/server.go         # Control API HTTP server
│   └── config/config.go          # 启动配置
├── Dockerfile
└── go.mod
```

### 4.2 核心接口设计

```go
// Engine 是录制/重放引擎的核心接口
type Engine interface {
    // 模式切换
    SetMode(mode Mode)
    GetMode() Mode

    // 录制: 存储请求-响应对
    Record(sig Signature, faultID string, resp *CapturedResponse) error

    // 重放: 根据签名和故障 ID 查找快照
    Replay(sig Signature, faultID string) (*CapturedResponse, bool)

    // 拦截: 根据规则返回 mock 响应
    Intercept(sig Signature, rules []InterceptRule) (*CapturedResponse, bool)

    // 获取所有快照
    GetSnapshots() []*Snapshot

    // 统计
    Stats() EngineStats
}

// Signature 是请求签名
type Signature struct {
    Hash   string // SHA256
    Method string // HTTP method 或 gRPC method
    Path   string // URL path 或 gRPC service/method
}

// CapturedResponse 统一 HTTP 和 gRPC 的响应
type CapturedResponse struct {
    Protocol    string            // "http" 或 "grpc"
    StatusCode  int               // HTTP status 或 gRPC status code
    Headers     map[string]string // HTTP headers 或 gRPC metadata
    Trailers    map[string]string // gRPC trailers (grpc-status, grpc-message)
    Body        []byte            // HTTP body 或 gRPC payload (原始字节)
    Latency     time.Duration     // 录制时的响应耗时，重放时用于模拟延迟
}
```

### 4.3 Docker 镜像

```dockerfile
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 go build -o /proxy-agent ./cmd/proxy

FROM alpine:3.19
COPY --from=builder /proxy-agent /usr/local/bin/proxy-agent
EXPOSE 8080 9090
ENTRYPOINT ["proxy-agent"]
```

镜像约 15-20MB（vs Envoy 镜像 ~60MB），启动时间 < 1s（vs Envoy 3-5s）。

---

## 五、K8s 资源操作对比

### 5.1 录制启动

| 步骤 | 当前（Envoy sidecar） | 整改后（独立代理） |
|------|----------------------|-------------------|
| 1 | 渲染 Envoy TAP 配置模板 | 构造 ProxyConfig 对象 |
| 2 | 创建 ConfigMap | 创建 Proxy Deployment |
| 3 | Patch 目标 Deployment（注入 sidecar） | 等待 Proxy Pod Ready |
| 4 | 重定向 Service 端口到 15006 | 切换 Endpoints 到 Proxy Pod IP |
| 5 | 等待 Pod 重建完成 | 调用 Control API 设置 RECORD 模式 |
| **耗时** | **30-60s**（Pod 重建） | **5-10s**（Deployment 创建 + Pod 启动） |
| **影响** | 目标服务中断（Pod 重建） | **目标服务零中断** |

### 5.2 录制停止

| 步骤 | 当前（Envoy sidecar） | 整改后（独立代理） |
|------|----------------------|-------------------|
| 1 | 最后一次 TapCollector 采集 | 调用 Control API 获取快照 |
| 2 | 恢复目标 Deployment（移除 sidecar） | 恢复 Endpoints |
| 3 | 恢复 Service 端口 | 删除 Proxy Deployment |
| 4 | 删除 ConfigMap | — |
| 5 | 等待 Pod 重建完成 | — |
| **耗时** | **30-60s** | **3-5s** |
| **风险** | 3 类资源恢复，任一失败导致残留 | 1 类资源恢复，清理简单 |

### 5.3 故障恢复

| 场景 | 当前 | 整改后 |
|------|------|--------|
| proxy 进程异常退出 | Envoy sidecar 留在 Pod 中，需手动清理 Deployment | Proxy Pod 被 K8s Deployment 自动重启；或直接删除 Proxy Deployment + 恢复 Endpoints |
| K8s API 调用失败（恢复阶段） | 需要回滚 Deployment + Service + ConfigMap | 只需恢复 Endpoints（单一资源） |
| 录制中目标 Pod 重启 | Envoy sidecar 随 Pod 重建，可能丢失未采集的 TAP 数据 | Proxy Pod 独立运行，不受影响；目标 Pod 重启后新 IP 需要更新 Endpoints |

---

## 六、gRPC 支持方案

### 6.1 当前状态

当前仅支持 HTTP/1.1，因为 Envoy TAP Filter 的配置是针对 HTTP 编写的。理论上 Envoy 支持 gRPC，但需要大量额外的 TAP 配置和数据解析逻辑。

### 6.2 整改后

Go 代理通过 cmux 在同一端口自动检测协议：

- HTTP/1.1 连接 → HTTP handler（httputil.ReverseProxy）
- HTTP/2 连接（gRPC）→ gRPC handler（grpc.UnknownServiceHandler）

gRPC handler 的核心设计：

```go
// 不需要任何 proto 定义，通用处理所有 gRPC 方法
func unknownServiceHandler(srv interface{}, stream grpc.ServerStream) error {
    method, _ := grpc.MethodFromServerStream(stream)
    // method = "/package.Service/Method"

    // 接收客户端请求 (原始字节)
    reqBytes := receiveRawFrame(stream)

    // 计算签名
    sig := computeSignature("grpc", method, stream.Context())

    switch engine.GetMode() {
    case RECORD:
        // 转发到真实后端，录制响应
        resp := forwardToBackend(method, reqBytes, stream)
        engine.Record(sig, currentFaultID, resp)
        return sendResponse(stream, resp)

    case REPLAY:
        // 查找快照，直接返回
        if cached, ok := engine.Replay(sig, currentFaultID); ok {
            time.Sleep(cached.Latency) // 模拟延迟
            return sendResponse(stream, cached)
        }
        // fallback: 转发到真实后端
        return forwardToBackend(method, reqBytes, stream)
    }
}
```

### 6.3 对两个测试系统的支持

| 测试系统 | 协议 | 当前支持 | 整改后 |
|----------|------|---------|--------|
| train-ticket | HTTP/1.1 (REST) | 支持 | 支持（HTTP handler） |
| otel-demo | gRPC + 部分 HTTP | 不支持 | 支持（cmux 自动检测） |

---

## 七、数据迁移

### 7.1 录制数据格式变化

**当前格式**（Envoy TAP 输出）：

```json
{
  "http_buffered_trace": {
    "request": {
      "headers": [...],
      "body": { "as_bytes": "..." }
    },
    "response": {
      "headers": [...],
      "body": { "as_bytes": "..." }
    }
  }
}
```

**整改后格式**（Proxy Snapshot）：

```json
{
  "signature": "sha256:abc123...",
  "fault_id": "svc-d:http-500",
  "protocol": "http",
  "request": {
    "method": "GET",
    "path": "/api/orders/123",
    "headers": { "content-type": "application/json" },
    "body_hash": "sha256:def456..."
  },
  "response": {
    "status_code": 500,
    "headers": { "content-type": "application/json" },
    "body": "<base64 encoded>",
    "latency_ms": 3200
  },
  "recorded_at": "2025-01-15T10:30:00Z"
}
```

### 7.2 MySQL 变更

**不变的表**：request_pattern, http_req_def, fixture

**新增的表**：

```sql
CREATE TABLE proxy_snapshot (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    recording_id  VARCHAR(64) NOT NULL,
    signature     VARCHAR(128) NOT NULL,
    fault_id      VARCHAR(128),
    protocol      VARCHAR(8) NOT NULL DEFAULT 'http',
    request_meta  JSON NOT NULL,
    response_meta JSON NOT NULL,
    response_body MEDIUMBLOB,
    latency_ms    INT NOT NULL DEFAULT 0,
    recorded_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recording_sig (recording_id, signature),
    INDEX idx_sig_fault (signature, fault_id)
);

CREATE TABLE proxy_instance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recording_id    VARCHAR(64) NOT NULL,
    namespace       VARCHAR(128) NOT NULL,
    target_service  VARCHAR(128) NOT NULL,
    proxy_pod_ip    VARCHAR(45),
    original_endpoints JSON,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ns_svc (namespace, target_service)
);
```