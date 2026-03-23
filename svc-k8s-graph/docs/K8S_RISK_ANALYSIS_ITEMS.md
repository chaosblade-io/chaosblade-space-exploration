# K8s 风险分析项汇总

本文档汇总了 `svc-k8s-graph` 项目中集成的所有 K8s 风险分析项。

---

## 一、风险分析 Pipeline 概览

系统采用**六阶段风险分析流程**：

| 阶段 | 名称 | 输入 | 输出 | 核心服务 |
|------|------|------|------|----------|
| Phase 1 | 规则扫描 | K8s资源配置 | ServiceRiskProfile | `RiskRuleEngineService` |
| Phase 2 | 拓扑LLM分析 | 服务拓扑 + Phase1结果 | TopologyRiskResult | `TopologyRiskLLMService` |
| Phase 3 | RiskRank计算 | Phase1 + Phase2 + 拓扑 | RiskRankResult | `RiskRankAlgorithm` |
| Phase 4 | Trace深度分析 | Top N高风险服务 | TraceAnalysisResult | `TraceAnalysisService` |
| Phase 5 | 综合分析与场景生成 | 全部Phase结果 | ComprehensiveAnalysisResult | `ComprehensiveAnalysisService` |
| Phase 6 | 实验配置生成 | ChaosScenario | ExperimentConfig | `ExperimentConfigService` |

---

## 二、Phase 1: 规则扫描 - 内置风险规则

### 2.1 风险规则分类

| 分类 | 枚举值 | 说明 |
|------|--------|------|
| 可用性风险 | `AVAILABILITY` | 影响服务可用性的配置问题 |
| 资源风险 | `RESOURCE` | 资源配置不当可能导致资源耗尽或争用 |
| 稳定性风险 | `STABILITY` | 可能导致服务不稳定的配置 |
| 数据安全风险 | `DATA_SECURITY` | 数据持久化和安全相关风险 |
| 可扩展性风险 | `SCALABILITY` | 影响服务扩展能力的配置 |

### 2.2 内置规则列表

| 规则ID | 名称 | 分类 | 基础分值 | 严重等级 | 关键设施加分 | 描述 | 修复建议 |
|--------|------|------|----------|----------|--------------|------|----------|
| **AVAIL_001** | 单副本部署 | AVAILABILITY | 30 | HIGH | +20 | 服务仅有单个副本，任何故障都会导致服务完全不可用 | 建议至少配置2个副本以保证高可用 |
| **AVAIL_002** | 无就绪探针 | AVAILABILITY | 25 | HIGH | +15 | 未配置就绪探针，可能导致流量在服务未就绪时被转发 | 配置readinessProbe确保服务就绪后才接收流量 |
| **AVAIL_003** | 无存活探针 | AVAILABILITY | 20 | MEDIUM | +10 | 未配置存活探针，无法自动检测和重启异常容器 | 配置livenessProbe确保容器异常时自动重启 |
| **AVAIL_004** | 无PodDisruptionBudget | AVAILABILITY | 15 | MEDIUM | +10 | 多副本服务未配置PDB，节点维护时可能所有副本同时被驱逐 | 配置PodDisruptionBudget限制同时不可用的Pod数量 |
| **RES_001** | 无资源限制 | RESOURCE | 20 | MEDIUM | +10 | 未配置资源limits，可能耗尽节点资源影响其他服务 | 配置CPU和内存的limits限制资源使用上限 |
| **RES_002** | 无资源请求 | RESOURCE | 15 | LOW | +5 | 未配置资源requests，调度器无法合理分配资源 | 配置CPU和内存的requests确保资源预留 |
| **STAB_001** | 使用latest镜像标签 | STABILITY | 15 | MEDIUM | +0 | 使用latest或无标签镜像，版本不可控可能导致意外更新 | 使用明确的版本标签，避免latest |
| **SCALE_001** | 无自动扩缩容 | SCALABILITY | 10 | LOW | +0 | 未配置HPA，无法根据负载自动扩缩容 | 配置HorizontalPodAutoscaler实现自动扩缩容 |

### 2.3 关键基础设施识别

系统会自动识别以下关键基础设施服务，并对其风险分数进行加权：

```
mysql, postgres, mongodb, redis, kafka, rabbitmq, zookeeper,
elasticsearch, etcd, consul, vault, minio, database, db, mq, cache
```

### 2.4 扫描的K8s资源类型

- **Deployment** - 无状态应用部署
- **StatefulSet** - 有状态应用部署
- **PodDisruptionBudget** - Pod中断预算
- **HorizontalPodAutoscaler** - 水平自动扩缩容

---

## 三、Phase 2: 拓扑风险分析

### 3.1 拓扑风险类型

| 风险类型 | 枚举值 | 说明 |
|----------|--------|------|
| 单点故障风险 | `SINGLE_POINT` | 关键服务只有单一实例或无冗余 |
| 级联故障风险 | `CASCADING` | 故障可能沿调用链传播 |
| 瓶颈风险 | `BOTTLENECK` | 高入度服务成为性能瓶颈 |
| 循环依赖风险 | `CIRCULAR_DEPENDENCY` | 可能导致死锁或启动问题 |
| 爆炸半径风险 | `BLAST_RADIUS` | 单个服务故障影响大量下游 |
| 依赖集中风险 | `DEPENDENCY_CONCENTRATION` | 多个服务依赖同一个高风险服务 |

### 3.2 规则触发条件（LLM不可用时的回退规则）

| 规则 | 触发条件 | 风险分数 | 严重等级 |
|------|----------|----------|----------|
| 高入度瓶颈服务 | 入度 ≥ 5 | 25 | HIGH |
| 高风险依赖集中 | Phase1分数 ≥ 60 且 入度 ≥ 3 | 30 | CRITICAL |
| 高爆炸半径服务 | 出度 ≥ 5 | 20 | MEDIUM |

---

## 四、Phase 3: RiskRank 算法

### 4.1 算法原理

基于改良 PageRank 的风险传播算法：

1. 每个服务有**固有风险分**（来自 Phase1 + Phase2）
2. 风险沿调用链**向下游传播**
3. 高入度服务（被多个上游依赖）**累积更多传播风险**
4. **最终得分 = 固有风险 + 传播风险**

### 4.2 算法参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 阻尼因子 (dampingFactor) | 0.85 | 控制传播衰减 |
| 最大迭代次数 (maxIterations) | 100 | 迭代上限 |
| 收敛阈值 (convergenceThreshold) | 0.0001 | 判断收敛的阈值 |

### 4.3 风险等级划分

| 等级 | 分数范围 |
|------|----------|
| CRITICAL | ≥ 100 |
| HIGH | 60 - 99 |
| MEDIUM | 30 - 59 |
| LOW | 1 - 29 |
| NONE | 0 |

---

## 五、Phase 4: Trace 深度分析

### 5.1 分析内容

- 获取 Top N 高风险服务的 Trace 数据
- 分析 API 级别的统计信息（请求率、错误率、延迟分位数）
- 选择代表性 Trace 进行详细分析

### 5.2 Trace 选择策略

| 策略 | 说明 |
|------|------|
| max_latency | 选择延迟最大的正常 Trace |
| error_sample | 随机选择一个错误 Trace |

---

## 六、Phase 5: 综合分析与故障场景生成

### 6.1 支持的故障类型

| 故障名称 | 故障代码 | 类别 |
|----------|----------|------|
| 容器内Cpu满载 | `chaos.container-cpu.fullload` | CPU |
| 容器内Cpu占用过高 | `chaos.container-cpu.load` | CPU |
| 容器内Mem占用过高 | `chaos.container-mem.load` | 内存 |
| 容器内Mem使用量(oom) | `chaos.container-mem.oom` | 内存 |
| 容器内网络延迟 | `chaos.container-network.delay` | 网络 |
| 容器内网络丢包 | `chaos.container-network.loss` | 网络 |
| 容器内网络DNS | `chaos.container-network.dns` | 网络 |
| Pod删除 | `chaos.pod.delete` | Pod |
| Pod故障 | `chaos.pod.fail` | Pod |
| 容器内进程杀死 | `chaos.container-process.kill` | 进程 |
| 容器内进程停止 | `chaos.container-process.stop` | 进程 |

---

## 七、Phase 6: 实验配置生成

为每个 ChaosScenario 生成可执行的 ChaosBlade 实验配置，包括：

- 故障参数 (Fault Configuration)
- 影响范围 (Sphere of Influence)
- 通用配置 (General Configuration)

---

## 八、相关文件索引

| 文件路径 | 说明 |
|----------|------|
| `service/risk/RiskRuleEngineService.java` | Phase 1 规则引擎服务 |
| `service/risk/TopologyRiskLLMService.java` | Phase 2 拓扑风险LLM分析 |
| `service/risk/RiskRankAlgorithm.java` | Phase 3 RiskRank算法 |
| `service/risk/TraceAnalysisService.java` | Phase 4 Trace分析服务 |
| `service/risk/ComprehensiveAnalysisService.java` | Phase 5 综合分析服务 |
| `service/experiment/ExperimentConfigService.java` | Phase 6 实验配置生成 |
| `service/risk/RiskPipelineOrchestrator.java` | Pipeline编排服务 |
| `domain/risk/pipeline/RiskRule.java` | 风险规则定义 |
| `domain/risk/pipeline/RiskRuleCategory.java` | 风险规则分类枚举 |

