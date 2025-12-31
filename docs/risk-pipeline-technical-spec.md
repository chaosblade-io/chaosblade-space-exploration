# 风险分析Pipeline技术实现文档

## 1. 系统架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ RiskAnalysisController                                               │   │
│  │  - GET /pipeline/{namespace}         启动异步分析                    │   │
│  │  - GET /pipeline/{namespace}/status  查询执行状态                    │   │
│  │  - GET /pipeline/{namespace}/summary 获取完整结果                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Service Layer                                      │
│  ┌──────────────────────┐    ┌──────────────────────────────────────────┐  │
│  │ AsyncPipelineService │───▶│ RiskPipelineOrchestrator                 │  │
│  │  - 并发控制           │    │  - Phase 1-6 编排                        │  │
│  │  - 状态追踪           │    │  - 状态回调                              │  │
│  │  - 历史记录           │    │  - 错误处理                              │  │
│  └──────────────────────┘    └──────────────────────────────────────────┘  │
│                                          │                                   │
│         ┌────────────────────────────────┼────────────────────────────┐     │
│         ▼                    ▼           ▼           ▼                ▼     │
│  ┌────────────┐  ┌────────────────┐  ┌────────┐  ┌──────────┐  ┌─────────┐ │
│  │RiskRule    │  │TopologyRisk    │  │RiskRank│  │Trace     │  │Compre-  │ │
│  │EngineService│  │LLMService     │  │Algorithm│  │Analysis  │  │hensive  │ │
│  │(Phase 1)   │  │(Phase 2)      │  │(Phase 3)│  │(Phase 4) │  │Analysis │ │
│  └────────────┘  └────────────────┘  └────────┘  └──────────┘  │(Phase5-6)│ │
│                                                                 └─────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Infrastructure Layer                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │ Kubernetes API  │  │ SkyWalking OAP  │  │ LLM API (DeepSeek/OpenAI)   │ │
│  │  - Deployment   │  │  - ServiceMap   │  │  - 拓扑分析                  │ │
│  │  - Pod          │  │  - Trace        │  │  - 综合分析                  │ │
│  │  - ConfigMap    │  │  - Metrics      │  │  - 场景生成                  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 六阶段Pipeline流程

| Phase | 名称 | 输入 | 输出 | 核心服务 |
|-------|------|------|------|----------|
| 1 | 规则扫描 | namespace | ServiceRiskProfile[] | RiskRuleEngineService |
| 2 | 拓扑LLM分析 | ServiceMap + Phase1结果 | TopologyRiskResult | TopologyRiskLLMService |
| 3 | RiskRank计算 | Phase1+2结果 + ServiceMap | RiskRankResult | RiskRankAlgorithm |
| 4 | Trace深度分析 | Top N服务 | TraceAnalysisResult[] | TraceAnalysisService |
| 5 | 综合分析 | Phase1-4所有结果 | ComprehensiveAnalysisResult | ComprehensiveAnalysisService |
| 6 | 配置生成 | ChaosScenario[] | ExperimentConfig[] | ExperimentConfigService |

### 1.3 数据流转图

```
namespace
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 1: 规则扫描                                                │
│   输入: namespace                                                │
│   处理: 遍历所有Deployment，应用30+条风险规则                      │
│   输出: Map<serviceName, ServiceRiskProfile>                     │
│         - 每个服务的风险分数                                      │
│         - 触发的规则列表                                          │
│         - 各分类分数（资源配置、可用性、安全性、可观测性）            │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 2: 拓扑LLM分析                                             │
│   输入: ServiceMap (节点+边) + Phase1结果                         │
│   处理: 调用LLM分析服务拓扑中的风险模式                            │
│   输出: TopologyRiskResult                                       │
│         - 拓扑风险列表（单点故障、级联风险、瓶颈等）                 │
│         - 服务拓扑风险分数                                        │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 3: RiskRank计算                                            │
│   输入: Phase1分数 + Phase2分数 + ServiceMap                      │
│   处理: 基于拓扑的PageRank变体算法                                 │
│   输出: RiskRankResult                                           │
│         - 服务风险排名（综合固有风险+传播风险）                     │
│         - Top N高风险服务                                         │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 4: Trace深度分析                                           │
│   输入: Top N服务名称                                             │
│   处理: 查询SkyWalking获取Trace数据，分析链路性能                   │
│   输出: Map<serviceName, TraceAnalysisResult>                    │
│         - API性能摘要                                             │
│         - 异常Trace采样                                           │
│         - 链路瓶颈分析                                            │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 5: 综合分析与场景生成                                       │
│   输入: Phase1-4所有结果                                          │
│   处理: 调用LLM进行多维度综合分析，生成故障场景                      │
│   输出: ComprehensiveAnalysisResult                              │
│         - 服务综合风险评估                                        │
│         - 风险因果链分析                                          │
│         - ChaosScenario[] 推荐的故障场景                          │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Phase 6: 实验配置生成                                            │
│   输入: ChaosScenario[]                                          │
│   处理: 查询ChaosBlade Box获取应用信息，生成可执行配置              │
│   输出: ExperimentConfig（嵌入到每个ChaosScenario中）              │
│         - 完整的ChaosBlade Box实验配置                            │
│         - 可直接提交执行                                          │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 核心算法说明

### 2.1 RiskRank算法

RiskRank是一个基于PageRank的风险传播算法，核心思想是：
- 高风险服务会将风险"传播"给依赖它的服务
- 被多个服务依赖的核心服务，即使自身风险低，也会获得较高的传播风险分

**算法公式：**
```
RiskRank(s) = (1-d) × InherentRisk(s) + d × Σ(RiskRank(t) / OutDegree(t))
              对所有指向s的服务t

其中：
- d = 0.85 (阻尼系数)
- InherentRisk(s) = Phase1分数 + Phase2分数（归一化到0-1）
- OutDegree(t) = 服务t的出边数量
```

**迭代过程：**
1. 初始化：所有服务的RiskRank = InherentRisk
2. 迭代：最多20次，或直到收敛（变化<0.001）
3. 输出：按RiskRank降序排列的服务列表

### 2.2 风险规则引擎

规则引擎采用声明式规则定义，支持以下规则类型：

| 规则类别 | 示例规则 | 权重 |
|----------|----------|------|
| 资源配置 | 未设置CPU/Memory Limit | 15 |
| 可用性 | replicas=1（单点故障） | 25 |
| 安全性 | 以root用户运行 | 20 |
| 可观测性 | 缺少健康检查探针 | 10 |
| 韧性 | 缺少PDB配置 | 15 |

**规则执行流程：**
```java
for (RiskRule rule : rules) {
    if (rule.matches(deployment)) {
        score += rule.getWeight();
        triggeredRules.add(rule);
    }
}
```

### 2.3 LLM集成方案

**调用架构：**
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ TopologyRisk    │     │ Comprehensive   │     │ DeepSeek API    │
│ LLMService      │────▶│ PromptBuilder   │────▶│ (or OpenAI)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                                               │
        │                                               ▼
        │                                       ┌─────────────────┐
        │                                       │ JSON Response   │
        │                                       │ Parser          │
        │                                       └─────────────────┘
        │                                               │
        ▼                                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 结构化输出：TopologyRiskResult / ComprehensiveAnalysisResult     │
└─────────────────────────────────────────────────────────────────┘
```

**Prompt设计原则：**
1. 提供完整的上下文（服务列表、依赖关系、风险规则结果）
2. 要求JSON格式输出，便于解析
3. 分步骤引导分析（先识别风险，再分析因果，最后生成场景）
4. 包含故障类型代码映射表，确保生成的场景可执行

## 3. 数据模型设计

### 3.1 核心数据结构

```
PipelineResult
├── namespace: String
├── success: boolean
├── phase1Results: Map<String, ServiceRiskProfile>
│   └── ServiceRiskProfile
│       ├── serviceName: String
│       ├── totalScore: int
│       ├── riskLevel: String (CRITICAL/HIGH/MEDIUM/LOW)
│       ├── triggeredRules: List<TriggeredRule>
│       └── categoryScores: Map<String, Integer>
├── phase2Result: TopologyRiskResult
│   ├── risks: List<TopologyRisk>
│   └── serviceTopologyScores: Map<String, Integer>
├── phase3Result: RiskRankResult
│   └── rankedServices: List<RankedService>
│       ├── rank: int
│       ├── serviceName: String
│       ├── riskRankScore: double
│       ├── inherentRiskScore: double
│       └── propagatedRiskScore: double
├── phase4Results: Map<String, TraceAnalysisResult>
│   └── TraceAnalysisResult
│       ├── apiSummaries: List<ApiSummary>
│       └── selectedTraces: List<TraceDetail>
├── phase5Result: ComprehensiveAnalysisResult
│   └── serviceAnalyses: Map<String, ServiceComprehensiveAnalysis>
│       ├── overallRiskLevel: RiskLevel
│       ├── topRisks: List<RiskWithCausality>
│       ├── causalChains: List<RiskCausalChain>
│       └── chaosScenarios: List<ChaosScenario>
│           ├── scenarioId: String
│           ├── name: String
│           ├── code: String (故障类型代码)
│           ├── faultParams: Map<String, String>
│           ├── experimentConfig: ExperimentConfig (Phase 6填充)
│           └── configGenerated: boolean
└── summary: PipelineSummary
```

### 3.2 执行状态数据结构

```
PipelineExecutionStatus
├── executionId: String
├── namespace: String
├── state: ExecutionState (PENDING/RUNNING/COMPLETED/FAILED)
├── currentPhase: int (1-6)
├── progressPercent: int (0-100)
├── startTime: String (ISO 8601)
├── endTime: String
├── elapsedTimeMs: long
├── phaseStatuses: List<PhaseStatus>
│   └── PhaseStatus
│       ├── phaseNumber: int
│       ├── phaseName: String
│       ├── state: ExecutionState
│       ├── startTimeMs: long
│       ├── durationMs: long
│       └── message: String
├── errorMessage: String
├── failedPhase: Integer
└── result: PipelineResult (执行完成后填充)
```

## 4. 技术栈选型

| 组件 | 技术选型 | 版本 | 用途 |
|------|----------|------|------|
| 后端框架 | Spring Boot | 2.7.x | Web服务、依赖注入、配置管理 |
| 异步处理 | Spring @Async | - | Pipeline异步执行 |
| 线程池 | ThreadPoolTaskExecutor | - | 控制并发执行数量 |
| K8s客户端 | Fabric8 Kubernetes Client | 6.x | 访问K8s API |
| 可观测性 | SkyWalking OAP | 9.x | 获取ServiceMap、Trace数据 |
| LLM API | DeepSeek / OpenAI | - | 拓扑分析、综合分析 |
| 混沌工程 | ChaosBlade Box | 1.x | 故障注入执行 |
| HTTP客户端 | RestTemplate / WebClient | - | 调用外部API |
| JSON处理 | Jackson | 2.x | JSON序列化/反序列化 |

## 5. 性能优化策略

### 5.1 当前性能瓶颈分析

| 阶段 | 典型耗时 | 瓶颈原因 |
|------|----------|----------|
| Phase 1 | 5-10s | K8s API调用次数多（每个Deployment一次） |
| Phase 2 | 30-60s | LLM API响应慢 |
| Phase 3 | <1s | 纯计算，无瓶颈 |
| Phase 4 | 60-120s | 多次SkyWalking API调用（每个服务多次） |
| Phase 5 | 30-60s | LLM API响应慢 |
| Phase 6 | 5-10s | ChaosBlade Box API调用 |

### 5.2 已实施的优化

1. **异步执行**: 用户无需等待完整执行，可轮询状态
2. **并发控制**: 同一namespace不重复执行，避免资源浪费
3. **状态追踪**: 实时更新进度，用户可了解执行情况

### 5.3 未来优化方向

1. **Phase 1 批量查询**: 使用K8s List API一次获取所有Deployment
2. **Phase 2/5 LLM缓存**: 对相似拓扑的分析结果进行缓存
3. **Phase 4 并行查询**: 对多个服务的Trace查询并行执行
4. **增量分析**: 只分析发生变化的服务，复用历史结果

## 6. 技术实现不足分析

### 6.1 性能方面

| 不足 | 影响 | 改进建议 |
|------|------|----------|
| LLM调用无缓存 | 重复分析相同拓扑浪费资源 | 实现基于拓扑哈希的缓存 |
| SkyWalking查询串行 | Phase 4耗时过长 | 改为并行查询 |
| 无请求超时控制 | LLM卡死导致整体超时 | 添加单独的超时控制 |
| 无分页处理 | 大规模服务时内存压力大 | 实现分批处理 |

### 6.2 可靠性方面

| 不足 | 影响 | 改进建议 |
|------|------|----------|
| 无重试机制 | 临时故障导致整体失败 | 添加带退避的重试逻辑 |
| 状态仅存内存 | 重启后丢失执行状态 | 持久化到Redis/数据库 |
| 无事务支持 | 部分失败难以回滚 | 实现Saga模式 |
| LLM返回校验弱 | 解析失败导致空结果 | 增强JSON Schema校验 |

### 6.3 可扩展性方面

| 不足 | 影响 | 改进建议 |
|------|------|----------|
| 规则硬编码 | 新规则需改代码 | 支持YAML/DB配置规则 |
| 单实例执行 | 无法水平扩展 | 引入分布式锁+任务队列 |
| 故障类型固定 | 新故障需改代码 | 动态加载故障类型定义 |

### 6.4 监控运维方面

| 不足 | 影响 | 改进建议 |
|------|------|----------|
| 无Metrics暴露 | 无法监控执行情况 | 集成Micrometer暴露指标 |
| 日志不够结构化 | 问题定位困难 | 使用MDC添加traceId |
| 无告警机制 | 故障发现不及时 | 集成告警通知 |
| 无执行统计 | 无法优化性能 | 记录各阶段耗时统计 |

### 6.5 用户体验方面

| 不足 | 影响 | 改进建议 |
|------|------|----------|
| 无WebSocket推送 | 需轮询获取状态 | 实现SSE/WebSocket推送 |
| 结果展示不直观 | 难以理解风险 | 提供可视化图表 |
| 无历史对比 | 无法追踪改进 | 实现历史结果对比功能 |
| 无导出功能 | 难以分享结果 | 支持PDF/Excel导出 |

