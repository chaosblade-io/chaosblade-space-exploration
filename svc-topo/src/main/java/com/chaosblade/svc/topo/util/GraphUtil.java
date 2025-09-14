package com.chaosblade.svc.topo.util;

import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图操作工具类
 *
 * 提供图分析和操作的实用方法
 */
public class GraphUtil {

    /**
     * 将TopologyGraph转换为JGraphT图
     */
    public static Graph<Node, Edge> convertToJGraphT(TopologyGraph topology) {
        Graph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);

        // 添加节点
        for (Node node : topology.getNodes()) {
            graph.addVertex(node);
        }

        // 添加边
        for (Edge edge : topology.getEdges()) {
            Node fromNode = topology.getNode(edge.getFrom());
            Node toNode = topology.getNode(edge.getTo());

            if (fromNode != null && toNode != null) {
                graph.addEdge(fromNode, toNode, edge);
            }
        }

        return graph;
    }

    /**
     * 查找两个节点之间的最短路径
     */
    public static List<Node> findShortestPath(TopologyGraph topology, String fromNodeId, String toNodeId) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);

        Node fromNode = topology.getNode(fromNodeId);
        Node toNode = topology.getNode(toNodeId);

        if (fromNode == null || toNode == null) {
            return Collections.emptyList();
        }

        DijkstraShortestPath<Node, Edge> dijkstra = new DijkstraShortestPath<>(graph);
        GraphPath<Node, Edge> path = dijkstra.getPath(fromNode, toNode);

        return path != null ? path.getVertexList() : Collections.emptyList();
    }

    /**
     * 获取节点的所有邻居（深度为1）
     */
    public static Set<Node> getNeighbors(TopologyGraph topology, String nodeId) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);
        Node node = topology.getNode(nodeId);

        if (node == null || !graph.containsVertex(node)) {
            return Collections.emptySet();
        }

        Set<Node> neighbors = new HashSet<>();

        // 出边的目标节点
        for (Edge edge : graph.outgoingEdgesOf(node)) {
            neighbors.add(graph.getEdgeTarget(edge));
        }

        // 入边的源节点
        for (Edge edge : graph.incomingEdgesOf(node)) {
            neighbors.add(graph.getEdgeSource(edge));
        }

        return neighbors;
    }

    /**
     * 获取节点的下游依赖（通过DEPENDS_ON关系）
     */
    public static Set<Node> getDownstreamDependencies(TopologyGraph topology, String nodeId) {
        return getRelatedNodes(topology, nodeId, RelationType.DEPENDS_ON, true);
    }

    /**
     * 获取节点的上游依赖（被哪些节点依赖）
     */
    public static Set<Node> getUpstreamDependencies(TopologyGraph topology, String nodeId) {
        return getRelatedNodes(topology, nodeId, RelationType.DEPENDS_ON, false);
    }

    /**
     * 获取通过特定关系类型连接的节点
     */
    public static Set<Node> getRelatedNodes(TopologyGraph topology, String nodeId,
                                          RelationType relationType, boolean outgoing) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);
        Node node = topology.getNode(nodeId);

        if (node == null || !graph.containsVertex(node)) {
            return Collections.emptySet();
        }

        Set<Node> relatedNodes = new HashSet<>();
        Set<Edge> edges = outgoing ? graph.outgoingEdgesOf(node) : graph.incomingEdgesOf(node);

        for (Edge edge : edges) {
            if (edge.getType() == relationType) {
                Node relatedNode = outgoing ? graph.getEdgeTarget(edge) : graph.getEdgeSource(edge);
                relatedNodes.add(relatedNode);
            }
        }

        return relatedNodes;
    }

    /**
     * 广度优先遍历
     */
    public static List<Node> breadthFirstTraversal(TopologyGraph topology, String startNodeId) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);
        Node startNode = topology.getNode(startNodeId);

        if (startNode == null || !graph.containsVertex(startNode)) {
            return Collections.emptyList();
        }

        List<Node> result = new ArrayList<>();
        BreadthFirstIterator<Node, Edge> iterator = new BreadthFirstIterator<>(graph, startNode);

        while (iterator.hasNext()) {
            result.add(iterator.next());
        }

        return result;
    }

    /**
     * 深度优先遍历
     */
    public static List<Node> depthFirstTraversal(TopologyGraph topology, String startNodeId) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);
        Node startNode = topology.getNode(startNodeId);

        if (startNode == null || !graph.containsVertex(startNode)) {
            return Collections.emptyList();
        }

        List<Node> result = new ArrayList<>();
        DepthFirstIterator<Node, Edge> iterator = new DepthFirstIterator<>(graph, startNode);

        while (iterator.hasNext()) {
            result.add(iterator.next());
        }

        return result;
    }

    /**
     * 查找根节点（没有入边的节点）
     */
    public static List<Node> findRootNodes(TopologyGraph topology) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);

        return graph.vertexSet().stream()
                .filter(node -> graph.incomingEdgesOf(node).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 查找叶子节点（没有出边的节点）
     */
    public static List<Node> findLeafNodes(TopologyGraph topology) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);

        return graph.vertexSet().stream()
                .filter(node -> graph.outgoingEdgesOf(node).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 检测图中是否存在环
     */
    public static boolean hasCycles(TopologyGraph topology) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);

        // 使用DFS检测环
        Set<Node> visited = new HashSet<>();
        Set<Node> recursionStack = new HashSet<>();

        for (Node node : graph.vertexSet()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(graph, node, visited, recursionStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * DFS检测环的辅助方法
     */
    private static boolean hasCycleDFS(Graph<Node, Edge> graph, Node node,
                                     Set<Node> visited, Set<Node> recursionStack) {
        visited.add(node);
        recursionStack.add(node);

        for (Edge edge : graph.outgoingEdgesOf(node)) {
            Node neighbor = graph.getEdgeTarget(edge);

            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(graph, neighbor, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                return true; // 发现环
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * 计算节点的度数（入度+出度）
     */
    public static Map<Node, Integer> calculateNodeDegrees(TopologyGraph topology) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);
        Map<Node, Integer> degrees = new HashMap<>();

        for (Node node : graph.vertexSet()) {
            int degree = graph.inDegreeOf(node) + graph.outDegreeOf(node);
            degrees.put(node, degree);
        }

        return degrees;
    }

    /**
     * 按层级对节点进行分组
     */
    public static Map<Integer, List<Node>> groupNodesByLevel(TopologyGraph topology) {
        Map<Integer, List<Node>> levelGroups = new HashMap<>();

        for (Node node : topology.getNodes()) {
            EntityType type = node.getEntityType();
            int level = type != null ? type.getLevel() : 0;

            levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(node);
        }

        return levelGroups;
    }

    /**
     * 查找关键路径（影响最多节点的路径）
     */
    public static List<Node> findCriticalPath(TopologyGraph topology) {
        Graph<Node, Edge> graph = convertToJGraphT(topology);

        // 简化算法：找到度数最高的节点作为关键节点
        Map<Node, Integer> degrees = calculateNodeDegrees(topology);

        return degrees.entrySet().stream()
                .sorted(Map.Entry.<Node, Integer>comparingByValue().reversed())
                .limit(10) // 取前10个关键节点
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 计算图的密度（边数/最大可能边数）
     */
    public static double calculateGraphDensity(TopologyGraph topology) {
        int nodeCount = topology.getNodes().size();
        int edgeCount = topology.getEdges().size();

        if (nodeCount <= 1) {
            return 0.0;
        }

        // 对于有向图，最大边数是 n*(n-1)
        int maxEdges = nodeCount * (nodeCount - 1);

        return (double) edgeCount / maxEdges;
    }

    /**
     * 验证拓扑图的一致性
     */
    public static List<String> validateTopology(TopologyGraph topology) {
        List<String> issues = new ArrayList<>();

        // 检查节点ID唯一性
        Set<String> nodeIds = new HashSet<>();
        for (Node node : topology.getNodes()) {
            if (node.getNodeId() == null) {
                issues.add("发现节点ID为null");
            } else if (!nodeIds.add(node.getNodeId())) {
                issues.add("发现重复的节点ID: " + node.getNodeId());
            }
        }

        // 检查边的引用完整性
        Set<String> existingNodeIds = nodeIds;
        for (Edge edge : topology.getEdges()) {
            if (!existingNodeIds.contains(edge.getFrom())) {
                issues.add("边引用了不存在的源节点: " + edge.getFrom());
            }
            if (!existingNodeIds.contains(edge.getTo())) {
                issues.add("边引用了不存在的目标节点: " + edge.getTo());
            }
        }

        // 检查自环
        for (Edge edge : topology.getEdges()) {
            if (Objects.equals(edge.getFrom(), edge.getTo())) {
                issues.add("发现自环: " + edge.getFrom());
            }
        }

        return issues;
    }
}
