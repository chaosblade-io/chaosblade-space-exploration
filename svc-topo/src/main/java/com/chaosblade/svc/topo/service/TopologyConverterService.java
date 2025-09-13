package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import com.chaosblade.svc.topo.util.EntityIdGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 拓扑结构转换服务
 * <p>
 * 功能：
 * 1. 将解析后的trace数据转换为图结构
 * 2. 使用JGraphT库构建有向图
 * 3. 生成符合demo-*.json格式的拓扑图
 */
@Service
public class TopologyConverterService {

    private static final Logger logger = LoggerFactory.getLogger(TopologyConverterService.class);

    @Autowired
    private TraceParserService traceParserService;

    // 存储当前拓扑图
    private TopologyGraph currentTopology;

    /**
     * 获取当前拓扑图
     */
    public TopologyGraph getCurrentTopology() {
        return currentTopology;
    }

    /**
     * 设置当前拓扑图
     */
    public void setCurrentTopology(TopologyGraph topology) {
        this.currentTopology = topology;
    }

    /**
     * 将TraceData转换为TopologyGraph
     */
    public TopologyGraph convertTraceToTopology(TraceData traceData) {
        logger.info("开始转换trace数据为拓扑图结构");

        TopologyGraph topology = new TopologyGraph();
        topology.getMetadata().setTitle("OpenTelemetry Trace 拓扑图");
        topology.getMetadata().setDescription("从trace数据生成的服务拓扑结构");

        if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
            logger.warn("trace数据为空，返回空拓扑图");
            return topology;
        }

        // 使用JGraphT构建内部图结构
        Graph<Node, Edge> jgraphGraph = new DirectedMultigraph<>(Edge.class);

        // 收集所有实体信息
        EntityCollector collector = collectEntities(traceData);

        // 1. 创建命名空间节点（1级）
        createNamespaceNodes(topology, jgraphGraph, collector);

        // 2. 创建服务节点（1级）
        createServiceNodes(topology, jgraphGraph, collector);

        // 3. 创建Pod节点（2级）
        createPodNodes(topology, jgraphGraph, collector);

        // 4. 创建Host节点（2级）
        createHostNodes(topology, jgraphGraph, collector);

        // 5. 创建RPC接口节点（3级）
        createRpcNodes(topology, jgraphGraph, collector);

        // 6. 创建关系边
        createRelationshipEdges(topology, jgraphGraph, collector);

        // 7. 计算RED指标
        calculateRedMetrics(topology, traceData);

        logger.info("拓扑图转换完成: {} 个节点, {} 条边", topology.getNodes().size(), topology.getEdges().size());

        return topology;
    }

    /**
     * 收集所有实体信息
     */
    private EntityCollector collectEntities(TraceData traceData) {
        EntityCollector collector = new EntityCollector();

        for (TraceData.TraceRecord record : traceData.getData()) {
            if (record.getProcesses() == null || record.getSpans() == null) {
                continue;
            }

            // 建立processId到ProcessData的映射
            Map<String, ProcessData> processes = record.getProcesses();

            // 收集服务和进程信息
            for (Map.Entry<String, ProcessData> entry : processes.entrySet()) {
                String processId = entry.getKey();
                ProcessData process = entry.getValue();

                collector.addService(process.getServiceName(), process);
                collector.addProcess(processId, process);
            }

            // 收集Span信息
            for (SpanData span : record.getSpans()) {
                ProcessData process = processes.get(span.getProcessId());
                if (process != null) {
                    collector.addSpan(span, process);
                }
            }
        }

        logger.info("收集实体完成: {} 个服务, {} 个进程, {} 个span", collector.services.size(), collector.processes.size(), collector.spans.size());

        return collector;
    }

    /**
     * 创建命名空间节点
     */
    private void createNamespaceNodes(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        // 收集所有命名空间
        Set<String> namespaces = collector.services.values().stream().map(ProcessData::getKubernetesNamespace).filter(Objects::nonNull).collect(Collectors.toSet());

        if (namespaces.isEmpty()) {
            namespaces.add("default"); // 默认命名空间
        }

        for (String namespace : namespaces) {
            Entity entity = new Entity("ns-" + namespace, EntityType.NAMESPACE, namespace + " Namespace");
            entity.setName(namespace);
            entity.setNamespace(namespace); // 设置 namespace 字段
            entity.setRegionId("unknown");

            Node node = new Node(entity.getEntityId(), entity);
            node.setRedMetrics(RedMetrics.success());

            topology.addNode(node);
            jgraph.addVertex(node);

            collector.namespaceNodes.put(namespace, node);
        }
    }

    /**
     * 创建服务节点
     */
    private void createServiceNodes(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        for (Map.Entry<String, ProcessData> entry : collector.services.entrySet()) {
            String serviceName = entry.getKey();
            ProcessData process = entry.getValue();

            Entity entity = new Entity("svc-" + serviceName, EntityType.SERVICE, serviceName + " Service");
            entity.setName(serviceName);
            entity.setNamespace(process.getKubernetesNamespace()); // 设置 namespace 字段
            entity.setAppId(serviceName + "@" + process.getKubernetesNamespace());
            entity.setRegionId(process.getRegion());

            Node node = new Node(entity.getEntityId(), entity);

            topology.addNode(node);
            jgraph.addVertex(node);

            collector.serviceNodes.put(serviceName, node);
        }
    }

    /**
     * 创建Pod节点
     */
    private void createPodNodes(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        // 根据服务和Pod名称创建Pod节点
        Map<String, Set<String>> servicePods = new HashMap<>();

        for (ProcessData process : collector.services.values()) {
            String serviceName = process.getServiceName();
            String podName = process.getKubernetesPodName();
            String ip = process.getIpAddress();

            if (podName != null) {
                servicePods.computeIfAbsent(serviceName, k -> new HashSet<>()).add(podName);

                Entity entity = new Entity("pod-" + podName, EntityType.POD, podName + " Pod");
                entity.setName(podName); // 设置 name 字段
                entity.setNamespace(process.getKubernetesNamespace()); // 设置 namespace 字段
                entity.setAppId(serviceName + "@" + process.getKubernetesNamespace());
                entity.setRegionId(process.getRegion());
                entity.addAttribute("ip", ip);
                entity.addAttribute("podName", podName);

                Node node = new Node(entity.getEntityId(), entity);

                topology.addNode(node);
                jgraph.addVertex(node);

                collector.podNodes.put(podName, node);
            }
        }
    }

    /**
     * 创建Host节点
     */
    private void createHostNodes(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        // 收集所有主机信息
        Set<String> hostNames = collector.services.values().stream().map(ProcessData::getHostName).filter(Objects::nonNull).collect(Collectors.toSet());

        if (hostNames.isEmpty()) {
            hostNames.add("Unknown"); // 默认主机名
        }

        for (String hostName : hostNames) {
            Entity entity = new Entity("host-" + hostName, EntityType.HOST, hostName + " Host");
            entity.setName(hostName);
            // Host 节点通常不直接关联到特定的 namespace，所以这里不设置 namespace 字段

            entity.setRegionId("unknown");

            Node node = new Node(entity.getEntityId(), entity);
            node.setRedMetrics(RedMetrics.success());

            topology.addNode(node);
            jgraph.addVertex(node);

            collector.hostNodes.put(hostName, node);
        }
    }

    /**
     * 创建RPC接口节点
     */
    private void createRpcNodes(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        // 收集RPC接口信息
        List<TraceParserService.RpcInterface> rpcInterfaces = traceParserService.extractRpcInterfaces(new TraceData() {{
            setData(Collections.singletonList(new TraceRecord() {{
                setSpans(new ArrayList<>(collector.spans.keySet()));
                setProcesses(collector.processes);
            }}));
        }});

        // 逐个服务看RPC接口
        Map<String, List<TraceParserService.RpcInterface>> serviceNames = rpcInterfaces.stream().collect(Collectors.groupingBy(TraceParserService.RpcInterface::getServiceName));

        for (Map.Entry<String, List<TraceParserService.RpcInterface>> entry : serviceNames.entrySet()) {
            String serviceName = entry.getKey();
            List<TraceParserService.RpcInterface> rpcs = entry.getValue();

            // 创建具体的RPC接口
            for (TraceParserService.RpcInterface rpc : rpcs) {
                String rpcId = EntityIdGenerator.generateRpcId(rpc.getInterfaceName());

                Entity rpcEntity = new Entity(rpcId, EntityType.RPC, rpc.getInterfaceName());
                rpcEntity.setName(rpc.getInterfaceName()); // 设置 name 字段
                // 设置 namespace 字段
                String namespace = "default";
                if (collector.services.containsKey(serviceName)) {
                    namespace = collector.services.get(serviceName).getKubernetesNamespace();
                }
                rpcEntity.setNamespace(namespace);
                rpcEntity.setAppId(serviceName + "@" + namespace);
                rpcEntity.setRegionId("unknown");
                rpcEntity.addAttribute("rpc", rpc.getInterfaceName());
                rpcEntity.addAttribute("protocol", rpc.getProtocol());
                
                // 添加 method 和 path 到 attributes 中（如果存在）
                if (rpc.getMethod() != null) {
                    rpcEntity.addAttribute("method", rpc.getMethod());
                }
                if (rpc.getPath() != null) {
                    rpcEntity.addAttribute("path", rpc.getPath());
                }

                Node rpcNode = new Node(rpcEntity.getEntityId(), rpcEntity);
                topology.addNode(rpcNode);
                jgraph.addVertex(rpcNode);

                collector.rpcNodes.put(rpcId, rpcNode);
                collector.rpcOperationToNode.put(rpc.getInterfaceName(), rpcNode);
            }
        }
    }

    /**
     * 创建关系边
     */
    private void createRelationshipEdges(TopologyGraph topology, Graph<Node, Edge> jgraph, EntityCollector collector) {
        // 1. 命名空间包含服务
        for (Node serviceNode : collector.serviceNodes.values()) {
            String namespace = serviceNode.getEntity().getNamespace();
            if (namespace == null) {
                continue;
            }
            Node namespaceNode = collector.namespaceNodes.get(namespace);
            if (namespaceNode != null) {
                createEdge(topology, jgraph, namespaceNode, serviceNode, RelationType.CONTAINS);
            }
        }

        // 2. 服务包含 Pod
        for (Node podNode : collector.podNodes.values()) {
            String serviceName = extractServiceNameFromAppId(podNode.getEntity().getAppId());
            Node serviceNode = collector.serviceNodes.get(serviceName);
            if (serviceNode != null) {
                createEdge(topology, jgraph, serviceNode, podNode, RelationType.CONTAINS);
            }
        }

        // 3. Pod运行在Host上
        for (Node podNode : collector.podNodes.values()) {
            String podName = (String) podNode.getEntity().getAttributes().get("podName");
            if (podName != null) {
                // 通过pod名称找到对应的process信息
                ProcessData podProcess = collector.processes.values().stream()
                    .filter(process -> podName.equals(process.getKubernetesPodName()))
                    .findFirst()
                    .orElse(null);

                if (podProcess != null && podProcess.getHostName() != null) {
                    Node hostNode = collector.hostNodes.get(podProcess.getHostName());
                    if (hostNode != null) {
                        createEdge(topology, jgraph, podNode, hostNode, RelationType.RUNS_ON);
                    }
                }
            }
        }

        // 4. 服务包含RPC接口（基于appId）
        for (Map.Entry<String, Node> entry : collector.serviceNodes.entrySet()) {
            String serviceName = entry.getKey();
            Node serviceNode = entry.getValue();

            // 查找该服务的RPC接口
            for (Node rpcNode : collector.rpcNodes.values()) {
                String rpcServiceName = extractServiceNameFromAppId(rpcNode.getEntity().getAppId());
                if (serviceName.equals(rpcServiceName)) {
                    createEdge(topology, jgraph, serviceNode, rpcNode, RelationType.CONTAINS);
                }
            }
        }

        // 5. 服务调用RPC接口（基于trace数据）
        List<TraceParserService.ServiceCall> serviceCalls = traceParserService.extractServiceCalls(new TraceData() {{
            setData(Collections.singletonList(new TraceRecord() {{
                setSpans(new ArrayList<>(collector.spans.keySet()));
                setProcesses(collector.processes);
            }}));
        }});

        for (TraceParserService.ServiceCall call : serviceCalls) {
            Node fromNode = collector.serviceNodes.get(call.getFromService());
            // 使用 operation 查找对应的 rpcNode
            Node toNode = collector.rpcOperationToNode.get(call.getOperation());

            if (fromNode != null && toNode != null) {
                createEdge(topology, jgraph, fromNode, toNode, RelationType.INVOKES);
            }
        }

        // 6. 服务间调用关系（基于trace数据）
        for (TraceParserService.ServiceCall call : serviceCalls) {
            Node fromNode = collector.serviceNodes.get(call.getFromService());
            Node toNode = collector.serviceNodes.get(call.getToService());

            if (fromNode != null && toNode != null) {
                createEdge(topology, jgraph, fromNode, toNode, RelationType.DEPENDS_ON);
            }
        }
    }

    /**
     * 创建边
     */
    private void createEdge(TopologyGraph topology, Graph<Node, Edge> jgraph, Node from, Node to, RelationType type) {
        String edgeId = from.getNodeId() + "-" + to.getNodeId() + "-" + type.name();

        Edge edge = new Edge(edgeId, from.getNodeId(), to.getNodeId(), type);
        edge.setRedMetrics(RedMetrics.success());

        topology.addEdge(edge);
        jgraph.addEdge(from, to, edge);
    }

    /**
     * 计算RED指标
     */
    private void calculateRedMetrics(TopologyGraph topology, TraceData traceData) {
        // 为每个节点和边计算RED指标
        Map<String, RedMetrics> nodeMetrics = new HashMap<>();
        Map<String, RedMetrics> edgeMetrics = new HashMap<>();

        // 从trace数据中提取指标
        if (traceData.getData() != null) {
            for (TraceData.TraceRecord record : traceData.getData()) {
                if (record.getSpans() == null) continue;

                for (SpanData span : record.getSpans()) {
                    // 更新节点指标
                    updateNodeMetrics(nodeMetrics, span);
                }
            }
        }

        // 应用指标到节点
        for (Node node : topology.getNodes()) {
            RedMetrics metrics = nodeMetrics.get(node.getNodeId());
            if (metrics != null) {
                node.setRedMetrics(metrics);
            } else {
                node.setRedMetrics(RedMetrics.success());
            }
        }

        // 应用指标到边
        for (Edge edge : topology.getEdges()) {
            RedMetrics metrics = edgeMetrics.get(edge.getEdgeId());
            if (metrics != null) {
                edge.setRedMetrics(metrics);
            } else {
                edge.setRedMetrics(RedMetrics.success());
            }
        }
    }

    // todo 接入 prom 指标数据

    /**
     * 更新节点指标
     */
    private void updateNodeMetrics(Map<String, RedMetrics> nodeMetrics, SpanData span) {
        // 这里可以根据具体业务逻辑更新指标
        // 示例实现：基于span的duration和error状态
        double duration = span.getDuration() != null ? span.getDuration() / 1000.0 : 0; // 转为毫秒
        boolean isError = span.isError();

        RedMetrics metrics = new RedMetrics(1, isError ? 1 : 0, duration, isError ? "error" : "success");

        // 可以根据span的信息映射到具体的节点ID
        // 这里简化处理
        String nodeId = "svc-" + (span.getServiceName() != null ? span.getServiceName() : "unknown");

        nodeMetrics.merge(nodeId, metrics, (existing, newMetrics) -> {
            existing.addMetrics(newMetrics);
            return existing;
        });
    }

    /**
     * 从AppId中提取服务名
     */
    private String extractServiceNameFromAppId(String appId) {
        if (appId == null) return null;
        String[] parts = appId.split("@");
        return parts[0];
    }

    /**
     * 实体收集器内部类
     */
    private static class EntityCollector {
        final Map<String, ProcessData> services = new HashMap<>();
        final Map<String, ProcessData> processes = new HashMap<>();
        final Map<SpanData, ProcessData> spans = new HashMap<>();

        final Map<String, Node> namespaceNodes = new HashMap<>();
        final Map<String, Node> serviceNodes = new HashMap<>();
        final Map<String, Node> podNodes = new HashMap<>();
        final Map<String, Node> hostNodes = new HashMap<>();
        final Map<String, Node> rpcNodes = new HashMap<>();
        final Map<String, Node> rpcOperationToNode = new HashMap<>();

        void addService(String serviceName, ProcessData process) {
            if (serviceName != null) {
                services.put(serviceName, process);
            }
        }

        void addProcess(String processId, ProcessData process) {
            processes.put(processId, process);
        }

        void addSpan(SpanData span, ProcessData process) {
            spans.put(span, process);
        }
    }
}
