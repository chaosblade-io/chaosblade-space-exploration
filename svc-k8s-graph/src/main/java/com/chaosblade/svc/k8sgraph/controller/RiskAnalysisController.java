package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.domain.risk.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.service.ChaosBladeLabelerService;
import com.chaosblade.svc.k8sgraph.service.ChaosBladeLabelerService.LabelingResult;
import com.chaosblade.svc.k8sgraph.service.ChaosBladeLabelerService.DeploymentLabelStatus;
import com.chaosblade.svc.k8sgraph.service.RiskAnalysisService;
import com.chaosblade.svc.k8sgraph.service.risk.AsyncPipelineService;
import com.chaosblade.svc.k8sgraph.service.risk.RiskPipelineOrchestrator;
import com.chaosblade.svc.k8sgraph.service.risk.RiskRuleEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险分析 API Controller
 * 提供基于 AI 的 K8s 资源和拓扑风险分析接口
 */
@RestController
@RequestMapping("/api/risk-analysis")
public class RiskAnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(RiskAnalysisController.class);
    
    @Autowired
    private RiskAnalysisService riskAnalysisService;

    @Autowired
    private RiskRuleEngineService riskRuleEngineService;

    @Autowired
    private RiskPipelineOrchestrator riskPipelineOrchestrator;

    @Autowired
    private AsyncPipelineService asyncPipelineService;

    @Autowired
    private ChaosBladeLabelerService chaosBladeLabelerService;

    /**
     * 分析 K8s 资源配置风险
     * 
     * POST /api/risk-analysis/resource
     * {
     *   "resourceType": "deployment",
     *   "resourceName": "my-app",
     *   "namespace": "default"
     * }
     */
    @PostMapping("/resource")
    public ResponseEntity<RiskAnalysisResponse> analyzeResourceRisk(
            @RequestBody ResourceRiskRequest request) {
        logger.info("Received resource risk analysis request: type={}, name={}, namespace={}",
            request.getResourceType(), request.getResourceName(), request.getNamespace());
        
        if (request.getResourceType() == null || request.getResourceName() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        RiskAnalysisResponse response = riskAnalysisService.analyzeResourceRisk(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 分析 K8s 拓扑风险
     * 
     * POST /api/risk-analysis/topology
     * {
     *   "namespace": "default"
     * }
     */
    @PostMapping("/topology")
    public ResponseEntity<RiskAnalysisResponse> analyzeTopologyRisk(
            @RequestBody TopologyRiskRequest request) {
        logger.info("Received topology risk analysis request: namespace={}", request.getNamespace());
        
        if (request.getNamespace() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        RiskAnalysisResponse response = riskAnalysisService.analyzeTopologyRisk(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 分析服务拓扑风险（基于可观测性数据）
     * 
     * POST /api/risk-analysis/service-topology
     * {
     *   "namespace": "default"
     * }
     */
    @PostMapping("/service-topology")
    public ResponseEntity<RiskAnalysisResponse> analyzeServiceTopologyRisk(
            @RequestBody TopologyRiskRequest request) {
        logger.info("Received service topology risk analysis request: namespace={}", 
            request.getNamespace());
        
        if (request.getNamespace() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        RiskAnalysisResponse response = riskAnalysisService.analyzeServiceTopologyRisk(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 分析链路风险
     * 
     * POST /api/risk-analysis/trace
     * {
     *   "traceId": "abc123..."
     * }
     */
    @PostMapping("/trace")
    public ResponseEntity<RiskAnalysisResponse> analyzeTraceRisk(
            @RequestBody TraceRiskRequest request) {
        logger.info("Received trace risk analysis request: traceId={}", request.getTraceId());
        
        if (request.getTraceId() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        RiskAnalysisResponse response = riskAnalysisService.analyzeTraceRisk(request);
        return ResponseEntity.ok(response);
    }

    // ==================== Phase 1: 规则引擎扫描接口 ====================

    /**
     * 扫描命名空间下所有服务的K8s配置风险
     *
     * GET /api/risk-analysis/rule-scan/namespace/{namespace}
     *
     * 返回该命名空间下所有服务的风险画像，包括：
     * - 按风险分数排名的服务列表
     * - 每个服务触发的规则详情
     * - 汇总统计信息
     */
    @GetMapping("/rule-scan/namespace/{namespace}")
    public ResponseEntity<NamespaceScanResult> scanNamespace(@PathVariable String namespace) {
        logger.info("Received namespace risk scan request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        NamespaceScanResult result = riskRuleEngineService.scanNamespace(namespace);
        return ResponseEntity.ok(result);
    }

    /**
     * 扫描单个服务的K8s配置风险
     *
     * GET /api/risk-analysis/rule-scan/service/{namespace}/{serviceName}
     *
     * 返回指定服务的风险画像，包括：
     * - 总风险分数
     * - 各分类的分数
     * - 触发的规则详情
     * - 关联的K8s资源
     */
    @GetMapping("/rule-scan/service/{namespace}/{serviceName}")
    public ResponseEntity<ServiceRiskProfile> scanService(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        logger.info("Received service risk scan request: namespace={}, service={}", namespace, serviceName);

        if (namespace == null || serviceName == null) {
            return ResponseEntity.badRequest().build();
        }

        ServiceRiskProfile result = riskRuleEngineService.scanService(serviceName, namespace);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有可用的风险规则
     *
     * GET /api/risk-analysis/rules
     */
    @GetMapping("/rules")
    public ResponseEntity<List<RiskRule>> getRules() {
        logger.info("Received get rules request");
        List<RiskRule> rules = riskRuleEngineService.getRules();
        return ResponseEntity.ok(rules);
    }

    // ==================== Pipeline接口（异步模式） ====================

    /**
     * 启动风险分析Pipeline（异步）
     *
     * GET /api/risk-analysis/pipeline/{namespace}
     *
     * 立即返回执行状态，Pipeline在后台异步执行。
     * 如果该namespace已有任务在执行，直接返回当前执行状态。
     *
     * 执行六阶段风险分析：
     * - Phase 1: 规则扫描
     * - Phase 2: 拓扑LLM分析
     * - Phase 3: RiskRank计算
     * - Phase 4: Trace深度分析
     * - Phase 5: 综合分析与故障场景生成
     * - Phase 6: 实验配置生成
     */
    @GetMapping("/pipeline/{namespace}")
    public ResponseEntity<PipelineExecutionStatus> executePipeline(@PathVariable String namespace) {
        logger.info("Received async pipeline execution request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineExecutionStatus status = asyncPipelineService.startPipeline(namespace);
        return ResponseEntity.ok(status);
    }

    /**
     * 获取Pipeline执行状态
     *
     * GET /api/risk-analysis/pipeline/{namespace}/status
     *
     * 返回当前执行进度、各Phase状态、错误信息等
     */
    @GetMapping("/pipeline/{namespace}/status")
    public ResponseEntity<PipelineExecutionStatus> getPipelineStatus(@PathVariable String namespace) {
        logger.info("Received pipeline status request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineExecutionStatus status = asyncPipelineService.getStatus(namespace);
        if (status == null) {
            // 如果没有执行记录，返回默认状态
            Map<String, Object> response = new HashMap<>();
            response.put("namespace", namespace);
            response.put("state", "NOT_STARTED");
            response.put("message", "该namespace尚未执行过Pipeline分析");
            return ResponseEntity.ok(new PipelineExecutionStatus(null, namespace));
        }
        return ResponseEntity.ok(status);
    }

    /**
     * 获取Pipeline完整结果
     *
     * GET /api/risk-analysis/pipeline/{namespace}/summary
     *
     * 如果Pipeline已完成，返回完整的PipelineResult。
     * 如果Pipeline未完成或未执行，返回当前执行状态和默认结构。
     */
    @GetMapping("/pipeline/{namespace}/summary")
    public ResponseEntity<Map<String, Object>> getPipelineSummary(@PathVariable String namespace) {
        logger.info("Received pipeline summary request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineExecutionStatus status = asyncPipelineService.getStatus(namespace);
        Map<String, Object> response = new HashMap<>();
        response.put("namespace", namespace);

        if (status == null) {
            response.put("state", "NOT_STARTED");
            response.put("message", "该namespace尚未执行过Pipeline分析");
            response.put("result", null);
            return ResponseEntity.ok(response);
        }

        response.put("executionId", status.getExecutionId());
        response.put("state", status.getState().name());
        response.put("currentPhase", status.getCurrentPhase());
        response.put("progressPercent", status.getProgressPercent());
        response.put("startTime", status.getStartTime());
        response.put("endTime", status.getEndTime());
        response.put("elapsedTimeMs", status.getElapsedTimeMs());
        response.put("phaseStatuses", status.getPhaseStatuses());

        if (status.getState() == PipelineExecutionStatus.ExecutionState.COMPLETED) {
            response.put("result", status.getResult());
        } else if (status.getState() == PipelineExecutionStatus.ExecutionState.FAILED) {
            response.put("errorMessage", status.getErrorMessage());
            response.put("failedPhase", status.getFailedPhase());
            response.put("result", null);
        } else {
            response.put("message", "Pipeline正在执行中，请稍后再试");
            response.put("result", null);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取Pipeline历史执行记录
     *
     * GET /api/risk-analysis/pipeline/{namespace}/history
     */
    @GetMapping("/pipeline/{namespace}/history")
    public ResponseEntity<List<PipelineExecutionStatus>> getPipelineHistory(@PathVariable String namespace) {
        logger.info("Received pipeline history request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<PipelineExecutionStatus> history = asyncPipelineService.getHistory(namespace);
        return ResponseEntity.ok(history);
    }

    /**
     * 同步执行Pipeline（用于调试或小规模测试）
     *
     * GET /api/risk-analysis/pipeline/{namespace}/sync
     */
    @GetMapping("/pipeline/{namespace}/sync")
    public ResponseEntity<PipelineResult> executePipelineSync(@PathVariable String namespace) {
        logger.info("Received sync pipeline execution request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineResult result = riskPipelineOrchestrator.execute(namespace);
        return ResponseEntity.ok(result);
    }

    // ==================== ChaosBlade标签管理接口 ====================

    /**
     * 给命名空间下所有Deployment打上ChaosBlade标签
     *
     * POST /api/risk-analysis/chaosblade/label/{namespace}
     * 可选参数: appGroup (默认使用命名空间名称)
     */
    @PostMapping("/chaosblade/label/{namespace}")
    public ResponseEntity<LabelingResult> labelNamespaceForChaosBlade(
            @PathVariable String namespace,
            @RequestParam(required = false) String appGroup) {
        logger.info("Received ChaosBlade labeling request: namespace={}, appGroup={}", namespace, appGroup);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 如果没有指定appGroup，使用namespace作为默认值
        String effectiveAppGroup = (appGroup != null && !appGroup.trim().isEmpty()) ? appGroup : namespace;

        LabelingResult result = chaosBladeLabelerService.labelNamespace(namespace, effectiveAppGroup);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取命名空间下所有Deployment的ChaosBlade标签状态
     *
     * GET /api/risk-analysis/chaosblade/label/{namespace}/status
     */
    @GetMapping("/chaosblade/label/{namespace}/status")
    public ResponseEntity<java.util.List<DeploymentLabelStatus>> getLabelStatus(@PathVariable String namespace) {
        logger.info("Received ChaosBlade label status request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        java.util.List<DeploymentLabelStatus> statusList = chaosBladeLabelerService.getLabelStatus(namespace);
        return ResponseEntity.ok(statusList);
    }
}

