package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.domain.risk.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.service.RiskAnalysisService;
import com.chaosblade.svc.k8sgraph.service.risk.RiskPipelineOrchestrator;
import com.chaosblade.svc.k8sgraph.service.risk.RiskRuleEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // ==================== Pipeline接口 ====================

    /**
     * 执行完整的风险分析Pipeline
     *
     * GET /api/risk-analysis/pipeline/{namespace}
     *
     * 执行三阶段风险分析：
     * - Phase 1: 规则扫描
     * - Phase 2: 拓扑LLM分析
     * - Phase 3: RiskRank计算
     */
    @GetMapping("/pipeline/{namespace}")
    public ResponseEntity<PipelineResult> executePipeline(@PathVariable String namespace) {
        logger.info("Received pipeline execution request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineResult result = riskPipelineOrchestrator.execute(namespace);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取Pipeline执行摘要（轻量级接口）
     *
     * GET /api/risk-analysis/pipeline/{namespace}/summary
     */
    @GetMapping("/pipeline/{namespace}/summary")
    public ResponseEntity<PipelineResult.PipelineSummary> getPipelineSummary(@PathVariable String namespace) {
        logger.info("Received pipeline summary request: namespace={}", namespace);

        if (namespace == null || namespace.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PipelineResult result = riskPipelineOrchestrator.execute(namespace);
        return ResponseEntity.ok(result.getSummary());
    }
}

