package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.client.LlmClient;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.TopologyRiskResult.TopologyRisk;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapEdge;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 拓扑风险LLM分析服务
 * 
 * Phase 2: 基于服务拓扑和Phase1结果，使用LLM识别拓扑层面的风险
 */
@Service
public class TopologyRiskLLMService {
    
    private static final Logger logger = LoggerFactory.getLogger(TopologyRiskLLMService.class);
    
    @Autowired
    private LlmClient llmClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /** 拓扑风险分析的系统提示 */
    private static final String SYSTEM_PROMPT = 
        "你是一位资深的微服务架构师和混沌工程专家。\n\n" +
        "## 你的任务\n" +
        "基于服务调用拓扑和各服务的配置风险分数，识别拓扑层面的系统性风险。\n\n" +
        "## 需要识别的拓扑风险类型\n" +
        "1. SINGLE_POINT: 单点故障风险 - 关键服务只有单一实例或无冗余\n" +
        "2. CASCADING: 级联故障风险 - 故障可能沿调用链传播\n" +
        "3. BOTTLENECK: 瓶颈风险 - 高入度服务成为性能瓶颈\n" +
        "4. CIRCULAR_DEPENDENCY: 循环依赖风险 - 可能导致死锁或启动问题\n" +
        "5. BLAST_RADIUS: 爆炸半径风险 - 单个服务故障影响大量下游\n" +
        "6. DEPENDENCY_CONCENTRATION: 依赖集中风险 - 多个服务依赖同一个高风险服务\n\n" +
        "## 分析原则\n" +
        "1. 结合Phase1的风险分数：高风险服务如果是关键节点，风险更大\n" +
        "2. 关注入度高的服务：被多个服务依赖的节点是潜在瓶颈\n" +
        "3. 关注调用链深度：长调用链增加故障传播风险\n" +
        "4. 每个风险必须指明受影响的服务列表\n\n" +
        "## 严格按以下JSON格式输出\n" +
        "```json\n" +
        "{\n" +
        "  \"risks\": [\n" +
        "    {\n" +
        "      \"riskId\": \"TOPO_001\",\n" +
        "      \"name\": \"风险名称\",\n" +
        "      \"category\": \"SINGLE_POINT|CASCADING|BOTTLENECK|CIRCULAR_DEPENDENCY|BLAST_RADIUS|DEPENDENCY_CONCENTRATION\",\n" +
        "      \"score\": 20,\n" +
        "      \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n" +
        "      \"affectedServices\": [\"service1\", \"service2\"],\n" +
        "      \"description\": \"风险描述\",\n" +
        "      \"evidence\": \"具体证据\",\n" +
        "      \"remediation\": \"改进建议\"\n" +
        "    }\n" +
        "  ]\n" +
        "}\n" +
        "```";
    
    /**
     * 分析服务拓扑风险
     * 
     * @param namespace 命名空间
     * @param serviceMap 服务拓扑数据
     * @param phase1Results Phase1的扫描结果
     * @return 拓扑风险分析结果
     */
    public TopologyRiskResult analyzeTopology(String namespace, ServiceMapData serviceMap,
            Map<String, ServiceRiskProfile> phase1Results) {
        
        long startTime = System.currentTimeMillis();
        logger.info("Starting topology risk analysis for namespace: {}", namespace);
        
        TopologyRiskResult result = new TopologyRiskResult(namespace);
        
        // 计算拓扑统计
        calculateTopologyStats(result, serviceMap);
        
        // 构建Prompt
        String prompt = buildPrompt(namespace, serviceMap, phase1Results);
        
        // 调用LLM
        if (!llmClient.isConfigured()) {
            logger.warn("LLM not configured, using rule-based topology analysis");
            // 回退到基于规则的分析
            performRuleBasedAnalysis(result, serviceMap, phase1Results);
        } else {
            try {
                String llmResponse = llmClient.chat(prompt, SYSTEM_PROMPT);
                if (llmResponse != null) {
                    parseAndAddRisks(result, llmResponse);
                }
            } catch (Exception e) {
                logger.error("LLM analysis failed, falling back to rule-based: {}", e.getMessage());
                performRuleBasedAnalysis(result, serviceMap, phase1Results);
            }
        }
        
        result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        logger.info("Topology risk analysis completed: {} risks found, {}ms",
            result.getRisks().size(), result.getAnalysisTimeMs());
        
        return result;
    }
    
    /**
     * 构建LLM分析Prompt
     */
    private String buildPrompt(String namespace, ServiceMapData serviceMap,
            Map<String, ServiceRiskProfile> phase1Results) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 服务拓扑风险分析\n\n");
        sb.append("请分析以下服务拓扑，识别拓扑层面的系统性风险。\n\n");
        
        // 拓扑数据
        sb.append("### 服务拓扑数据\n```json\n");
        sb.append(formatTopologyAsJson(serviceMap, phase1Results));
        sb.append("\n```\n\n");
        
        // Phase1风险摘要
        sb.append("### Phase1配置风险摘要\n```json\n");
        sb.append(formatPhase1Summary(phase1Results));
        sb.append("\n```\n\n");
        
        sb.append("请严格按照系统提示中的JSON格式输出分析结果。\n");
        
        return sb.toString();
    }
    
    /**
     * 格式化拓扑数据为JSON
     */
    private String formatTopologyAsJson(ServiceMapData serviceMap,
            Map<String, ServiceRiskProfile> phase1Results) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            
            // 服务节点
            List<Map<String, Object>> nodes = new ArrayList<>();
            Map<String, Integer> inDegrees = calculateInDegrees(serviceMap);
            Map<String, Integer> outDegrees = calculateOutDegrees(serviceMap);
            
            if (serviceMap.getNodes() != null) {
                for (ServiceMapNode node : serviceMap.getNodes()) {
                    Map<String, Object> nodeData = new LinkedHashMap<>();
                    nodeData.put("name", node.getServiceName());
                    nodeData.put("inDegree", inDegrees.getOrDefault(node.getServiceName(), 0));
                    nodeData.put("outDegree", outDegrees.getOrDefault(node.getServiceName(), 0));
                    
                    // 添加Phase1风险分数
                    ServiceRiskProfile profile = phase1Results.get(node.getServiceName());
                    if (profile != null) {
                        nodeData.put("phase1Score", profile.getTotalScore());
                        nodeData.put("riskLevel", profile.getRiskLevel());
                    }
                    
                    nodes.add(nodeData);
                }
            }
            data.put("services", nodes);
            // 更多内容在下面继续
            // 调用边
            List<Map<String, Object>> edges = new ArrayList<>();
            if (serviceMap.getEdges() != null) {
                for (ServiceMapEdge edge : serviceMap.getEdges()) {
                    Map<String, Object> edgeData = new LinkedHashMap<>();
                    edgeData.put("from", edge.getSourceService());
                    edgeData.put("to", edge.getTargetService());
                    if (edge.getCallCount() != null) {
                        edgeData.put("callCount", edge.getCallCount());
                    }
                    edges.add(edgeData);
                }
            }
            data.put("calls", edges);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 格式化Phase1风险摘要
     */
    private String formatPhase1Summary(Map<String, ServiceRiskProfile> phase1Results) {
        try {
            List<Map<String, Object>> summary = new ArrayList<>();
            for (Map.Entry<String, ServiceRiskProfile> entry : phase1Results.entrySet()) {
                ServiceRiskProfile profile = entry.getValue();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("service", profile.getServiceName());
                item.put("score", profile.getTotalScore());
                item.put("level", profile.getRiskLevel());
                item.put("criticalInfra", profile.isCriticalInfra());
                item.put("rulesTriggered", profile.getTriggeredRules().size());
                summary.add(item);
            }
            // 按分数排序
            summary.sort((a, b) -> Integer.compare((Integer)b.get("score"), (Integer)a.get("score")));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 计算入度
     */
    private Map<String, Integer> calculateInDegrees(ServiceMapData serviceMap) {
        Map<String, Integer> inDegrees = new HashMap<>();
        if (serviceMap.getEdges() != null) {
            for (ServiceMapEdge edge : serviceMap.getEdges()) {
                inDegrees.merge(edge.getTargetService(), 1, Integer::sum);
            }
        }
        return inDegrees;
    }

    /**
     * 计算出度
     */
    private Map<String, Integer> calculateOutDegrees(ServiceMapData serviceMap) {
        Map<String, Integer> outDegrees = new HashMap<>();
        if (serviceMap.getEdges() != null) {
            for (ServiceMapEdge edge : serviceMap.getEdges()) {
                outDegrees.merge(edge.getSourceService(), 1, Integer::sum);
            }
        }
        return outDegrees;
    }

    /**
     * 计算拓扑统计
     */
    private void calculateTopologyStats(TopologyRiskResult result, ServiceMapData serviceMap) {
        TopologyRiskResult.TopologyStats stats = result.getStats();

        if (serviceMap.getNodes() != null) {
            stats.setTotalServices(serviceMap.getNodes().size());
        }
        if (serviceMap.getEdges() != null) {
            stats.setTotalEdges(serviceMap.getEdges().size());
        }

        Map<String, Integer> inDegrees = calculateInDegrees(serviceMap);
        Map<String, Integer> outDegrees = calculateOutDegrees(serviceMap);

        int maxIn = 0, maxOut = 0;
        String mostConnected = null;
        int maxTotal = 0;

        for (String service : inDegrees.keySet()) {
            int in = inDegrees.getOrDefault(service, 0);
            int out = outDegrees.getOrDefault(service, 0);
            maxIn = Math.max(maxIn, in);
            maxOut = Math.max(maxOut, out);
            if (in + out > maxTotal) {
                maxTotal = in + out;
                mostConnected = service;
            }
        }

        stats.setMaxInDegree(maxIn);
        stats.setMaxOutDegree(maxOut);
        stats.setMostConnectedService(mostConnected);
    }

    /**
     * 解析LLM响应并添加风险
     */
    private void parseAndAddRisks(TopologyRiskResult result, String llmResponse) {
        try {
            // 提取JSON部分
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);
            JsonNode risksNode = root.path("risks");

            if (risksNode.isArray()) {
                int riskIndex = 1;
                for (JsonNode riskNode : risksNode) {
                    TopologyRisk risk = new TopologyRisk();
                    risk.setRiskId(riskNode.path("riskId").asText("TOPO_" + String.format("%03d", riskIndex++)));
                    risk.setName(riskNode.path("name").asText());
                    risk.setCategory(riskNode.path("category").asText());
                    risk.setScore(riskNode.path("score").asInt(20));
                    risk.setSeverity(riskNode.path("severity").asText("MEDIUM"));
                    risk.setDescription(riskNode.path("description").asText());
                    risk.setEvidence(riskNode.path("evidence").asText());
                    risk.setRemediation(riskNode.path("remediation").asText());

                    // 解析受影响服务
                    JsonNode affectedNode = riskNode.path("affectedServices");
                    if (affectedNode.isArray()) {
                        List<String> affected = new ArrayList<>();
                        for (JsonNode s : affectedNode) {
                            affected.add(s.asText());
                        }
                        risk.setAffectedServices(affected);
                    }

                    result.addRisk(risk);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse LLM response: {}", e.getMessage());
        }
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 基于规则的拓扑分析（LLM不可用时的回退）
     */
    private void performRuleBasedAnalysis(TopologyRiskResult result, ServiceMapData serviceMap,
            Map<String, ServiceRiskProfile> phase1Results) {

        Map<String, Integer> inDegrees = calculateInDegrees(serviceMap);
        Map<String, Integer> outDegrees = calculateOutDegrees(serviceMap);
        int riskIndex = 1;

        // 规则1: 高入度服务（瓶颈风险）
        for (Map.Entry<String, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() >= 5) {
                TopologyRisk risk = new TopologyRisk();
                risk.setRiskId("TOPO_" + String.format("%03d", riskIndex++));
                risk.setName("高入度瓶颈服务: " + entry.getKey());
                risk.setCategory("BOTTLENECK");
                risk.setScore(25);
                risk.setSeverity("HIGH");
                risk.setAffectedServices(Arrays.asList(entry.getKey()));
                risk.setDescription(entry.getKey() + " 被 " + entry.getValue() + " 个服务依赖，是系统瓶颈");
                risk.setEvidence("入度: " + entry.getValue());
                risk.setRemediation("考虑对该服务进行横向扩展或引入缓存");
                result.addRisk(risk);
            }
        }

        // 规则2: 高风险+高入度（级联风险）
        for (Map.Entry<String, ServiceRiskProfile> entry : phase1Results.entrySet()) {
            String service = entry.getKey();
            ServiceRiskProfile profile = entry.getValue();
            int inDegree = inDegrees.getOrDefault(service, 0);

            if (profile.getTotalScore() >= 60 && inDegree >= 3) {
                TopologyRisk risk = new TopologyRisk();
                risk.setRiskId("TOPO_" + String.format("%03d", riskIndex++));
                risk.setName("高风险依赖集中: " + service);
                risk.setCategory("DEPENDENCY_CONCENTRATION");
                risk.setScore(30);
                risk.setSeverity("CRITICAL");
                risk.setAffectedServices(findDependentServices(service, serviceMap));
                risk.setDescription(service + " 配置风险高(" + profile.getTotalScore() + "分)且被多个服务依赖");
                risk.setEvidence("配置风险分: " + profile.getTotalScore() + ", 入度: " + inDegree);
                risk.setRemediation("优先修复该服务的配置问题，并考虑增加冗余");
                result.addRisk(risk);
            }
        }

        // 规则3: 高出度服务（爆炸半径风险）
        for (Map.Entry<String, Integer> entry : outDegrees.entrySet()) {
            if (entry.getValue() >= 5) {
                TopologyRisk risk = new TopologyRisk();
                risk.setRiskId("TOPO_" + String.format("%03d", riskIndex++));
                risk.setName("高爆炸半径服务: " + entry.getKey());
                risk.setCategory("BLAST_RADIUS");
                risk.setScore(20);
                risk.setSeverity("MEDIUM");
                risk.setAffectedServices(Arrays.asList(entry.getKey()));
                risk.setDescription(entry.getKey() + " 依赖 " + entry.getValue() + " 个下游服务，任一下游故障都会影响它");
                risk.setEvidence("出度: " + entry.getValue());
                risk.setRemediation("考虑增加熔断器和降级策略");
                result.addRisk(risk);
            }
        }
    }

    /**
     * 查找依赖某服务的所有上游服务
     */
    private List<String> findDependentServices(String targetService, ServiceMapData serviceMap) {
        List<String> dependents = new ArrayList<>();
        dependents.add(targetService);
        if (serviceMap.getEdges() != null) {
            for (ServiceMapEdge edge : serviceMap.getEdges()) {
                if (edge.getTargetService().equals(targetService)) {
                    dependents.add(edge.getSourceService());
                }
            }
        }
        return dependents;
    }
}

