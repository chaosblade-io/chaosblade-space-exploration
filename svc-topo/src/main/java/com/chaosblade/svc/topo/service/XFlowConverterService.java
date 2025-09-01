/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * XFlow 转换服务
 * 将拓扑图数据转换为 XFlow 格式
 */
@Service
public class XFlowConverterService {

    private static final Logger logger = LoggerFactory.getLogger(XFlowConverterService.class);

    @Autowired
    private TopologyConverterService topologyConverterService;

    /**
     * 获取当前拓扑的 XFlow 格式数据
     */
    public Map<String, Object> getCurrentTopologyAsXFlow() {
        try {
            TopologyGraph topology = topologyConverterService.getCurrentTopology();
            if (topology == null || topology.isEmpty()) {
                logger.warn("当前拓扑数据为空");
                return createEmptyXFlowData();
            }

            return convertToXFlow(topology);
        } catch (Exception e) {
            logger.error("转换拓扑数据为 XFlow 格式失败", e);
            throw new RuntimeException("转换拓扑数据失败", e);
        }
    }

    /**
     * 刷新并获取 XFlow 数据
     */
    public Map<String, Object> refreshAndGetXFlowData() {
        // todo 这里可以添加刷新逻辑，目前直接返回当前数据
        return getCurrentTopologyAsXFlow();
    }

    /**
     * 将拓扑图转换为 XFlow 格式
     */
    public Map<String, Object> convertToXFlow(TopologyGraph topology) {
        Map<String, Object> result = new HashMap<>();

        // 转换节点
        List<Map<String, Object>> nodes = convertNodes(new ArrayList<>(topology.getNodes()));
        result.put("nodes", nodes);

        // 转换边
        List<Map<String, Object>> edges = convertEdges(new ArrayList<>(topology.getEdges()));
        result.put("edges", edges);

        // 添加统计信息
        result.put("statistics", createStatistics(topology));

        // 添加元数据
        result.put("metadata", createMetadata());

        logger.info("成功转换拓扑数据为 XFlow 格式，节点数: {}, 边数: {}", nodes.size(), edges.size());

        return result;
    }

    /**
     * 转换节点数据
     */
    private List<Map<String, Object>> convertNodes(List<Node> nodes) {
        return nodes.stream()
            .map(this::convertNode)
            .collect(Collectors.toList());
    }

    /**
     * 转换单个节点
     */
    private Map<String, Object> convertNode(Node node) {
        Map<String, Object> xflowNode = new HashMap<>();

        // 基础属性
        xflowNode.put("id", node.getNodeId());
        xflowNode.put("label", node.getDisplayName());
        xflowNode.put("shape", getNodeShape(node.getEntityType()));

        // 位置信息 - 使用智能布局算法
        Position position = calculateNodePosition(node);
        xflowNode.put("x", position.getX());
        xflowNode.put("y", position.getY());

        // 尺寸信息
        Size size = getNodeSize(node.getEntityType());
        xflowNode.put("width", size.getWidth());
        xflowNode.put("height", size.getHeight());

        // 样式属性
        Map<String, Object> attrs = generateNodeAttributes(node);
        xflowNode.put("attrs", attrs);

        // 数据属性
        Map<String, Object> data = new HashMap<>();
        data.put("entity", node.getEntity());
        data.put("redMetrics", node.getRedMetrics());
        data.put("entityType", node.getEntityType().toString());
        data.put("status", determineNodeStatus(node.getRedMetrics()));
        // 添加显示名称到数据中
        data.put("displayName", node.getDisplayName());
        xflowNode.put("data", data);

        return xflowNode;
    }

    /**
     * 转换边数据
     */
    private List<Map<String, Object>> convertEdges(List<Edge> edges) {
        return edges.stream()
            .map(this::convertEdge)
            .collect(Collectors.toList());
    }

    /**
     * 转换单个边
     */
    private Map<String, Object> convertEdge(Edge edge) {
        Map<String, Object> xflowEdge = new HashMap<>();

        // 基础属性
        xflowEdge.put("id", edge.getEdgeId());
        xflowEdge.put("source", edge.getFrom());
        xflowEdge.put("target", edge.getTo());
        xflowEdge.put("shape", "edge");

        // 标签 - 显示边类型
        Map<String, Object> labelConfig = new HashMap<>();
        labelConfig.put("text", edge.getType().getDisplayName());
        labelConfig.put("fontSize", 6);
        labelConfig.put("fill", "#666");
        labelConfig.put("position", 0.5); // 标签位置在边的中间
        xflowEdge.put("label", labelConfig);

        // 样式属性
        Map<String, Object> attrs = generateEdgeAttributes(edge);
        xflowEdge.put("attrs", attrs);

        // 数据属性
        Map<String, Object> data = new HashMap<>();
        data.put("type", edge.getType().toString());
        data.put("redMetrics", edge.getRedMetrics());
        xflowEdge.put("data", data);

        return xflowEdge;
    }

    /**
     * 获取节点形状
     */
    private String getNodeShape(EntityType entityType) {
        switch (entityType) {
            case SERVICE:
                return "custom-service";
            case NAMESPACE:
                return "custom-namespace";
            case RPC:
                return "custom-rpc";
            case RPC_GROUP:
                return "custom-rpc-group";
            case HOST:
                return "custom-host";
            default:
                return "rect";
        }
    }

    /**
     * 获取节点尺寸
     */
    private Size getNodeSize(EntityType entityType) {
        switch (entityType) {
            case SERVICE:
                return new Size(120, 60);
            case NAMESPACE:
                return new Size(160, 80);
            case RPC:
                return new Size(100, 40);
            case RPC_GROUP:
                return new Size(140, 50);
            case HOST:
                return new Size(100, 50);
            default:
                return new Size(100, 50);
        }
    }

    /**
     * 计算节点位置 - 使用改进的布局算法
     */
    private Position calculateNodePosition(Node node) {
        String nodeId = node.getNodeId();
        int hash = Math.abs(nodeId.hashCode());

        // 根据实体类型调整布局
        EntityType entityType = node.getEntityType();
        int baseX = 0, baseY = 0;

        switch (entityType) {
            case NAMESPACE:
                baseX = 50;
                baseY = 50;
                break;
            case SERVICE:
                baseX = 200;
                baseY = 150;
                break;
            case HOST:
                baseX = 400;
                baseY = 50;
                break;
            case RPC_GROUP:
                baseX = 350;
                baseY = 250;
                break;
            case RPC:
                baseX = 500;
                baseY = 350;
                break;
        }

        // 添加随机偏移，避免重叠
        int offsetX = (hash % 5) * 40;
        int offsetY = ((hash / 5) % 4) * 30;

        return new Position(baseX + offsetX, baseY + offsetY);
    }

    /**
     * 生成节点属性
     */
    private Map<String, Object> generateNodeAttributes(Node node) {
        Map<String, Object> attrs = new HashMap<>();

        // 根据 RED 指标确定状态
        String status = determineNodeStatus(node.getRedMetrics());

        // 根据节点类型获取颜色配置
        Map<String, String> colorConfig = getNodeColorConfig(node.getEntityType(), status);

        // 基础样式
        Map<String, Object> body = new HashMap<>();
        body.put("fill", colorConfig.get("fill"));
        body.put("stroke", colorConfig.get("stroke"));
        body.put("strokeWidth", getStatusStrokeWidth(status));
        body.put("rx", 6); // 圆角
        body.put("ry", 6);
        attrs.put("body", body);

        // 文本样式
        Map<String, Object> text = new HashMap<>();
        text.put("fontSize", getFontSize(node.getEntityType()));
        text.put("fill", colorConfig.get("textColor"));
        text.put("textAnchor", "middle");
        text.put("textVerticalAnchor", "middle");
        text.put("textWrap", Map.of("width", "90%", "height", "90%"));
        // 添加节点名称作为文本内容
        text.put("text", node.getDisplayName());
        attrs.put("text", text);

        return attrs;
    }

    /**
     * 生成边属性
     */
    private Map<String, Object> generateEdgeAttributes(Edge edge) {
        Map<String, Object> attrs = new HashMap<>();

        // 线条样式
        Map<String, Object> line = new HashMap<>();
        line.put("stroke", getStrokeColor(edge.getType()));
        line.put("strokeWidth", getStrokeWidth(edge.getType()));

        // 配置箭头样式
        Map<String, Object> targetMarker = new HashMap<>();
        targetMarker.put("name", "classic");
        targetMarker.put("width", 8);
        targetMarker.put("height", 6);
        targetMarker.put("fill", getStrokeColor(edge.getType()));
        line.put("targetMarker", targetMarker);

        // 根据关系类型设置线条样式
        if (edge.getType() == RelationType.CONTAINS) {
            line.put("strokeDasharray", "5,5");
        }

        attrs.put("line", line);

        return attrs;
    }

    /**
     * 确定节点状态
     */
    private String determineNodeStatus(RedMetrics redMetrics) {
        if (redMetrics == null) return "unknown";

        if (!redMetrics.isHealthy()) return "error";
        if (redMetrics.getErrorRate() > 5) return "warning";
        if (redMetrics.getSuccessRate() < 95) return "warning";
        return "success";
    }

    /**
     * 根据节点类型和状态获取颜色配置
     */
    private Map<String, String> getNodeColorConfig(EntityType entityType, String status) {
        Map<String, String> config = new HashMap<>();

        // 根据节点类型设置基础颜色
        switch (entityType) {
            case NAMESPACE:
                config.put("fill", "#e6f7ff");     // 深蓝色背景
                config.put("stroke", "#1890ff");
                config.put("textColor", "#1890ff");
                break;
            case SERVICE:
                config.put("fill", "#f6ffed");     // 深绿色背景
                config.put("stroke", "#52c41a");
                config.put("textColor", "#52c41a");
                break;
            case HOST:
                config.put("fill", "#fff2e8");     // 橙色背景
                config.put("stroke", "#fa8c16");
                config.put("textColor", "#fa8c16");
                break;
            case RPC_GROUP:
                config.put("fill", "#f9f0ff");     // 紫色背景
                config.put("stroke", "#722ed1");
                config.put("textColor", "#722ed1");
                break;
            case RPC:
                config.put("fill", "#fff1f0");     // 红色背景
                config.put("stroke", "#f5222d");
                config.put("textColor", "#f5222d");
                break;
            default:
                config.put("fill", "#fafafa");
                config.put("stroke", "#d9d9d9");
                config.put("textColor", "#666");
        }

        // 根据状态调整颜色强度
        if ("error".equals(status)) {
            // 错误状态使用红色
            config.put("fill", "#fff2f0");
            config.put("stroke", "#ff4d4f");
            config.put("textColor", "#ff4d4f");
        } else if ("warning".equals(status)) {
            // 警告状态使用黄色
            config.put("fill", "#fffbe6");
            config.put("stroke", "#faad14");
            config.put("textColor", "#faad14");
        }

        return config;
    }

    /**
     * 获取状态边框颜色
     */
    private String getStatusStrokeColor(String status) {
        switch (status) {
            case "success":
                return "#52c41a";
            case "warning":
                return "#faad14";
            case "error":
                return "#ff4d4f";
            default:
                return "#d9d9d9";
        }
    }

    /**
     * 获取状态边框宽度
     */
    private int getStatusStrokeWidth(String status) {
        switch (status) {
            case "error":
                return 3;
            case "warning":
            case "success":
                return 2;
            default:
                return 1;
        }
    }

    /**
     * 获取文本颜色
     */
    private String getTextColor(String status) {
        switch (status) {
            case "success":
                return "#52c41a";
            case "warning":
                return "#faad14";
            case "error":
                return "#ff4d4f";
            default:
                return "#666";
        }
    }

    /**
     * 获取字体大小
     */
    private int getFontSize(EntityType entityType) {
        switch (entityType) {
            case NAMESPACE:
                return 14;
            case SERVICE:
                return 12;
            case HOST:
                return 12;
            case RPC_GROUP:
                return 11;
            case RPC:
                return 10;
            default:
                return 12;
        }
    }

    /**
     * 获取边标签
     */
    private String getEdgeLabel(RelationType type) {
        switch (type) {
            case DEPENDS_ON:
                return "依赖";
            case CONTAINS:
                return "包含";
            case INVOKES:
                return "调用";
            default:
                return null;
        }
    }

    /**
     * 获取边颜色
     */
    private String getStrokeColor(RelationType type) {
        switch (type) {
            case DEPENDS_ON:
                return "#1890ff";
            case CONTAINS:
                return "#52c41a";
            case INVOKES:
                return "#722ed1";
            default:
                return "#d9d9d9";
        }
    }

    /**
     * 获取边宽度
     */
    private int getStrokeWidth(RelationType type) {
        switch (type) {
            case DEPENDS_ON:
                return 2;
            case INVOKES:
                return 1;
            case CONTAINS:
                return 1;
            default:
                return 1;
        }
    }

    /**
     * 获取节点详情
     */
    public Map<String, Object> getNodeDetails(String nodeId) {
        try {
            TopologyGraph topology = topologyConverterService.getCurrentTopology();
            if (topology == null) {
                return Map.of();
            }

            Optional<Node> nodeOpt = topology.getNodes().stream()
                .filter(node -> node.getNodeId().equals(nodeId))
                .findFirst();

            if (nodeOpt.isPresent()) {
                Node node = nodeOpt.get();
                Map<String, Object> details = new HashMap<>();
                details.put("nodeId", node.getNodeId());
                details.put("displayName", node.getDisplayName());
                details.put("entityType", node.getEntityType());
                details.put("entity", node.getEntity());
                details.put("redMetrics", node.getRedMetrics());
                details.put("status", determineNodeStatus(node.getRedMetrics()));
                return details;
            }

            return Map.of();
        } catch (Exception e) {
            logger.error("获取节点详情失败: {}", nodeId, e);
            throw new RuntimeException("获取节点详情失败", e);
        }
    }

    /**
     * 应用布局算法
     */
    public Map<String, Object> applyLayout(String algorithm, String direction, Map<String, Object> options) {
        // 暂时返回当前数据，后续可以实现具体的布局算法
        Map<String, Object> data = getCurrentTopologyAsXFlow();

        // 这里可以根据算法和方向重新计算节点位置
        // 暂时使用简单的重新布局
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.get("nodes");

        if ("dagre".equals(algorithm)) {
            applyDagreLayout(nodes, direction);
        }

        return data;
    }

    /**
     * 应用 Dagre 布局
     */
    private void applyDagreLayout(List<Map<String, Object>> nodes, String direction) {
        // 简单的 Dagre 风格布局
        boolean isVertical = "TB".equals(direction) || "BT".equals(direction);

        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            if (isVertical) {
                node.put("x", (i % 4) * 200 + 100);
                node.put("y", (i / 4) * 150 + 100);
            } else {
                node.put("x", (i / 4) * 200 + 100);
                node.put("y", (i % 4) * 150 + 100);
            }
        }
    }

    /**
     * 创建空的 XFlow 数据
     */
    private Map<String, Object> createEmptyXFlowData() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", new ArrayList<>());
        result.put("edges", new ArrayList<>());
        result.put("statistics", Map.of(
            "nodeCount", 0,
            "edgeCount", 0
        ));
        result.put("metadata", createMetadata());
        return result;
    }

    /**
     * 创建统计信息
     */
    private Map<String, Object> createStatistics(TopologyGraph topology) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("nodeCount", topology.getNodes().size());
        stats.put("edgeCount", topology.getEdges().size());

        // 节点类型统计
        Map<EntityType, Long> nodeTypeCount = topology.getNodes().stream()
            .collect(Collectors.groupingBy(Node::getEntityType, Collectors.counting()));
        stats.put("nodeTypeCount", nodeTypeCount);

        // 边类型统计
        Map<RelationType, Long> edgeTypeCount = topology.getEdges().stream()
            .collect(Collectors.groupingBy(Edge::getType, Collectors.counting()));
        stats.put("edgeTypeCount", edgeTypeCount);

        return stats;
    }

    /**
     * 创建元数据
     */
    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "XFlow 拓扑图");
        metadata.put("description", "基于 XFlow 的微服务拓扑可视化");
        metadata.put("version", "1.0");
        metadata.put("createdAt", System.currentTimeMillis());
        metadata.put("updatedAt", System.currentTimeMillis());
        return metadata;
    }

    // 辅助类
    private static class Position {
        private final int x;
        private final int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    private static class Size {
        private final int width;
        private final int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
