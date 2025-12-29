package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.domain.risk.*;
import com.chaosblade.svc.k8sgraph.service.RiskAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}

