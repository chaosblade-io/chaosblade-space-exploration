# 风险分析Pipeline API接口文档

## 1. 接口概览

| 接口 | 方法 | 功能 | 响应时间 |
|------|------|------|----------|
| `/api/risk-analysis/pipeline/{namespace}` | GET | 启动异步分析 | <100ms |
| `/api/risk-analysis/pipeline/{namespace}/status` | GET | 查询执行状态 | <50ms |
| `/api/risk-analysis/pipeline/{namespace}/summary` | GET | 获取完整结果 | <100ms |
| `/api/risk-analysis/pipeline/{namespace}/history` | GET | 获取历史记录 | <50ms |
| `/api/risk-analysis/pipeline/{namespace}/sync` | GET | 同步执行（调试用） | 60-300s |

## 2. 接口详细说明

### 2.1 启动异步分析

**请求**
```
GET /api/risk-analysis/pipeline/{namespace}
```

**路径参数**
| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| namespace | String | 是 | Kubernetes命名空间名称 | train-ticket |

**响应 (200 OK)**
```json
{
  "executionId": "exec-train-ticket-1703952000000",
  "namespace": "train-ticket",
  "state": "PENDING",
  "currentPhase": 0,
  "progressPercent": 0,
  "startTime": "2024-12-30T15:20:00",
  "endTime": null,
  "elapsedTimeMs": 0,
  "phaseStatuses": [
    {"phaseNumber": 1, "phaseName": "规则扫描", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 2, "phaseName": "拓扑LLM分析", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 3, "phaseName": "RiskRank计算", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 4, "phaseName": "Trace深度分析", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 5, "phaseName": "综合分析与场景生成", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 6, "phaseName": "实验配置生成", "state": "PENDING", "durationMs": 0, "message": null}
  ],
  "errorMessage": null,
  "failedPhase": null,
  "result": null
}
```

**并发控制**
- 如果该namespace已有任务在执行（state=PENDING或RUNNING），直接返回当前执行状态
- 不会重复启动新任务

**状态说明**
| 状态 | 说明 |
|------|------|
| PENDING | 任务已创建，等待线程池分配 |
| RUNNING | 正在执行中 |
| COMPLETED | 执行成功完成 |
| FAILED | 执行失败 |

### 2.2 查询执行状态

**请求**
```
GET /api/risk-analysis/pipeline/{namespace}/status
```

**响应 (200 OK) - 执行中**
```json
{
  "executionId": "exec-train-ticket-1703952000000",
  "namespace": "train-ticket",
  "state": "RUNNING",
  "currentPhase": 4,
  "progressPercent": 66,
  "startTime": "2024-12-30T15:20:00",
  "endTime": null,
  "elapsedTimeMs": 95000,
  "phaseStatuses": [
    {"phaseNumber": 1, "phaseName": "规则扫描", "state": "COMPLETED", "durationMs": 5200, "message": "完成: 扫描了45个服务"},
    {"phaseNumber": 2, "phaseName": "拓扑LLM分析", "state": "COMPLETED", "durationMs": 32100, "message": "完成: 发现8个拓扑风险"},
    {"phaseNumber": 3, "phaseName": "RiskRank计算", "state": "COMPLETED", "durationMs": 450, "message": "完成: 排名了45个服务"},
    {"phaseNumber": 4, "phaseName": "Trace深度分析", "state": "RUNNING", "durationMs": 0, "message": "开始Trace分析(Top 3服务)..."},
    {"phaseNumber": 5, "phaseName": "综合分析与场景生成", "state": "PENDING", "durationMs": 0, "message": null},
    {"phaseNumber": 6, "phaseName": "实验配置生成", "state": "PENDING", "durationMs": 0, "message": null}
  ],
  "errorMessage": null,
  "failedPhase": null,
  "result": null
}
```

**响应 - 未执行过**
```json
{
  "executionId": null,
  "namespace": "unknown-namespace",
  "state": "PENDING",
  "currentPhase": 0,
  "progressPercent": 0,
  "startTime": null,
  "endTime": null,
  "elapsedTimeMs": 0,
  "phaseStatuses": [...],
  "errorMessage": null,
  "failedPhase": null,
  "result": null
}
```

### 2.3 获取完整结果

**请求**
```
GET /api/risk-analysis/pipeline/{namespace}/summary
```

**响应 (200 OK) - 执行完成**
```json
{
  "namespace": "train-ticket",
  "executionId": "exec-train-ticket-1703952000000",
  "state": "COMPLETED",
  "currentPhase": 6,
  "progressPercent": 100,
  "startTime": "2024-12-30T15:20:00",
  "endTime": "2024-12-30T15:23:28",
  "elapsedTimeMs": 208000,
  "phaseStatuses": [...],
  "result": {
    "namespace": "train-ticket",
    "success": true,
    "phase1Results": {...},
    "phase2Result": {...},
    "phase3Result": {...},
    "phase4Results": {...},
    "phase5Result": {
      "serviceAnalyses": {
        "ts-order-service": {
          "serviceName": "ts-order-service",
          "overallRiskLevel": "HIGH",
          "overallRiskScore": 78.5,
          "chaosScenarios": [
            {
              "scenarioId": "scenario-001",
              "name": "订单服务CPU满载测试",
              "code": "chaos.container-cpu.fullload",
              "faultName": "容器内Cpu满载",
              "faultParams": {"cpu-percent": "80"},
              "configGenerated": true,
              "experimentConfig": {...}
            }
          ]
        }
      }
    },
    "summary": {...}
  }
}
```

**响应 - 执行中**
```json
{
  "namespace": "train-ticket",
  "state": "RUNNING",
  "currentPhase": 4,
  "progressPercent": 66,
  "message": "Pipeline正在执行中，请稍后再试",
  "result": null
}
```

**响应 - 执行失败**
```json
{
  "namespace": "train-ticket",
  "state": "FAILED",
  "currentPhase": 2,
  "progressPercent": 33,
  "errorMessage": "LLM API调用超时",
  "failedPhase": 2,
  "result": null
}
```

### 2.4 获取历史记录

**请求**
```
GET /api/risk-analysis/pipeline/{namespace}/history
```

**响应 (200 OK)**
```json
[
  {
    "executionId": "exec-train-ticket-1703952000000",
    "namespace": "train-ticket",
    "state": "COMPLETED",
    "startTime": "2024-12-30T15:20:00",
    "endTime": "2024-12-30T15:23:28",
    "elapsedTimeMs": 208000,
    "result": {...}
  },
  {
    "executionId": "exec-train-ticket-1703865600000",
    "namespace": "train-ticket",
    "state": "FAILED",
    "startTime": "2024-12-29T15:20:00",
    "endTime": "2024-12-29T15:21:15",
    "elapsedTimeMs": 75000,
    "failedPhase": 2,
    "errorMessage": "LLM API rate limit exceeded"
  }
]
```

### 2.5 同步执行（调试用）

**请求**
```
GET /api/risk-analysis/pipeline/{namespace}/sync
```

**说明**
- 同步执行，请求会阻塞直到执行完成
- 适用于调试或小规模测试场景
- 不建议在生产环境使用

**响应 (200 OK)**
```json
{
  "namespace": "train-ticket",
  "success": true,
  "phase1TimeMs": 5200,
  "phase2TimeMs": 32100,
  "phase3TimeMs": 450,
  "phase4TimeMs": 85000,
  "phase5TimeMs": 45000,
  "phase6TimeMs": 8500,
  "totalTimeMs": 208000,
  "phase1Results": {...},
  "phase2Result": {...},
  "phase3Result": {...},
  "phase4Results": {...},
  "phase5Result": {...},
  "summary": {...}
}
```

## 3. 响应数据结构详解

### 3.1 PipelineResult 完整结构

```typescript
interface PipelineResult {
  namespace: string;           // 命名空间
  success: boolean;            // 是否成功
  errorMessage?: string;       // 错误信息
  executionTime: string;       // ISO 8601时间戳

  // 各阶段耗时(毫秒)
  phase1TimeMs: number;
  phase2TimeMs: number;
  phase3TimeMs: number;
  phase4TimeMs: number;
  phase5TimeMs: number;
  phase6TimeMs: number;
  totalTimeMs: number;

  // Phase 1: 规则扫描结果
  phase1Results: {
    [serviceName: string]: ServiceRiskProfile;
  };

  // Phase 2: 拓扑分析结果
  phase2Result: TopologyRiskResult;

  // Phase 3: 风险排名结果
  phase3Result: RiskRankResult;

  // Phase 4: Trace分析结果
  phase4Results: {
    [serviceName: string]: TraceAnalysisResult;
  };

  // Phase 5: 综合分析结果
  phase5Result: ComprehensiveAnalysisResult;

  // 执行摘要
  summary: PipelineSummary;
}

interface ServiceRiskProfile {
  serviceName: string;
  totalScore: number;          // 0-100
  riskLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  criticalInfra: boolean;
  triggeredRules: TriggeredRule[];
  categoryScores: {
    resourceConfig: number;
    availability: number;
    security: number;
    observability: number;
    resilience: number;
  };
}

interface TriggeredRule {
  ruleId: string;
  name: string;
  category: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  weight: number;
  description: string;
  recommendation: string;
}

interface RiskRankResult {
  rankedServices: RankedService[];
}

interface RankedService {
  rank: number;                // 排名，从1开始
  serviceName: string;
  riskRankScore: number;       // 0-100，综合风险分
  inherentRiskScore: number;   // 固有风险分
  propagatedRiskScore: number; // 传播风险分
  riskLevel: string;
}

interface ChaosScenario {
  scenarioId: string;
  name: string;                 // 场景名称
  code: string;                 // 故障类型代码，如 "chaos.container-cpu.fullload"
  faultName: string;            // 故障类型名称，如 "容器内Cpu满载"
  targetRiskId: string;         // 针对的风险ID
  type: ScenarioType;
  objective: string;            // 测试目标
  intensity: number;            // 1-5，故障强度
  durationSeconds: number;      // 建议执行时长
  steps: string[];              // 执行步骤
  expectedImpact: string;       // 预期影响
  successCriteria: string[];    // 成功标准
  rollbackPlan: string;         // 回滚方案
  faultParams: {                // 故障参数
    [key: string]: string;
  };
  configGenerated: boolean;     // 配置是否生成成功
  configError?: string;         // 配置生成失败原因
  experimentConfig?: ExperimentConfig;  // 可执行的实验配置
}

enum ScenarioType {
  POD_KILL, POD_FAILURE, CPU_STRESS, MEMORY_STRESS,
  NETWORK_DELAY, NETWORK_LOSS, NETWORK_PARTITION,
  DISK_FILL, IO_DELAY, JVM_EXCEPTION,
  HTTP_DELAY, HTTP_ERROR, DB_DELAY, TRAFFIC_SPIKE
}
```

### 3.2 ExperimentConfig 结构

```typescript
interface ExperimentConfig {
  name: string;              // 实验名称
  description: string;       // 实验描述
  namespace: string;         // 默认 "default"
  Lang: string;              // 默认 "zh"
  definition: {
    runMode: 'SEQUENCE';     // 执行模式
    duration: number;        // 执行时长(秒)
    flowGroups: FlowGroup[];
  };
}

interface FlowGroup {
  appName: string;           // 应用名称
  appId: string;             // ChaosBlade Box中的应用ID
  groupName: string;         // 分组名称
  hosts: Host[];             // 目标主机列表
  flows: Flow[];             // 故障流程
}

interface Host {
  hostId: string;
  deviceId: string;
  deviceType: number;
  ip: string;
  hostname: string;
  appId: string;
  appName: string;
  groupId: string;
  groupName: string;
}

interface Flow {
  flowId: string;
  attack: Attack[];          // 攻击动作
  check: any[];
  recover: Recover[];        // 恢复动作
  wait: any[];
}

interface Attack {
  activityName: string;      // 活动名称
  app_code: string;          // 故障类型代码
  arguments: Argument[];     // 故障参数
}

interface Argument {
  argumentName: string;      // 参数名
  value: string;             // 参数值
}
```

## 4. 错误码说明

| HTTP状态码 | 错误场景 | 响应示例 |
|------------|----------|----------|
| 400 | namespace参数为空 | 空响应体 |
| 404 | 资源不存在 | `{"error": "Namespace not found"}` |
| 500 | 服务器内部错误 | `{"error": "Internal server error"}` |
| 503 | 服务不可用 | `{"error": "LLM service unavailable"}` |

## 5. 使用场景说明

### 5.1 典型使用流程

```
1. 启动分析
   GET /api/risk-analysis/pipeline/train-ticket
   → 返回executionId和初始状态

2. 轮询状态（建议间隔5秒）
   GET /api/risk-analysis/pipeline/train-ticket/status
   → 检查state是否为COMPLETED或FAILED

3. 获取结果
   GET /api/risk-analysis/pipeline/train-ticket/summary
   → 获取完整的分析结果

4. （可选）查看历史
   GET /api/risk-analysis/pipeline/train-ticket/history
   → 对比历史分析结果
```

### 5.2 前端轮询示例

```javascript
async function waitForPipelineComplete(namespace) {
  const maxWait = 300000; // 5分钟
  const interval = 5000;  // 5秒
  const start = Date.now();

  while (Date.now() - start < maxWait) {
    const status = await fetch(`/api/risk-analysis/pipeline/${namespace}/status`)
      .then(r => r.json());

    if (status.state === 'COMPLETED') {
      return await fetch(`/api/risk-analysis/pipeline/${namespace}/summary`)
        .then(r => r.json());
    }

    if (status.state === 'FAILED') {
      throw new Error(`分析失败: ${status.errorMessage} (Phase ${status.failedPhase})`);
    }

    // 更新UI进度
    updateProgress(status.progressPercent, status.phaseStatuses);

    await new Promise(resolve => setTimeout(resolve, interval));
  }

  throw new Error('分析超时');
}
```

## 6. 集成指南

### 6.1 认证授权

当前版本暂无认证机制，建议：
- 内网部署时通过网络隔离保护
- 生产环境集成API Gateway进行认证

### 6.2 调用限制

| 限制项 | 值 | 说明 |
|--------|-----|------|
| 并发执行数 | 2 | 线程池核心线程数 |
| 队列容量 | 10 | 等待执行的任务数 |
| 历史记录保留 | 10条/namespace | 内存存储 |
| 单次分析超时 | 无限制 | 建议客户端设置5分钟 |


