package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.client.LlmClient;
import com.chaosblade.svc.k8sgraph.client.ObservabilityApiClient;
import com.chaosblade.svc.k8sgraph.config.RiskAnalysisProperties;
import com.chaosblade.svc.k8sgraph.domain.GraphData;
import com.chaosblade.svc.k8sgraph.domain.ResourceDetail;
import com.chaosblade.svc.k8sgraph.domain.risk.*;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 风险分析服务 - 使用 AI 分析 K8s 资源和拓扑的风险点
 */
@Service
public class RiskAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAnalysisService.class);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private K8sGraphService k8sGraphService;

    @Autowired
    private K8sResourceDetailService k8sResourceDetailService;

    @Autowired
    private ServiceMapService serviceMapService;

    @Autowired
    private ObservabilityApiClient observabilityApiClient;

    @Autowired
    private RiskAnalysisPromptBuilder promptBuilder;

    @Autowired
    private RiskAnalysisProperties riskAnalysisProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 分析 K8s 资源配置风险
     */
    public RiskAnalysisResponse analyzeResourceRisk(ResourceRiskRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("Analyzing resource risk: type={}, name={}, namespace={}",
            request.getResourceType(), request.getResourceName(), request.getNamespace());

        RiskAnalysisResponse response = RiskAnalysisResponse.create("resource",
            request.getResourceType() + "/" + request.getResourceName());

        try {
            // 获取资源详情
            ResourceDetail detail = k8sResourceDetailService.getResourceDetail(
                request.getResourceType(), request.getResourceName(), request.getNamespace());

            // 构建 Prompt
            String prompt = promptBuilder.buildResourceRiskPrompt(
                request.getResourceType(), request.getResourceName(),
                request.getNamespace(), detail);

            // 设置调试信息
            setDebugPromptIfEnabled(response, prompt);

            // 调用 LLM
            String aiResponse = llmClient.chat(prompt, RiskAnalysisPromptBuilder.SYSTEM_PROMPT);

            // 解析响应
            if (aiResponse != null) {
                parseAndAddRisks(response, aiResponse, request.getResourceType(),
                    request.getResourceName(), request.getNamespace());
            }

        } catch (Exception e) {
            logger.error("Failed to analyze resource risk: {}", e.getMessage(), e);
        }

        response.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        response.calculateSummary();
        return response;
    }
    
    /**
     * 分析 K8s 拓扑风险
     */
    public RiskAnalysisResponse analyzeTopologyRisk(TopologyRiskRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("Analyzing topology risk for namespace: {}", request.getNamespace());

        RiskAnalysisResponse response = RiskAnalysisResponse.create("topology", request.getNamespace());

        try {
            // 获取命名空间拓扑
            GraphData graphData = k8sGraphService.getGraphByNamespace(request.getNamespace());

            // 构建 Prompt
            String prompt = promptBuilder.buildTopologyRiskPrompt(request.getNamespace(), graphData);

            // 设置调试信息
            setDebugPromptIfEnabled(response, prompt);

            // 调用 LLM
            String aiResponse = llmClient.chat(prompt, RiskAnalysisPromptBuilder.SYSTEM_PROMPT);

            // 解析响应
            if (aiResponse != null) {
                parseAndAddRisks(response, aiResponse, null, null, request.getNamespace());
            }

        } catch (Exception e) {
            logger.error("Failed to analyze topology risk: {}", e.getMessage(), e);
        }

        response.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        response.calculateSummary();
        return response;
    }
    
    /**
     * 分析服务拓扑风险
     */
    public RiskAnalysisResponse analyzeServiceTopologyRisk(TopologyRiskRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("Analyzing service topology risk for namespace: {}", request.getNamespace());

        RiskAnalysisResponse response = RiskAnalysisResponse.create("service-topology", request.getNamespace());

        try {
            // 获取服务调用拓扑（最近1小时）
            long toMs = System.currentTimeMillis();
            long fromMs = toMs - 3600_000;
            ServiceMapData serviceMap = serviceMapService.getServiceMap(fromMs, toMs);

            // 构建 Prompt
            String prompt = promptBuilder.buildServiceTopologyRiskPrompt(request.getNamespace(), serviceMap);

            // 设置调试信息
            setDebugPromptIfEnabled(response, prompt);

            // 调用 LLM
            String aiResponse = llmClient.chat(prompt, RiskAnalysisPromptBuilder.SYSTEM_PROMPT);

            // 解析响应
            if (aiResponse != null) {
                parseAndAddRisks(response, aiResponse, null, null, request.getNamespace());
            }

        } catch (Exception e) {
            logger.error("Failed to analyze service topology risk: {}", e.getMessage(), e);
        }

        response.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        response.calculateSummary();
        return response;
    }

    /**
     * 分析链路风险
     */
    public RiskAnalysisResponse analyzeTraceRisk(TraceRiskRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("Analyzing trace risk for traceId: {}", request.getTraceId());

        RiskAnalysisResponse response = RiskAnalysisResponse.create("trace", request.getTraceId());

        try {
            // 获取 Trace 数据
            JsonNode traceData = observabilityApiClient.getTraceDetail(request.getTraceId());

            // 构建 Prompt
            String prompt = promptBuilder.buildTraceRiskPrompt(request.getTraceId(), traceData);

            // 设置调试信息
            setDebugPromptIfEnabled(response, prompt);

            // 调用 LLM
            String aiResponse = llmClient.chat(prompt, RiskAnalysisPromptBuilder.SYSTEM_PROMPT);

            // 解析响应
            if (aiResponse != null) {
                parseAndAddRisks(response, aiResponse, null, null, null);
            }

        } catch (Exception e) {
            logger.error("Failed to analyze trace risk: {}", e.getMessage(), e);
        }

        response.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        response.calculateSummary();
        return response;
    }

    /**
     * 解析 AI 响应并添加风险项
     */
    private void parseAndAddRisks(RiskAnalysisResponse response, String aiResponse,
                                   String resourceType, String resourceName, String namespace) {
        try {
            // 提取 JSON
            String json = extractJson(aiResponse);
            if (json == null) {
                logger.warn("No valid JSON found in AI response");
                return;
            }

            // 解析 JSON
            JsonNode root = objectMapper.readTree(json);
            JsonNode risksNode = root.path("risks");

            if (risksNode.isArray()) {
                for (JsonNode riskNode : risksNode) {
                    RiskAnalysisResult risk = parseRiskResult(riskNode, resourceType, resourceName, namespace);
                    if (risk != null) {
                        response.addRisk(risk);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse AI response: {}", e.getMessage());
        }
    }

    private String extractJson(String text) {
        // 尝试提取 JSON 块
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private RiskAnalysisResult parseRiskResult(JsonNode node, String resourceType,
                                                String resourceName, String namespace) {
        RiskAnalysisResult risk = new RiskAnalysisResult();
        risk.setName(node.path("name").asText("未知风险"));
        risk.setDescription(node.path("description").asText(""));

        // 解析分类
        String categoryStr = node.path("category").asText("AVAILABILITY_RISK");
        try {
            risk.setCategory(RiskCategory.valueOf(categoryStr));
        } catch (IllegalArgumentException e) {
            risk.setCategory(RiskCategory.AVAILABILITY_RISK);
        }

        // 解析严重等级
        String severityStr = node.path("severity").asText("MEDIUM");
        try {
            risk.setSeverity(RiskSeverity.valueOf(severityStr));
        } catch (IllegalArgumentException e) {
            risk.setSeverity(RiskSeverity.MEDIUM);
        }

        risk.setImpactScope(node.path("impactScope").asText(""));
        if (resourceType != null && resourceName != null) {
            risk.setRelatedResource(resourceType + "/" + resourceName);
        }
        risk.setNamespace(namespace);

        // 解析推荐故障
        JsonNode faultsNode = node.path("recommendedFaults");
        if (faultsNode.isArray()) {
            for (JsonNode faultNode : faultsNode) {
                RecommendedFault fault = new RecommendedFault();
                fault.setFaultCode(faultNode.path("faultCode").asText(""));
                fault.setFaultName(faultNode.path("faultName").asText(""));
                fault.setDescription(faultNode.path("description").asText(""));
                fault.setPriority(faultNode.path("priority").asInt(1));

                // 解析参数
                JsonNode paramsNode = faultNode.path("parameters");
                if (paramsNode.isObject()) {
                    paramsNode.fields().forEachRemaining(entry ->
                        fault.getParameters().put(entry.getKey(), entry.getValue().asText()));
                }

                risk.addRecommendedFault(fault);
            }
        }

        return risk;
    }

    /**
     * 如果配置开启，设置调试用的prompt信息
     */
    private void setDebugPromptIfEnabled(RiskAnalysisResponse response, String prompt) {
        if (riskAnalysisProperties.getDebug().isShowPrompt()) {
            response.setDebugPrompt(prompt);
            response.setDebugSystemPrompt(RiskAnalysisPromptBuilder.SYSTEM_PROMPT);
            logger.debug("Debug prompt enabled, prompt length: {}", prompt.length());
        }
    }
}

