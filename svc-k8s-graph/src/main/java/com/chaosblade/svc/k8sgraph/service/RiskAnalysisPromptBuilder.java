package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.GraphData;
import com.chaosblade.svc.k8sgraph.domain.GraphEdge;
import com.chaosblade.svc.k8sgraph.domain.GraphNode;
import com.chaosblade.svc.k8sgraph.domain.ResourceDetail;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapEdge;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 风险分析 Prompt 构建器
 */
@Component
public class RiskAnalysisPromptBuilder {
    
    /** 通用的系统提示 */
    public static final String SYSTEM_PROMPT =
        "你是一位专业的 Kubernetes 运维专家和混沌工程师。你的任务是分析 K8s 资源配置和系统拓扑，识别潜在风险点，并提供针对性的故障注入建议。\n\n" +
        "## 风险分类体系\n" +
        "- SINGLE_POINT_FAILURE: 单点故障 - 缺少冗余配置\n" +
        "- RESOURCE_BOTTLENECK: 资源瓶颈 - 资源限制不当\n" +
        "- DEPENDENCY_RISK: 依赖风险 - 依赖关系过于复杂\n" +
        "- PERFORMANCE_DEGRADATION: 性能退化 - 潜在性能问题\n" +
        "- AVAILABILITY_RISK: 可用性风险 - 影响系统可用性\n\n" +
        "## 严重等级\n" +
        "- CRITICAL: 严重（影响核心业务）\n" +
        "- HIGH: 高（影响重要功能）\n" +
        "- MEDIUM: 中等（影响次要功能）\n" +
        "- LOW: 低（轻微影响）\n\n" +
        "## 支持的故障类型（ChaosBlade）\n" +
        "- chaosblade.k8s.container-cpu: CPU满载\n" +
        "- chaosblade.k8s.container-memory: 内存满载\n" +
        "- chaosblade.k8s.container-disk: 磁盘负载提升\n" +
        "- chaosblade.k8s.container-network-delay: 网络延迟\n" +
        "- chaosblade.k8s.container-network-loss: 网络丢包\n" +
        "- chaosblade.k8s.container-network-corrupt: 网络损坏\n" +
        "- chaosblade.k8s.container-network-occupy: 网络占用\n" +
        "- chaosblade.k8s.container-network-dns: DNS异常\n" +
        "- chaosblade.k8s.container-process-stop: 进程停滞\n" +
        "- chaosblade.k8s.pod-kill: Pod删除\n" +
        "- chaosblade.k8s.container-remove: 容器移除\n\n" +
        "请严格按照 JSON 格式输出分析结果。";

    /**
     * 构建 K8s 资源配置风险分析的 Prompt
     */
    public String buildResourceRiskPrompt(String resourceType, String resourceName, 
                                          String namespace, ResourceDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：K8s 资源配置风险分析\n\n");
        sb.append("请分析以下 K8s 资源的配置风险点：\n\n");
        sb.append("### 资源信息\n");
        sb.append("- 资源类型: ").append(resourceType).append("\n");
        sb.append("- 资源名称: ").append(resourceName).append("\n");
        sb.append("- 命名空间: ").append(namespace).append("\n\n");
        
        if (detail != null) {
            sb.append("### 资源详情\n```json\n");
            sb.append(formatDetail(detail));
            sb.append("\n```\n\n");
        }
        
        sb.append(getResourceAnalysisInstructions(resourceType));
        sb.append(getOutputFormat());
        
        return sb.toString();
    }
    
    private String getResourceAnalysisInstructions(String resourceType) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 分析要点\n");
        
        switch (resourceType.toLowerCase()) {
            case "deployment":
                sb.append("1. 副本数是否足够（replicas >= 2 为高可用）\n");
                sb.append("2. 是否配置了资源限制（requests/limits）\n");
                sb.append("3. 是否配置了健康检查（readinessProbe/livenessProbe）\n");
                sb.append("4. 更新策略是否合理\n");
                sb.append("5. Pod 反亲和性配置\n");
                break;
            case "pod":
                sb.append("1. 资源请求和限制是否合理\n");
                sb.append("2. 是否配置了健康检查\n");
                sb.append("3. 容器重启策略\n");
                sb.append("4. 是否挂载了敏感配置\n");
                break;
            case "service":
                sb.append("1. Service 类型是否合适\n");
                sb.append("2. 端口配置是否正确\n");
                sb.append("3. 是否有足够的后端 Pod\n");
                sb.append("4. 会话亲和性配置\n");
                break;
            default:
                sb.append("1. 资源配置是否完整\n");
                sb.append("2. 是否存在单点故障风险\n");
                sb.append("3. 是否有资源限制配置\n");
        }
        sb.append("\n");
        return sb.toString();
    }
    
    private String formatDetail(ResourceDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"resourceType\": \"").append(detail.getResourceType()).append("\",\n");
        sb.append("  \"resourceName\": \"").append(detail.getResourceName()).append("\",\n");
        sb.append("  \"namespace\": \"").append(detail.getNamespace()).append("\",\n");
        sb.append("  \"status\": \"").append(detail.getStatus()).append("\",\n");
        sb.append("  \"labels\": ").append(detail.getLabels()).append(",\n");
        sb.append("  \"properties\": ").append(detail.getProperties()).append("\n");
        sb.append("}");
        return sb.toString();
    }
    
    private String getOutputFormat() {
        return "\n### 输出格式要求\n" +
            "请严格按照以下 JSON 格式输出（只输出 JSON，不要其他内容）：\n" +
            "```json\n" +
            "{\n" +
            "  \"risks\": [\n" +
            "    {\n" +
            "      \"name\": \"风险名称\",\n" +
            "      \"description\": \"风险详细描述\",\n" +
            "      \"category\": \"SINGLE_POINT_FAILURE|RESOURCE_BOTTLENECK|DEPENDENCY_RISK|PERFORMANCE_DEGRADATION|AVAILABILITY_RISK\",\n" +
            "      \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n" +
            "      \"impactScope\": \"影响范围描述\",\n" +
            "      \"recommendedFaults\": [\n" +
            "        {\n" +
            "          \"faultCode\": \"chaosblade.k8s.xxx\",\n" +
            "          \"faultName\": \"故障名称\",\n" +
            "          \"description\": \"故障描述和验证目标\",\n" +
            "          \"priority\": 1,\n" +
            "          \"parameters\": {\"timeout\": \"60\", \"cpu-percent\": \"80\"}\n" +
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
        sb.append("请分析以下命名空间的资源拓扑依赖风险：\n\n");
        sb.append("### 命名空间: ").append(namespace).append("\n\n");

        if (graphData != null) {
            sb.append("### 资源统计\n");
            sb.append("- 节点数量: ").append(graphData.getNodes().size()).append("\n");
            sb.append("- 边关系数量: ").append(graphData.getEdges().size()).append("\n\n");

            sb.append("### 核心资源列表\n");
            sb.append(formatTopologyNodes(graphData));
            sb.append("\n");

            sb.append("### 资源关系\n");
            sb.append(formatTopologyEdges(graphData));
            sb.append("\n");
        }

        sb.append("### 分析要点\n");
        sb.append("1. 识别单点故障（只有单个实例的关键服务）\n");
        sb.append("2. 识别资源瓶颈（被多个服务依赖的资源）\n");
        sb.append("3. 识别依赖链过长的情况\n");
        sb.append("4. 识别孤立资源\n");
        sb.append("5. 识别关键路径上的风险点\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    private String formatTopologyNodes(GraphData graphData) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GraphNode node : graphData.getNodes()) {
            if (count >= 50) {
                sb.append("... (省略更多节点)\n");
                break;
            }
            String type = node.getType();
            // 只显示核心资源
            if (type.contains("deployment") || type.contains("service") ||
                type.contains("pod") || type.contains("statefulset")) {
                sb.append("- [").append(type).append("] ").append(node.getName());
                if (node.getProperties().containsKey("replicas")) {
                    sb.append(" (replicas: ").append(node.getProperties().get("replicas")).append(")");
                }
                sb.append("\n");
                count++;
            }
        }
        return sb.toString();
    }

    private String formatTopologyEdges(GraphData graphData) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GraphEdge edge : graphData.getEdges()) {
            if (count >= 30) {
                sb.append("... (省略更多关系)\n");
                break;
            }
            sb.append("- ").append(edge.getSource()).append(" --[")
              .append(edge.getType()).append("]--> ").append(edge.getTarget()).append("\n");
            count++;
        }
        return sb.toString();
    }

    /**
     * 构建服务拓扑风险分析的 Prompt
     */
    public String buildServiceTopologyRiskPrompt(String namespace, ServiceMapData serviceMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：服务拓扑风险分析\n\n");
        sb.append("请分析以下服务调用拓扑的风险点：\n\n");
        sb.append("### 命名空间: ").append(namespace).append("\n\n");

        if (serviceMap != null && !serviceMap.getNodes().isEmpty()) {
            // 按命名空间过滤节点
            List<ServiceMapNode> filteredNodes = serviceMap.getNodes().stream()
                .filter(node -> namespace == null || namespace.isEmpty()
                    || namespace.equals(node.getNamespace()))
                .collect(java.util.stream.Collectors.toList());

            sb.append("### 服务列表 (共 ").append(filteredNodes.size()).append(" 个)\n");
            int nodeCount = 0;
            for (ServiceMapNode node : filteredNodes) {
                if (nodeCount >= 50) {
                    sb.append("... (省略更多服务)\n");
                    break;
                }
                sb.append("- ").append(node.getServiceName());
                if (node.getStatus() != null && !"ok".equals(node.getStatus())) {
                    sb.append(" [状态: ").append(node.getStatus()).append("]");
                }
                if (node.getRequestCount() != null && node.getRequestCount() > 0) {
                    sb.append(" (请求数: ").append(node.getRequestCount()).append(")");
                }
                sb.append("\n");
                nodeCount++;
            }
            sb.append("\n");

            // 过滤涉及该命名空间服务的边
            Set<String> filteredServiceNames = filteredNodes.stream()
                .map(ServiceMapNode::getServiceName)
                .collect(java.util.stream.Collectors.toSet());

            List<ServiceMapEdge> filteredEdges = serviceMap.getEdges().stream()
                .filter(edge -> filteredServiceNames.contains(edge.getSourceService())
                    || filteredServiceNames.contains(edge.getTargetService()))
                .collect(java.util.stream.Collectors.toList());

            sb.append("### 服务调用关系 (共 ").append(filteredEdges.size()).append(" 条)\n");
            int edgeCount = 0;
            for (ServiceMapEdge edge : filteredEdges) {
                if (edgeCount >= 50) {
                    sb.append("... (省略更多关系)\n");
                    break;
                }
                sb.append("- ").append(edge.getSourceService()).append(" -> ").append(edge.getTargetService());
                if (edge.getCallCount() != null && edge.getCallCount() > 0) {
                    sb.append(" (调用数: ").append(edge.getCallCount()).append(")");
                }
                if (edge.getAvgLatency() != null && edge.getAvgLatency() > 0) {
                    sb.append(" (延迟: ").append(String.format("%.1f", edge.getAvgLatency())).append("ms)");
                }
                if (edge.getErrorRate() != null && edge.getErrorRate() > 0) {
                    sb.append(" [错误率: ").append(String.format("%.2f%%", edge.getErrorRate())).append("]");
                }
                sb.append("\n");
                edgeCount++;
            }
            sb.append("\n");
        } else {
            sb.append("### 注意：未获取到服务拓扑数据\n\n");
        }

        sb.append("### 分析要点\n");
        sb.append("1. 识别关键路径上的服务（被多个服务调用）\n");
        sb.append("2. 识别循环依赖\n");
        sb.append("3. 识别服务孤岛（没有调用关系的服务）\n");
        sb.append("4. 识别高错误率的调用链路\n");
        sb.append("5. 识别调用链过长的情况\n");
        sb.append("6. 识别状态异常的服务（critical/warning）\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
    }

    /**
     * 构建链路风险分析的 Prompt
     */
    public String buildTraceRiskPrompt(String traceId, JsonNode traceData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务：链路风险分析\n\n");
        sb.append("请分析以下链路追踪数据的风险点：\n\n");
        sb.append("### TraceId: ").append(traceId).append("\n\n");

        if (traceData != null && traceData.isArray() && traceData.size() > 0) {
            sb.append("### Span 列表\n");
            int count = 0;
            for (JsonNode span : traceData) {
                if (count >= 30) {
                    sb.append("... (省略更多 span)\n");
                    break;
                }
                // 支持两种字段格式: Coroot格式和标准格式
                String serviceName = getSpanField(span, "service", "ServiceName", "serviceName");
                String name = getSpanField(span, "name", "Name", "operationName");
                double duration = span.path("duration").asDouble(span.path("Duration").asDouble(0));
                String statusStr = getSpanField(span, "status", "StatusCode", "statusCode");
                boolean hasError = span.path("status").path("error").asBoolean(false);

                // 显示所有span，不过滤
                sb.append("- [").append(serviceName).append("] ").append(name);
                if (duration > 0) {
                    // duration 可能是毫秒或微秒，根据值大小判断
                    double durationMs = duration > 10000 ? duration / 1000.0 : duration;
                    sb.append(" (耗时: ").append(String.format("%.2f", durationMs)).append("ms)");
                }
                if (hasError) {
                    sb.append(" [错误]");
                } else if (!statusStr.isEmpty()) {
                    sb.append(" [状态: ").append(statusStr).append("]");
                }
                sb.append("\n");
                count++;
            }
            sb.append("\n");
            sb.append("**共计 ").append(traceData.size()).append(" 个 span**\n\n");
        } else {
            sb.append("### 注意：未获取到具体的Span数据\n\n");
        }

        sb.append("### 分析要点\n");
        sb.append("1. 识别性能瓶颈（耗时较长的服务）\n");
        sb.append("2. 识别错误和异常\n");
        sb.append("3. 识别延迟异常（耗时明显高于平均值）\n");
        sb.append("4. 识别关键服务节点\n");
        sb.append("5. 识别潜在故障点\n\n");

        sb.append(getOutputFormat());
        return sb.toString();
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

