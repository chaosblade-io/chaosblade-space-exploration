# 风险分析Pipeline测试用例文档

## 1. 功能测试用例

### 1.1 异步执行流程测试

| 用例ID | 用例名称 | 前置条件 | 测试步骤 | 预期结果 |
|--------|----------|----------|----------|----------|
| FT-001 | 启动异步分析 | namespace存在且有服务 | 1. 调用 GET /pipeline/{namespace} | 返回200，state=PENDING，executionId非空 |
| FT-002 | 查询执行状态 | 已启动分析任务 | 1. 调用 GET /pipeline/{namespace}/status | 返回200，包含currentPhase和progressPercent |
| FT-003 | 获取完整结果 | 分析任务已完成 | 1. 调用 GET /pipeline/{namespace}/summary | 返回200，result非空，包含所有phase结果 |
| FT-004 | 并发控制 | 已有任务在执行 | 1. 再次调用 GET /pipeline/{namespace} | 返回当前执行状态，不启动新任务 |
| FT-005 | 历史记录查询 | 有历史执行记录 | 1. 调用 GET /pipeline/{namespace}/history | 返回历史记录列表，按时间倒序 |

### 1.2 六阶段Pipeline测试

| 用例ID | 用例名称 | 前置条件 | 测试步骤 | 预期结果 |
|--------|----------|----------|----------|----------|
| FT-101 | Phase 1规则扫描 | namespace有Deployment | 1. 执行Pipeline | phase1Results包含所有服务的风险评估 |
| FT-102 | Phase 2拓扑分析 | SkyWalking有ServiceMap | 1. 执行Pipeline | phase2Result包含拓扑风险列表 |
| FT-103 | Phase 3风险排名 | Phase1和Phase2完成 | 1. 执行Pipeline | phase3Result包含排序后的服务列表 |
| FT-104 | Phase 4 Trace分析 | SkyWalking有Trace数据 | 1. 执行Pipeline | phase4Results包含Top N服务的Trace分析 |
| FT-105 | Phase 5综合分析 | Phase1-4完成 | 1. 执行Pipeline | phase5Result包含综合分析和故障场景 |
| FT-106 | Phase 6配置生成 | Phase5生成了场景 | 1. 执行Pipeline | 每个场景包含experimentConfig |

### 1.3 规则引擎测试

| 用例ID | 用例名称 | 前置条件 | 测试步骤 | 预期结果 |
|--------|----------|----------|----------|----------|
| FT-201 | 单副本检测 | Deployment replicas=1 | 1. 执行规则扫描 | 触发"单点故障"规则，权重25 |
| FT-202 | 资源限制检测 | 未设置CPU/Memory Limit | 1. 执行规则扫描 | 触发"资源限制缺失"规则 |
| FT-203 | 健康检查检测 | 未配置liveness/readiness | 1. 执行规则扫描 | 触发"健康检查缺失"规则 |
| FT-204 | 安全配置检测 | runAsRoot=true | 1. 执行规则扫描 | 触发"以root运行"规则 |
| FT-205 | 多规则触发 | 多个配置问题 | 1. 执行规则扫描 | 所有适用规则都被触发，分数累加 |

### 1.4 RiskRank算法测试

| 用例ID | 用例名称 | 前置条件 | 测试步骤 | 预期结果 |
|--------|----------|----------|----------|----------|
| FT-301 | 基础排名 | 有多个服务 | 1. 执行RiskRank | 返回按风险分降序排列的服务列表 |
| FT-302 | 风险传播 | 服务A依赖高风险服务B | 1. 执行RiskRank | 服务A的传播风险分>0 |
| FT-303 | 核心服务识别 | 服务C被多个服务依赖 | 1. 执行RiskRank | 服务C排名靠前 |
| FT-304 | 收敛性 | 复杂拓扑 | 1. 执行RiskRank | 算法在20次迭代内收敛 |

### 1.5 实验配置生成测试

| 用例ID | 用例名称 | 前置条件 | 测试步骤 | 预期结果 |
|--------|----------|----------|----------|----------|
| FT-401 | CPU满载配置 | 场景code=chaos.container-cpu.fullload | 1. 生成配置 | 配置包含正确的app_code和参数 |
| FT-402 | 网络延迟配置 | 场景code=chaos.network.delay | 1. 生成配置 | 配置包含time和offset参数 |
| FT-403 | Pod删除配置 | 场景code=chaos.pod.delete | 1. 生成配置 | 配置包含正确的目标Pod信息 |
| FT-404 | 应用ID映射 | ChaosBlade Box有应用 | 1. 生成配置 | appId正确映射到ChaosBlade Box应用 |

## 2. 边界测试用例

| 用例ID | 用例名称 | 测试场景 | 预期结果 |
|--------|----------|----------|----------|
| BT-001 | 空namespace | namespace无任何Deployment | phase1Results为空Map，不报错 |
| BT-002 | 单服务namespace | 只有1个Deployment | 正常完成分析，拓扑分析显示无依赖 |
| BT-003 | 大规模namespace | 100+个Deployment | 正常完成，可能耗时较长 |
| BT-004 | 无SkyWalking数据 | SkyWalking无该namespace数据 | Phase2/4返回空结果，不影响其他阶段 |
| BT-005 | 无ChaosBlade应用 | ChaosBlade Box无对应应用 | Phase6配置生成失败，记录错误原因 |
| BT-006 | 特殊字符namespace | namespace含有特殊字符 | 正确处理，不报错 |
| BT-007 | 超长服务名 | 服务名超过255字符 | 正确处理，不截断 |

## 3. 异常测试用例

### 3.1 外部服务异常

| 用例ID | 用例名称 | 异常场景 | 预期结果 |
|--------|----------|----------|----------|
| ET-001 | K8s API不可用 | K8s API返回5xx | Phase1失败，记录错误，Pipeline终止 |
| ET-002 | K8s API超时 | K8s API响应超时 | Phase1失败，记录超时错误 |
| ET-003 | SkyWalking不可用 | SkyWalking OAP返回5xx | Phase2/4失败，记录错误 |
| ET-004 | LLM API不可用 | DeepSeek API返回5xx | Phase2/5失败，记录错误 |
| ET-005 | LLM API限流 | DeepSeek返回429 | 记录限流错误，建议稍后重试 |
| ET-006 | LLM返回无效JSON | LLM返回非JSON格式 | 解析失败，记录原始响应 |
| ET-007 | ChaosBlade不可用 | ChaosBlade Box返回5xx | Phase6失败，场景无配置 |

### 3.2 数据异常

| 用例ID | 用例名称 | 异常场景 | 预期结果 |
|--------|----------|----------|----------|
| ET-101 | 循环依赖 | 服务A→B→C→A | RiskRank正常收敛，不死循环 |
| ET-102 | 孤立服务 | 服务无任何依赖关系 | 正常处理，传播风险分为0 |
| ET-103 | 重复服务名 | 多个Deployment同名 | 合并处理或报告冲突 |
| ET-104 | 无效Trace数据 | Trace缺少关键字段 | 跳过无效Trace，继续处理 |

### 3.3 并发异常

| 用例ID | 用例名称 | 异常场景 | 预期结果 |
|--------|----------|----------|----------|
| ET-201 | 并发启动 | 同时发起多个相同namespace请求 | 只执行一个，其他返回当前状态 |
| ET-202 | 执行中查询 | 执行过程中频繁查询状态 | 状态查询不影响执行 |
| ET-203 | 线程池满 | 超过最大并发数 | 新任务进入队列等待 |
| ET-204 | 队列满 | 队列容量已满 | 返回服务繁忙错误 |

## 4. 性能测试用例

| 用例ID | 用例名称 | 测试场景 | 性能指标 |
|--------|----------|----------|----------|
| PT-001 | 小规模分析 | 10个服务的namespace | 总耗时<60秒 |
| PT-002 | 中规模分析 | 50个服务的namespace | 总耗时<180秒 |
| PT-003 | 大规模分析 | 100个服务的namespace | 总耗时<300秒 |
| PT-004 | 状态查询响应 | 执行中查询状态 | 响应时间<50ms |
| PT-005 | 结果查询响应 | 查询完成的结果 | 响应时间<100ms |
| PT-006 | 并发执行 | 同时分析3个不同namespace | 各自独立完成，无干扰 |
| PT-007 | 内存占用 | 分析100个服务 | 内存增量<500MB |

## 5. 集成测试用例

| 用例ID | 用例名称 | 测试场景 | 预期结果 |
|--------|----------|----------|----------|
| IT-001 | 端到端流程 | 完整执行6个阶段 | 所有阶段成功，生成可执行配置 |
| IT-002 | 配置可执行性 | 使用生成的配置执行实验 | ChaosBlade Box成功创建实验 |
| IT-003 | 结果一致性 | 相同输入多次执行 | 规则扫描结果一致，LLM结果相似 |
| IT-004 | 增量分析 | 服务配置变更后重新分析 | 正确反映配置变更 |

## 6. 测试数据准备

### 6.1 测试namespace配置

```yaml
# test-namespace-small (10个服务)
- ts-order-service (replicas=1, no limits)
- ts-travel-service (replicas=2, with limits)
- ts-station-service (replicas=1, no probes)
- ts-route-service (replicas=3, full config)
- ...

# test-namespace-medium (50个服务)
# 包含各种配置组合

# test-namespace-large (100个服务)
# 压力测试用
```

### 6.2 Mock数据

```json
// SkyWalking ServiceMap Mock
{
  "nodes": [
    {"id": "1", "name": "ts-order-service", "type": "Tomcat"},
    {"id": "2", "name": "ts-travel-service", "type": "Tomcat"}
  ],
  "calls": [
    {"source": "1", "target": "2", "avgResponseTime": 150}
  ]
}

// LLM Response Mock
{
  "risks": [
    {"riskId": "R001", "type": "SINGLE_POINT_FAILURE", "severity": "HIGH"}
  ],
  "chaosScenarios": [
    {"scenarioId": "S001", "code": "chaos.container-cpu.fullload"}
  ]
}
```
