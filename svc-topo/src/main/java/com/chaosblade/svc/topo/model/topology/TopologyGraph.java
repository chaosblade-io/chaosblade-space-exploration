package com.chaosblade.svc.topo.model.topology;

import com.chaosblade.svc.topo.model.entity.Edge;
import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.entity.RelationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 拓扑图模型 符合demo-*.json格式的内存图结构
 *
 * <p>包含节点集合和边集合，提供图操作方法
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopologyGraph {

  /** 节点集合 */
  @JsonProperty("nodes")
  private List<Node> nodes;

  /** 边集合 */
  @JsonProperty("edges")
  private List<Edge> edges;

  /** 图的元数据 */
  @JsonProperty("metadata")
  private GraphMetadata metadata;

  // 内部索引，用于快速查找
  private transient Map<String, Node> nodeIndex;
  private transient Map<String, Edge> edgeIndex;
  private transient Map<String, List<Edge>> outgoingEdges;
  private transient Map<String, List<Edge>> incomingEdges;

  // 构造函数
  public TopologyGraph() {
    this.nodes = new ArrayList<>();
    this.edges = new ArrayList<>();
    this.metadata = new GraphMetadata();
    this.initializeIndexes();
  }

  private void initializeIndexes() {
    this.nodeIndex = new HashMap<>();
    this.edgeIndex = new HashMap<>();
    this.outgoingEdges = new HashMap<>();
    this.incomingEdges = new HashMap<>();
  }

  // Getter and Setter methods
  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
    rebuildIndexes();
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public void setEdges(List<Edge> edges) {
    this.edges = edges;
    rebuildIndexes();
  }

  public GraphMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(GraphMetadata metadata) {
    this.metadata = metadata;
  }

  // ========== 节点操作方法 ==========

  /** 添加节点 */
  public void addNode(Node node) {
    if (node == null || node.getNodeId() == null) {
      return;
    }

    if (!nodeIndex.containsKey(node.getNodeId())) {
      nodes.add(node);
      nodeIndex.put(node.getNodeId(), node);
      outgoingEdges.put(node.getNodeId(), new ArrayList<>());
      incomingEdges.put(node.getNodeId(), new ArrayList<>());
    }
  }

  /** 根据ID获取节点 */
  public Node getNode(String nodeId) {
    return nodeIndex.get(nodeId);
  }

  /** 根据实体类型获取节点列表 */
  public List<Node> getNodesByType(EntityType type) {
    return nodes.stream()
        .filter(node -> type.equals(node.getEntityType()))
        .collect(Collectors.toList());
  }

  /** 移除节点及其相关边 */
  public boolean removeNode(String nodeId) {
    Node node = nodeIndex.remove(nodeId);
    if (node == null) {
      return false;
    }

    nodes.remove(node);

    // 移除相关的边
    List<Edge> relatedEdges = new ArrayList<>();
    relatedEdges.addAll(outgoingEdges.getOrDefault(nodeId, new ArrayList<>()));
    relatedEdges.addAll(incomingEdges.getOrDefault(nodeId, new ArrayList<>()));

    for (Edge edge : relatedEdges) {
      removeEdge(edge.getEdgeId());
    }

    outgoingEdges.remove(nodeId);
    incomingEdges.remove(nodeId);

    return true;
  }

  // ========== 边操作方法 ==========

  /** 添加边 */
  public void addEdge(Edge edge) {
    if (edge == null || edge.getEdgeId() == null) {
      return;
    }

    // 确保源节点和目标节点存在
    if (!nodeIndex.containsKey(edge.getFrom()) || !nodeIndex.containsKey(edge.getTo())) {
      return;
    }

    if (!edgeIndex.containsKey(edge.getEdgeId())) {
      edges.add(edge);
      edgeIndex.put(edge.getEdgeId(), edge);

      outgoingEdges.get(edge.getFrom()).add(edge);
      incomingEdges.get(edge.getTo()).add(edge);
    }
  }

  /** 根据ID获取边 */
  public Edge getEdge(String edgeId) {
    return edgeIndex.get(edgeId);
  }

  /** 获取节点的出边 */
  public List<Edge> getOutgoingEdges(String nodeId) {
    return new ArrayList<>(outgoingEdges.getOrDefault(nodeId, new ArrayList<>()));
  }

  /** 获取节点的入边 */
  public List<Edge> getIncomingEdges(String nodeId) {
    return new ArrayList<>(incomingEdges.getOrDefault(nodeId, new ArrayList<>()));
  }

  /** 根据关系类型获取边列表 */
  public List<Edge> getEdgesByType(RelationType type) {
    return edges.stream().filter(edge -> type.equals(edge.getType())).collect(Collectors.toList());
  }

  /** 移除边 */
  public boolean removeEdge(String edgeId) {
    Edge edge = edgeIndex.remove(edgeId);
    if (edge == null) {
      return false;
    }

    edges.remove(edge);
    outgoingEdges.get(edge.getFrom()).remove(edge);
    incomingEdges.get(edge.getTo()).remove(edge);

    return true;
  }

  // ========== 图分析方法 ==========

  /** 获取节点的邻居 */
  public Set<String> getNeighbors(String nodeId) {
    Set<String> neighbors = new HashSet<>();

    for (Edge edge : getOutgoingEdges(nodeId)) {
      neighbors.add(edge.getTo());
    }
    for (Edge edge : getIncomingEdges(nodeId)) {
      neighbors.add(edge.getFrom());
    }

    return neighbors;
  }

  /** 检查两个节点是否连通 */
  public boolean isConnected(String nodeId1, String nodeId2) {
    return getNeighbors(nodeId1).contains(nodeId2);
  }

  /** 获取图的统计信息 */
  public GraphStatistics getStatistics() {
    Map<EntityType, Integer> nodeTypeCount = new HashMap<>();
    Map<RelationType, Integer> edgeTypeCount = new HashMap<>();

    for (Node node : nodes) {
      EntityType type = node.getEntityType();
      nodeTypeCount.put(type, nodeTypeCount.getOrDefault(type, 0) + 1);
    }

    for (Edge edge : edges) {
      RelationType type = edge.getType();
      edgeTypeCount.put(type, edgeTypeCount.getOrDefault(type, 0) + 1);
    }

    return new GraphStatistics(nodes.size(), edges.size(), nodeTypeCount, edgeTypeCount);
  }

  /** 重建内部索引 */
  public void rebuildIndexes() {
    initializeIndexes();

    for (Node node : nodes) {
      if (node.getNodeId() != null) {
        nodeIndex.put(node.getNodeId(), node);
        outgoingEdges.put(node.getNodeId(), new ArrayList<>());
        incomingEdges.put(node.getNodeId(), new ArrayList<>());
      }
    }

    for (Edge edge : edges) {
      if (edge.getEdgeId() != null) {
        edgeIndex.put(edge.getEdgeId(), edge);
        if (edge.getFrom() != null && edge.getTo() != null) {
          outgoingEdges.get(edge.getFrom()).add(edge);
          incomingEdges.get(edge.getTo()).add(edge);
        }
      }
    }
  }

  /** 清空图 */
  public void clear() {
    nodes.clear();
    edges.clear();
    initializeIndexes();
  }

  /** 检查图是否为空 */
  public boolean isEmpty() {
    return nodes.isEmpty() && edges.isEmpty();
  }

  @Override
  public String toString() {
    return "TopologyGraph{"
        + "nodes="
        + nodes.size()
        + ", edges="
        + edges.size()
        + ", metadata="
        + metadata
        + '}';
  }

  /** 图元数据类 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class GraphMetadata {
    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("createdAt")
    private Long createdAt;

    @JsonProperty("updatedAt")
    private Long updatedAt;

    public GraphMetadata() {
      this.createdAt = System.currentTimeMillis();
      this.updatedAt = this.createdAt;
    }

    // Getter and Setter methods
    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public Long getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
      this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  /** 图统计信息类 */
  public static class GraphStatistics {
    private final int nodeCount;
    private final int edgeCount;
    private final Map<EntityType, Integer> nodeTypeCount;
    private final Map<RelationType, Integer> edgeTypeCount;

    public GraphStatistics(
        int nodeCount,
        int edgeCount,
        Map<EntityType, Integer> nodeTypeCount,
        Map<RelationType, Integer> edgeTypeCount) {
      this.nodeCount = nodeCount;
      this.edgeCount = edgeCount;
      this.nodeTypeCount = nodeTypeCount;
      this.edgeTypeCount = edgeTypeCount;
    }

    public int getNodeCount() {
      return nodeCount;
    }

    public int getEdgeCount() {
      return edgeCount;
    }

    public Map<EntityType, Integer> getNodeTypeCount() {
      return nodeTypeCount;
    }

    public Map<RelationType, Integer> getEdgeTypeCount() {
      return edgeTypeCount;
    }

    @Override
    public String toString() {
      return "GraphStatistics{"
          + "nodeCount="
          + nodeCount
          + ", edgeCount="
          + edgeCount
          + ", nodeTypes="
          + nodeTypeCount
          + ", edgeTypes="
          + edgeTypeCount
          + '}';
    }
  }
}
