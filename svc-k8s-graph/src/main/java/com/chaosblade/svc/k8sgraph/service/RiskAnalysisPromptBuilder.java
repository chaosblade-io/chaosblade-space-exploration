package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.GraphData;
import com.chaosblade.svc.k8sgraph.domain.GraphEdge;
import com.chaosblade.svc.k8sgraph.domain.GraphNode;
import com.chaosblade.svc.k8sgraph.domain.ResourceDetail;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapEdge;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 风险分析 Prompt 构建器
 *
 * 设计原则：
 * 1. 数据完整性 > 数据可读性（使用JSON，不省略关键数据）
 * 2. 提供统计摘要 + 原始数据（让模型既能宏观又能微观）
 * 3. 分析框架作为参考而非约束
 * 4. 输出格式允许扩展
 */
@Component
public class RiskAnalysisPromptBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 通用的系统提示 - 开放式分析框架 */
    public static final String SYSTEM_PROMPT =
        "你是一位资深的 Kubernetes 运维专家和混沌工程师。\n\n" +
        "## 你的职责\n" +
        "基于提供的数据，独立发现和识别系统中的潜在风险点。不要局限于预设的分析框架，而是根据数据本身的特征进行深入分析。\n\n" +
        "## 风险分类参考（不限于此）\n" +
        "以下是常见的风险类别，但你可以根据实际发现定义新的类别：\n" +
        "- SINGLE_POINT_FAILURE: 单点故障\n" +
        "- RESOURCE_BOTTLENECK: 资源瓶颈\n" +
        "- DEPENDENCY_RISK: 依赖风险\n" +
        "- PERFORMANCE_DEGRADATION: 性能退化\n" +
        "- AVAILABILITY_RISK: 可用性风险\n" +
        "- CONFIGURATION_RISK: 配置风险\n" +
        "- SECURITY_RISK: 安全风险\n" +
        "- DATA_CONSISTENCY_RISK: 数据一致性风险\n" +
        "- CASCADING_FAILURE_RISK: 级联故障风险\n" +
        "- 其他你发现的风险类别...\n\n" +
        "## 严重等级\n" +
        "- CRITICAL: 严重（可能导致核心业务中断）\n" +
        "- HIGH: 高（显著影响业务功能）\n" +
        "- MEDIUM: 中等（部分功能受影响）\n" +
        "- LOW: 低（轻微影响或预防性建议）\n\n" +
        "## 可用的故障注入类型（ChaosBlade）\n" +
        "- chaosblade.k8s.container-cpu: CPU负载注入\n" +
        "- chaosblade.k8s.container-memory: 内存负载注入\n" +
        "- chaosblade.k8s.container-disk: 磁盘IO负载\n" +
        "- chaosblade.k8s.container-network-delay: 网络延迟\n" +
        "- chaosblade.k8s.container-network-loss: 网络丢包\n" +
        "- chaosblade.k8s.container-network-corrupt: 网络包损坏\n" +
        "- chaosblade.k8s.container-network-dns: DNS故障\n" +
        "- chaosblade.k8s.container-process-stop: 进程停止\n" +
        "- chaosblade.k8s.pod-kill: Pod终止\n" +
        "- chaosblade.k8s.container-remove: 容器移除\n\n" +
        "## 分析原则\n" +
        "1. 基于数据说话：每个风险结论都应有数据支撑\n" +
        "2. 量化影响：尽可能用具体数值描述风险影响\n" +
        "3. 优先级排序：按实际业务影响排序风险\n" +
        "4. 可操作性：每个风险都应有对应的验证方案\n\n" +
        "请严格按照指定的 JSON 格式输出分析结果。";

    /**
     * 构建 K8s 资源配置风险分析的 Prompt
     */
    public String buildResourceRiskPrompt(String resourceType, String resourceName,
                                          String namespace, ResourceDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：K8s 资源配置风险分析\n\n");
        sb.append("请基于以下资源的完整配置数据，独立发现潜在风险点。\n\n");

        // 使用结构化JSON传递数据
        sb.append("### 资源配置数据\n```json\n");
        sb.append(formatResourceDetailAsJson(resourceType, resourceName, namespace, detail));
        sb.append("\n```\n\n");

        sb.append("### 分析参考维度（不限于此）\n");
        sb.append("- 高可用性：副本数、PDB配置、反亲和性\n");
        sb.append("- 资源管理：requests/limits配置、QoS等级\n");
        sb.append("- 健康检查：探针配置及参数合理性\n");
        sb.append("- 安全配置：权限、网络策略、敏感信息\n");
        sb.append("- 更新策略：滚动更新参数、回滚能力\n");
        sb.append("- 其他你发现的风险维度...\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    /**
     * 将资源详情格式化为完整的JSON
     */
    private String formatResourceDetailAsJson(String resourceType, String resourceName,
                                               String namespace, ResourceDetail detail) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resourceType", resourceType);
            data.put("resourceName", resourceName);
            data.put("namespace", namespace);

            if (detail != null) {
                data.put("status", detail.getStatus());
                data.put("creationTimestamp", detail.getCreationTimestamp());
                data.put("labels", detail.getLabels());

                // 完整的properties，不做截断
                Map<String, Object> properties = detail.getProperties();
                if (properties != null && !properties.isEmpty()) {
                    data.put("spec", properties);
                }

                // 提取关键配置缺失信息
                Map<String, Object> configAnalysis = new LinkedHashMap<>();
                configAnalysis.put("hasResourceLimits", properties != null && properties.containsKey("resources"));
                configAnalysis.put("hasReadinessProbe", properties != null && properties.containsKey("readinessProbe"));
                configAnalysis.put("hasLivenessProbe", properties != null && properties.containsKey("livenessProbe"));
                configAnalysis.put("hasPDB", properties != null && properties.containsKey("pdb"));
                configAnalysis.put("hasHPA", properties != null && properties.containsKey("hpa"));
                data.put("configAnalysis", configAnalysis);
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            // 回退到简单格式
            return formatDetailFallback(resourceType, resourceName, namespace, detail);
        }
    }

    private String formatDetailFallback(String resourceType, String resourceName,
                                        String namespace, ResourceDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"resourceType\": \"").append(resourceType).append("\",\n");
        sb.append("  \"resourceName\": \"").append(resourceName).append("\",\n");
        sb.append("  \"namespace\": \"").append(namespace).append("\",\n");
        if (detail != null) {
            sb.append("  \"status\": \"").append(detail.getStatus()).append("\",\n");
            sb.append("  \"properties\": ").append(detail.getProperties()).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 通用输出格式 - 更灵活的JSON结构
     */
    private String getOutputFormat() {
        return "### 输出格式要求\n" +
            "请严格按照以下 JSON 格式输出（只输出 JSON，不要其他内容）：\n" +
            "```json\n" +
            "{\n" +
            "  \"analysisContext\": {\n" +
            "    \"dataCompleteness\": \"数据完整度评估（complete/partial/insufficient）\",\n" +
            "    \"confidenceLevel\": \"分析置信度（high/medium/low）\",\n" +
            "    \"limitations\": [\"数据局限性说明\"]\n" +
            "  },\n" +
            "  \"risks\": [\n" +
            "    {\n" +
            "      \"name\": \"风险名称\",\n" +
            "      \"description\": \"风险详细描述，包含具体数据依据\",\n" +
            "      \"category\": \"风险类别（可自定义）\",\n" +
            "      \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n" +
            "      \"evidence\": \"支撑该风险结论的具体数据\",\n" +
            "      \"impactScope\": \"影响范围描述\",\n" +
            "      \"affectedResources\": [\"受影响的资源列表\"],\n" +
            "      \"recommendedFaults\": [\n" +
            "        {\n" +
            "          \"faultCode\": \"chaosblade.k8s.xxx\",\n" +
            "          \"faultName\": \"故障名称\",\n" +
            "          \"description\": \"故障描述和验证目标\",\n" +
            "          \"priority\": 1,\n" +
            "          \"parameters\": {}\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "```\n";
    }
    
    /**
     * 构建 K8s 拓扑风险分析的 Prompt
     */
    public String buildTopologyRiskPrompt(String namespace, GraphData graphData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：K8s 拓扑风险分析\n\n");
        sb.append("请基于以下命名空间的完整资源拓扑数据，独立发现依赖关系中的风险点。\n\n");

        sb.append("### 拓扑数据\n```json\n");
        sb.append(formatTopologyAsJson(namespace, graphData));
        sb.append("\n```\n\n");

        sb.append("### 分析参考维度（不限于此）\n");
        sb.append("- 单点故障：关键服务缺少冗余\n");
        sb.append("- 依赖瓶颈：高入度节点（被多个服务依赖）\n");
        sb.append("- 级联风险：依赖链过长可能导致故障传播\n");
        sb.append("- 孤立资源：未被使用或缺乏监控\n");
        sb.append("- 循环依赖：可能导致死锁或启动问题\n");
        sb.append("- 其他你发现的拓扑风险...\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    /**
     * 将拓扑数据格式化为结构化JSON，包含统计摘要
     */
    private String formatTopologyAsJson(String namespace, GraphData graphData) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("namespace", namespace);

            if (graphData == null) {
                data.put("error", "无法获取拓扑数据");
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            }

            // 统计摘要
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalNodes", graphData.getNodes().size());
            summary.put("totalEdges", graphData.getEdges().size());

            // 按类型统计节点
            Map<String, Integer> nodeTypeCount = new LinkedHashMap<>();
            Map<String, Integer> inDegree = new HashMap<>();  // 入度统计
            Map<String, Integer> outDegree = new HashMap<>(); // 出度统计

            for (GraphNode node : graphData.getNodes()) {
                String type = node.getType();
                nodeTypeCount.merge(type, 1, Integer::sum);
                inDegree.put(node.getId(), 0);
                outDegree.put(node.getId(), 0);
            }
            summary.put("nodeTypeDistribution", nodeTypeCount);

            // 计算入度和出度
            Map<String, List<String>> edgeTypeCount = new LinkedHashMap<>();
            for (GraphEdge edge : graphData.getEdges()) {
                inDegree.merge(edge.getTarget(), 1, Integer::sum);
                outDegree.merge(edge.getSource(), 1, Integer::sum);
                String edgeTypeName = edge.getType() != null ? edge.getType().name() : "UNKNOWN";
                edgeTypeCount.computeIfAbsent(edgeTypeName, k -> new ArrayList<>())
                    .add(edge.getSource() + " -> " + edge.getTarget());
            }
            summary.put("edgeTypeDistribution", edgeTypeCount.entrySet().stream()
                .collect(LinkedHashMap::new,
                    (m, e) -> m.put(e.getKey(), e.getValue().size()),
                    Map::putAll));

            // 高入度节点（潜在瓶颈）
            List<Map<String, Object>> highInDegreeNodes = new ArrayList<>();
            inDegree.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("nodeId", e.getKey());
                    node.put("inDegree", e.getValue());
                    highInDegreeNodes.add(node);
                });
            summary.put("highInDegreeNodes", highInDegreeNodes);

            // 单副本工作负载（潜在单点故障）
            List<Map<String, Object>> singleReplicaWorkloads = new ArrayList<>();
            for (GraphNode node : graphData.getNodes()) {
                String type = node.getType();
                if (type.contains("deployment") || type.contains("statefulset")) {
                    Object replicas = node.getProperties().get("replicas");
                    if (replicas != null && "1".equals(replicas.toString())) {
                        Map<String, Object> workload = new LinkedHashMap<>();
                        workload.put("name", node.getName());
                        workload.put("type", type);
                        workload.put("replicas", 1);
                        singleReplicaWorkloads.add(workload);
                    }
                }
            }
            summary.put("singleReplicaWorkloads", singleReplicaWorkloads);

            data.put("summary", summary);

            // 完整节点列表（工作负载和服务）
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (GraphNode node : graphData.getNodes()) {
                String type = node.getType();
                if (type.contains("deployment") || type.contains("service") ||
                    type.contains("statefulset") || type.contains("daemonset")) {
                    Map<String, Object> nodeData = new LinkedHashMap<>();
                    nodeData.put("id", node.getId());
                    nodeData.put("name", node.getName());
                    nodeData.put("type", type);
                    nodeData.put("inDegree", inDegree.getOrDefault(node.getId(), 0));
                    nodeData.put("outDegree", outDegree.getOrDefault(node.getId(), 0));
                    if (!node.getProperties().isEmpty()) {
                        nodeData.put("properties", node.getProperties());
                    }
                    nodes.add(nodeData);
                }
            }
            data.put("nodes", nodes);

            // 完整边列表
            List<Map<String, Object>> edges = new ArrayList<>();
            for (GraphEdge edge : graphData.getEdges()) {
                Map<String, Object> edgeData = new LinkedHashMap<>();
                edgeData.put("source", edge.getSource());
                edgeData.put("target", edge.getTarget());
                edgeData.put("type", edge.getType());
                edges.add(edgeData);
            }
            data.put("edges", edges);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\": \"格式化拓扑数据失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 构建服务拓扑风险分析的 Prompt
     */
    public String buildServiceTopologyRiskPrompt(String namespace, ServiceMapData serviceMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：服务拓扑风险分析\n\n");
        sb.append("请基于以下服务调用拓扑数据，独立发现服务间调用关系中的风险点。\n\n");

        sb.append("### 服务拓扑数据\n```json\n");
        sb.append(formatServiceMapAsJson(namespace, serviceMap));
        sb.append("\n```\n\n");

        sb.append("### 分析参考维度（不限于此）\n");
        sb.append("- 关键路径：高入度服务可能是系统瓶颈\n");
        sb.append("- 错误传播：高错误率调用链可能导致级联故障\n");
        sb.append("- 延迟敏感：高延迟调用影响用户体验\n");
        sb.append("- 服务健康：异常状态服务需要关注\n");
        sb.append("- 循环依赖：可能导致死锁或资源耗尽\n");
        sb.append("- 其他你发现的服务拓扑风险...\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    /**
     * 将服务拓扑数据格式化为结构化JSON
     */
    private String formatServiceMapAsJson(String namespace, ServiceMapData serviceMap) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("namespace", namespace);

            if (serviceMap == null || serviceMap.getNodes().isEmpty()) {
                data.put("error", "未获取到服务拓扑数据");
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            }

            // 按命名空间过滤节点
            List<ServiceMapNode> filteredNodes = serviceMap.getNodes().stream()
                .filter(node -> namespace == null || namespace.isEmpty()
                    || namespace.equals(node.getNamespace()))
                .collect(java.util.stream.Collectors.toList());

            // 统计摘要
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalServices", filteredNodes.size());

            // 状态分布
            Map<String, Integer> statusDistribution = new LinkedHashMap<>();
            long totalRequests = 0;
            double totalErrorRate = 0;
            int errorRateCount = 0;

            for (ServiceMapNode node : filteredNodes) {
                String status = node.getStatus() != null ? node.getStatus() : "unknown";
                statusDistribution.merge(status, 1, Integer::sum);
                if (node.getRequestCount() != null) {
                    totalRequests += node.getRequestCount();
                }
                if (node.getErrorRate() != null) {
                    totalErrorRate += node.getErrorRate();
                    errorRateCount++;
                }
            }
            summary.put("statusDistribution", statusDistribution);
            summary.put("totalRequests", totalRequests);
            summary.put("avgErrorRate", errorRateCount > 0 ? totalErrorRate / errorRateCount : 0);

            // 异常服务列表
            List<Map<String, Object>> abnormalServices = new ArrayList<>();
            for (ServiceMapNode node : filteredNodes) {
                if (node.getStatus() != null && !"ok".equals(node.getStatus())) {
                    Map<String, Object> svc = new LinkedHashMap<>();
                    svc.put("serviceName", node.getServiceName());
                    svc.put("status", node.getStatus());
                    svc.put("errorRate", node.getErrorRate());
                    svc.put("requestCount", node.getRequestCount());
                    abnormalServices.add(svc);
                }
            }
            summary.put("abnormalServices", abnormalServices);

            data.put("summary", summary);

            // 完整服务列表
            List<Map<String, Object>> services = new ArrayList<>();
            for (ServiceMapNode node : filteredNodes) {
                Map<String, Object> svc = new LinkedHashMap<>();
                svc.put("serviceName", node.getServiceName());
                svc.put("namespace", node.getNamespace());
                svc.put("status", node.getStatus());
                svc.put("requestCount", node.getRequestCount());
                svc.put("errorRate", node.getErrorRate());
                svc.put("avgLatency", node.getAvgLatency());
                svc.put("p99Latency", node.getP99Latency());
                services.add(svc);
            }
            data.put("services", services);

            // 过滤涉及该命名空间服务的边
            Set<String> filteredServiceNames = filteredNodes.stream()
                .map(ServiceMapNode::getServiceName)
                .collect(java.util.stream.Collectors.toSet());

            List<ServiceMapEdge> filteredEdges = serviceMap.getEdges().stream()
                .filter(edge -> filteredServiceNames.contains(edge.getSourceService())
                    || filteredServiceNames.contains(edge.getTargetService()))
                .collect(java.util.stream.Collectors.toList());

            // 调用关系统计
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, Integer> outDegree = new HashMap<>();
            for (ServiceMapEdge edge : filteredEdges) {
                inDegree.merge(edge.getTargetService(), 1, Integer::sum);
                outDegree.merge(edge.getSourceService(), 1, Integer::sum);
            }

            // 高入度服务（潜在瓶颈）
            List<Map<String, Object>> highInDegreeServices = new ArrayList<>();
            inDegree.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> svc = new LinkedHashMap<>();
                    svc.put("serviceName", e.getKey());
                    svc.put("inDegree", e.getValue());
                    highInDegreeServices.add(svc);
                });
            summary.put("highInDegreeServices", highInDegreeServices);

            // 完整调用关系
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ServiceMapEdge edge : filteredEdges) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("source", edge.getSourceService());
                call.put("target", edge.getTargetService());
                call.put("callCount", edge.getCallCount());
                call.put("avgLatency", edge.getAvgLatency());
                call.put("p99Latency", edge.getP99Latency());
                call.put("errorRate", edge.getErrorRate());
                calls.add(call);
            }
            data.put("calls", calls);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\": \"格式化服务拓扑数据失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 构建链路风险分析的 Prompt
     */
    public String buildTraceRiskPrompt(String traceId, JsonNode traceData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：链路风险分析\n\n");
        sb.append("请基于以下链路追踪数据，独立发现调用链中的风险点。\n\n");

        sb.append("### 链路数据\n```json\n");
        sb.append(formatTraceAsJson(traceId, traceData));
        sb.append("\n```\n\n");

        sb.append("### 分析参考维度（不限于此）\n");
        sb.append("- 性能瓶颈：耗时异常的span\n");
        sb.append("- 错误传播：错误span及其影响范围\n");
        sb.append("- 调用深度：过深的调用链可能导致延迟累积\n");
        sb.append("- 重试风暴：同一操作的重复调用\n");
        sb.append("- 资源竞争：并发调用可能导致的问题\n");
        sb.append("- 其他你发现的链路风险...\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    /**
     * 将链路数据格式化为结构化JSON，保留span层级关系
     */
    private String formatTraceAsJson(String traceId, JsonNode traceData) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("traceId", traceId);

            if (traceData == null || !traceData.isArray() || traceData.size() == 0) {
                data.put("error", "未获取到具体的Span数据");
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            }

            // 统计摘要
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalSpans", traceData.size());

            // 收集所有span信息
            List<Map<String, Object>> spans = new ArrayList<>();
            Map<String, Integer> serviceCallCount = new LinkedHashMap<>();
            int errorCount = 0;
            double totalDuration = 0;
            double maxDuration = 0;
            String slowestSpan = "";
            List<Map<String, Object>> errorSpans = new ArrayList<>();

            for (JsonNode span : traceData) {
                String serviceName = getSpanField(span, "service", "ServiceName", "serviceName");
                String name = getSpanField(span, "name", "Name", "operationName");
                String spanId = getSpanField(span, "spanId", "SpanId", "span_id");
                String parentSpanId = getSpanField(span, "parentSpanId", "ParentSpanId", "parent_span_id");
                double duration = span.path("duration").asDouble(span.path("Duration").asDouble(0));
                boolean hasError = span.path("status").path("error").asBoolean(false);
                String statusCode = getSpanField(span, "status", "StatusCode", "statusCode");

                // 统计服务调用次数
                serviceCallCount.merge(serviceName, 1, Integer::sum);

                // 转换duration为毫秒
                double durationMs = duration > 10000 ? duration / 1000.0 : duration;
                totalDuration += durationMs;

                if (durationMs > maxDuration) {
                    maxDuration = durationMs;
                    slowestSpan = serviceName + ":" + name;
                }

                // 构建span数据
                Map<String, Object> spanData = new LinkedHashMap<>();
                spanData.put("spanId", spanId);
                spanData.put("parentSpanId", parentSpanId);
                spanData.put("serviceName", serviceName);
                spanData.put("operationName", name);
                spanData.put("durationMs", durationMs);
                spanData.put("hasError", hasError);

                if (hasError) {
                    errorCount++;
                    spanData.put("statusCode", statusCode);
                    // 尝试获取错误详情
                    JsonNode statusNode = span.path("status");
                    if (statusNode.has("message")) {
                        spanData.put("errorMessage", statusNode.path("message").asText());
                    }

                    Map<String, Object> errorSpan = new LinkedHashMap<>();
                    errorSpan.put("serviceName", serviceName);
                    errorSpan.put("operationName", name);
                    errorSpan.put("durationMs", durationMs);
                    errorSpans.add(errorSpan);
                }

                spans.add(spanData);
            }

            summary.put("errorCount", errorCount);
            summary.put("errorRate", traceData.size() > 0 ? (double) errorCount / traceData.size() * 100 : 0);
            summary.put("avgDurationMs", traceData.size() > 0 ? totalDuration / traceData.size() : 0);
            summary.put("maxDurationMs", maxDuration);
            summary.put("slowestSpan", slowestSpan);
            summary.put("serviceCallDistribution", serviceCallCount);
            summary.put("errorSpans", errorSpans);

            // 识别慢span（超过平均值2倍）
            double avgDuration = traceData.size() > 0 ? totalDuration / traceData.size() : 0;
            List<Map<String, Object>> slowSpans = new ArrayList<>();
            for (Map<String, Object> spanData : spans) {
                double durationMs = (Double) spanData.get("durationMs");
                if (durationMs > avgDuration * 2 && durationMs > 10) {
                    Map<String, Object> slowSpan = new LinkedHashMap<>();
                    slowSpan.put("serviceName", spanData.get("serviceName"));
                    slowSpan.put("operationName", spanData.get("operationName"));
                    slowSpan.put("durationMs", durationMs);
                    slowSpan.put("ratio", durationMs / avgDuration);
                    slowSpans.add(slowSpan);
                }
            }
            summary.put("slowSpans", slowSpans);

            data.put("summary", summary);
            data.put("spans", spans);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\": \"格式化链路数据失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 从 span 中获取字段值，支持多种字段名
     */
    private String getSpanField(JsonNode span, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = span.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText("");
            }
        }
        return "";
    }
}

