package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskexecutor.entity.ApiTopology;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyEdgeRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyNodeRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyRepository;
import com.chaosblade.svc.taskexecutor.repository.DetectionTaskRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TopologyLayerService {

  private final DetectionTaskRepository detectionTaskRepository;
  private final ApiTopologyRepository apiTopologyRepository;
  private final ApiTopologyNodeRepository nodeRepository;
  private final ApiTopologyEdgeRepository edgeRepository;

  public TopologyLayerService(
      DetectionTaskRepository detectionTaskRepository,
      ApiTopologyRepository apiTopologyRepository,
      ApiTopologyNodeRepository nodeRepository,
      ApiTopologyEdgeRepository edgeRepository) {
    this.detectionTaskRepository = detectionTaskRepository;
    this.apiTopologyRepository = apiTopologyRepository;
    this.nodeRepository = nodeRepository;
    this.edgeRepository = edgeRepository;
  }

  public List<Layer> getLayersByTaskId(Long taskId) {
    DetectionTask task =
        detectionTaskRepository
            .findById(taskId)
            .orElseThrow(
                () -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + taskId));

    // 必须使用 system_id + api_id 精确匹配拓扑，避免跨系统误配
    ApiTopology latest =
        apiTopologyRepository
            .findBySystemIdAndApiId(task.getSystemId(), task.getApiId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        "TOPOLOGY_NOT_FOUND",
                        "未找到该系统与API对应的拓扑: system_id="
                            + task.getSystemId()
                            + ", api_id="
                            + task.getApiId()));

    Long topologyId = latest.getId();
    List<ApiTopologyNode> nodes = nodeRepository.findByTopologyId(topologyId);
    List<ApiTopologyEdge> edges = edgeRepository.findByTopologyId(topologyId);

    return computeLayers(nodes, edges);
  }

  private List<Layer> computeLayers(List<ApiTopologyNode> allNodes, List<ApiTopologyEdge> edges) {
    Map<Long, ApiTopologyNode> nodeMap =
        allNodes.stream().collect(Collectors.toMap(ApiTopologyNode::getId, n -> n));

    // Build degree maps
    Map<Long, Integer> outDeg = new HashMap<>();
    Map<Long, Integer> inDeg = new HashMap<>();
    for (Long id : nodeMap.keySet()) {
      outDeg.put(id, 0);
      inDeg.put(id, 0);
    }
    for (ApiTopologyEdge e : edges) {
      if (!nodeMap.containsKey(e.getFromNodeId()) || !nodeMap.containsKey(e.getToNodeId())) {
        continue;
      }
      outDeg.computeIfPresent(e.getFromNodeId(), (k, v) -> v + 1);
      inDeg.computeIfPresent(e.getToNodeId(), (k, v) -> v + 1);
    }

    // Remove isolated nodes (no in and no out)
    Set<Long> active =
        nodeMap.keySet().stream()
            .filter(id -> (outDeg.getOrDefault(id, 0) + inDeg.getOrDefault(id, 0)) > 0)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    // Build adjacency (forward): from_node_id -> to_node_id (caller -> callee)
    Map<Long, Set<Long>> parents = new HashMap<>();
    Map<Long, Set<Long>> children = new HashMap<>();
    for (Long id : active) {
      parents.put(id, new HashSet<>());
      children.put(id, new HashSet<>());
    }
    for (ApiTopologyEdge e : edges) {
      Long u = e.getFromNodeId();
      Long v = e.getToNodeId();
      if (active.contains(u) && active.contains(v)) {
        children.get(u).add(v);
        parents.get(v).add(u);
      }
    }

    // 使用自底向上的层级定义：
    // 叶子（无出边）层=1；父节点层=1+max(子节点层)
    Map<Long, Integer> level = new LinkedHashMap<>();
    Set<Long> tempMark = new HashSet<>(); // 用于检测环

    java.util.function.Function<Long, Integer> dfs =
        new java.util.function.Function<>() {
          @Override
          public Integer apply(Long u) {
            if (level.containsKey(u)) return level.get(u);
            if (tempMark.contains(u)) {
              // 检测到环：保守处理，将该节点视为叶子（层=1），避免无限递归
              // 也可选择记录告警日志并采用其他策略
              level.put(u, 1);
              return 1;
            }
            tempMark.add(u);
            Set<Long> chs = children.getOrDefault(u, Collections.emptySet());
            int lv;
            if (chs.isEmpty()) {
              lv = 1;
            } else {
              int maxChild = 0;
              for (Long v : chs) {
                maxChild = Math.max(maxChild, this.apply(v));
              }
              lv = maxChild + 1;
            }
            tempMark.remove(u);
            level.put(u, lv);
            return lv;
          }
        };

    int maxLevel = 0;
    for (Long id : active) {
      maxLevel = Math.max(maxLevel, dfs.apply(id));
    }

    // 聚合到层 -> 节点
    Map<Integer, List<Layer.NodeInfo>> byLevel = new TreeMap<>(); // 从小到大（1 开始）
    for (Long id : active) {
      int lv = level.getOrDefault(id, 1);
      byLevel
          .computeIfAbsent(lv, k -> new ArrayList<>())
          .add(
              new Layer.NodeInfo(
                  id,
                  nodeMap.get(id).getName(),
                  nodeMap.get(id).getNodeKey(),
                  nodeMap.get(id).getProtocol() != null
                      ? nodeMap.get(id).getProtocol().name()
                      : null));
    }
    List<Layer> layers = new ArrayList<>();
    int idx = 1;
    for (Map.Entry<Integer, List<Layer.NodeInfo>> e : byLevel.entrySet()) {
      List<Layer.NodeInfo> nodeInfos =
          e.getValue().stream()
              .sorted(Comparator.comparing(Layer.NodeInfo::getId))
              .collect(Collectors.toList());
      layers.add(new Layer(idx++, nodeInfos));
    }
    return layers;
  }

  // DTOs
  public static class Layer {
    private int index;
    private List<NodeInfo> nodes;

    public Layer(int index, List<NodeInfo> nodes) {
      this.index = index;
      this.nodes = nodes;
    }

    public int getIndex() {
      return index;
    }

    public List<NodeInfo> getNodes() {
      return nodes;
    }

    public static class NodeInfo {
      private Long id;
      private String name;
      private String nodeKey;
      private String protocol;

      public NodeInfo(Long id, String name, String nodeKey, String protocol) {
        this.id = id;
        this.name = name;
        this.nodeKey = nodeKey;
        this.protocol = protocol;
      }

      public Long getId() {
        return id;
      }

      public String getName() {
        return name;
      }

      public String getNodeKey() {
        return nodeKey;
      }

      public String getProtocol() {
        return protocol;
      }
    }
  }
}
