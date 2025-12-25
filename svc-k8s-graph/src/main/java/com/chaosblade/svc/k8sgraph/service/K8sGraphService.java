package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.*;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapEdge;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.chaosblade.svc.k8sgraph.service.NodeBuilder.*;

/**
 * K8s资源拓扑图服务
 * 从K8s API获取资源并构建拓扑关系图
 */
@Service
public class K8sGraphService {

    private static final Logger logger = LoggerFactory.getLogger(K8sGraphService.class);

    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired(required = false)
    private ServiceMapService serviceMapService;
    
    /**
     * 获取完整的K8s资源拓扑图
     */
    public GraphData getFullGraph() {
        logger.info("Building full K8s resource graph...");
        
        GraphData graphData = new GraphData();
        
        // 添加四大领域定义
        graphData.addDomain(Domain.infra());
        graphData.addDomain(Domain.workload());
        graphData.addDomain(Domain.network());
        graphData.addDomain(Domain.config());
        
        // 用于存储节点ID以便建立边关系
        Map<String, GraphNode> nodeMap = new HashMap<>();
        
        // 1. 采集基础设施域资源
        collectInfraResources(graphData, nodeMap);
        
        // 2. 采集工作负载域资源
        collectWorkloadResources(graphData, nodeMap);
        
        // 3. 采集网络域资源
        collectNetworkResources(graphData, nodeMap);
        
        // 4. 采集配置与安全域资源
        collectConfigResources(graphData, nodeMap);
        
        // 5. 建立边关系
        buildEdges(graphData, nodeMap);
        
        // 6. 计算统计信息
        graphData.calculateStats();
        
        logger.info("Graph built: {} nodes, {} edges", 
            graphData.getNodes().size(), graphData.getEdges().size());
        
        return graphData;
    }
    
    /**
     * 获取指定命名空间的资源拓扑图
     */
    public GraphData getGraphByNamespace(String namespace) {
        logger.info("Building K8s resource graph for namespace: {}", namespace);
        
        GraphData graphData = new GraphData();
        
        graphData.addDomain(Domain.infra());
        graphData.addDomain(Domain.workload());
        graphData.addDomain(Domain.network());
        graphData.addDomain(Domain.config());
        
        Map<String, GraphNode> nodeMap = new HashMap<>();
        
        // 添加命名空间节点
        collectNamespaceNode(graphData, nodeMap, namespace);
        
        // 采集该命名空间下的资源
        collectWorkloadResourcesByNamespace(graphData, nodeMap, namespace);
        collectNetworkResourcesByNamespace(graphData, nodeMap, namespace);
        collectConfigResourcesByNamespace(graphData, nodeMap, namespace);
        
        // 建立边关系
        buildEdges(graphData, nodeMap);
        
        graphData.calculateStats();

        return graphData;
    }

    /**
     * 获取包含服务调用关系的完整拓扑图
     * @param fromMs 时间区间开始（毫秒时间戳）
     * @param toMs 时间区间结束（毫秒时间戳）
     */
    public GraphData getGraphWithServiceMap(long fromMs, long toMs) {
        logger.info("Building K8s resource graph with service map: from={}, to={}", fromMs, toMs);

        // 先获取基础 K8s 资源图
        GraphData graphData = getFullGraph();

        // 添加服务调用关系
        if (serviceMapService != null) {
            addServiceCallEdges(graphData, fromMs, toMs);
        }

        return graphData;
    }

    /**
     * 获取包含服务调用关系的拓扑图（支持命名空间过滤）
     * @param fromMs 时间区间开始（毫秒时间戳）
     * @param toMs 时间区间结束（毫秒时间戳）
     * @param namespaces 要包含的命名空间列表
     */
    public GraphData getGraphWithServiceMap(long fromMs, long toMs, List<String> namespaces) {
        logger.info("Building K8s resource graph with service map: from={}, to={}, namespaces={}",
                fromMs, toMs, namespaces);

        // 获取指定命名空间的 K8s 资源图
        GraphData graphData = getGraphByNamespaces(namespaces);

        // 添加服务调用关系
        if (serviceMapService != null) {
            addServiceCallEdges(graphData, fromMs, toMs);
        }

        return graphData;
    }

    /**
     * 获取多个命名空间的资源拓扑图
     */
    public GraphData getGraphByNamespaces(List<String> namespaces) {
        logger.info("Building K8s resource graph for namespaces: {}", namespaces);

        GraphData graphData = new GraphData();

        // 添加领域
        graphData.addDomain(Domain.infra());
        graphData.addDomain(Domain.workload());
        graphData.addDomain(Domain.network());
        graphData.addDomain(Domain.config());

        Map<String, GraphNode> nodeMap = new HashMap<>();

        // 收集指定命名空间的资源
        for (String namespace : namespaces) {
            collectNamespaceResources(graphData, nodeMap, namespace);
        }

        // 建立资源间的关系
        buildEdges(graphData, nodeMap);

        // 计算统计信息
        graphData.calculateStats();

        return graphData;
    }

    /**
     * 收集单个命名空间的资源
     */
    private void collectNamespaceResources(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        // 添加命名空间节点
        try {
            Namespace ns = kubernetesClient.namespaces().withName(namespace).get();
            if (ns != null) {
                GraphNode nsNode = createNamespaceNode(ns);
                graphData.addNode(nsNode);
                nodeMap.put(nsNode.getId(), nsNode);
            }
        } catch (Exception e) {
            logger.warn("Failed to get namespace {}: {}", namespace, e.getMessage());
        }

        // 收集该命名空间下的工作负载资源
        collectNamespaceWorkloads(graphData, nodeMap, namespace);

        // 收集该命名空间下的网络资源
        collectNamespaceNetworkResources(graphData, nodeMap, namespace);

        // 收集该命名空间下的配置资源
        collectNamespaceConfigResources(graphData, nodeMap, namespace);
    }

    private void collectNamespaceWorkloads(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        // Deployments
        try {
            List<Deployment> deployments = kubernetesClient.apps().deployments()
                    .inNamespace(namespace).list().getItems();
            for (Deployment deploy : deployments) {
                GraphNode node = createDeploymentNode(deploy);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list deployments in {}: {}", namespace, e.getMessage());
        }

        // Pods
        try {
            List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).list().getItems();
            for (Pod pod : pods) {
                GraphNode node = createPodNode(pod);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list pods in {}: {}", namespace, e.getMessage());
        }

        // StatefulSets
        try {
            List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets()
                    .inNamespace(namespace).list().getItems();
            for (StatefulSet ss : statefulSets) {
                GraphNode node = createStatefulSetNode(ss);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list statefulsets in {}: {}", namespace, e.getMessage());
        }

        // DaemonSets
        try {
            List<DaemonSet> daemonSets = kubernetesClient.apps().daemonSets()
                    .inNamespace(namespace).list().getItems();
            for (DaemonSet ds : daemonSets) {
                GraphNode node = createDaemonSetNode(ds);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list daemonsets in {}: {}", namespace, e.getMessage());
        }
    }

    private void collectNamespaceNetworkResources(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        // Services
        try {
            List<io.fabric8.kubernetes.api.model.Service> services = kubernetesClient.services()
                    .inNamespace(namespace).list().getItems();
            for (io.fabric8.kubernetes.api.model.Service svc : services) {
                GraphNode node = createServiceNode(svc);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list services in {}: {}", namespace, e.getMessage());
        }

        // Ingresses
        try {
            List<Ingress> ingresses = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace).list().getItems();
            for (Ingress ing : ingresses) {
                GraphNode node = createIngressNode(ing);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list ingresses in {}: {}", namespace, e.getMessage());
        }
    }

    private void collectNamespaceConfigResources(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        // ConfigMaps
        try {
            List<ConfigMap> configMaps = kubernetesClient.configMaps()
                    .inNamespace(namespace).list().getItems();
            for (ConfigMap cm : configMaps) {
                GraphNode node = createConfigMapNode(cm);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list configmaps in {}: {}", namespace, e.getMessage());
        }

        // Secrets
        try {
            List<Secret> secrets = kubernetesClient.secrets()
                    .inNamespace(namespace).list().getItems();
            for (Secret secret : secrets) {
                GraphNode node = createSecretNode(secret);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to list secrets in {}: {}", namespace, e.getMessage());
        }
    }

    /**
     * 添加服务调用关系边
     */
    private void addServiceCallEdges(GraphData graphData, long fromMs, long toMs) {
        try {
            ServiceMapData serviceMap = serviceMapService.getServiceMap(fromMs, toMs);

            // 建立服务名到节点ID的映射
            Map<String, String> serviceNameToNodeId = new HashMap<>();
            for (GraphNode node : graphData.getNodes()) {
                if (ResourceType.SERVICE.equals(node.getType())) {
                    // 使用服务名作为键
                    serviceNameToNodeId.put(node.getName(), node.getId());
                }
            }

            // 添加服务调用边
            for (ServiceMapEdge edge : serviceMap.getEdges()) {
                String sourceId = serviceNameToNodeId.get(edge.getSourceService());
                String targetId = serviceNameToNodeId.get(edge.getTargetService());

                if (sourceId != null && targetId != null) {
                    GraphEdge callEdge = GraphEdge.create(EdgeType.CALLS, sourceId, targetId);
                    // 添加调用指标到边属性
                    if (edge.getCallCount() != null) {
                        callEdge.getProperties().put("callCount", edge.getCallCount());
                    }
                    if (edge.getErrorRate() != null) {
                        callEdge.getProperties().put("errorRate", edge.getErrorRate());
                    }
                    if (edge.getAvgLatency() != null) {
                        callEdge.getProperties().put("avgLatency", edge.getAvgLatency());
                    }
                    graphData.addEdge(callEdge);
                }
            }

            logger.info("Added {} service call edges", serviceMap.getEdges().size());
        } catch (Exception e) {
            logger.warn("Failed to add service call edges: {}", e.getMessage(), e);
        }
    }

    // ========== 采集方法 ==========
    
    private void collectInfraResources(GraphData graphData, Map<String, GraphNode> nodeMap) {
        // Namespaces
        try {
            List<Namespace> namespaces = kubernetesClient.namespaces().list().getItems();
            for (Namespace ns : namespaces) {
                GraphNode node = createNamespaceNode(ns);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
            logger.debug("Collected {} namespaces", namespaces.size());
        } catch (Exception e) {
            logger.warn("Failed to collect namespaces: {}", e.getMessage());
        }
        
        // Nodes
        try {
            List<Node> nodes = kubernetesClient.nodes().list().getItems();
            for (Node n : nodes) {
                GraphNode node = createK8sNodeNode(n);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
            logger.debug("Collected {} nodes", nodes.size());
        } catch (Exception e) {
            logger.warn("Failed to collect nodes: {}", e.getMessage());
        }
        
        // PersistentVolumes
        try {
            List<PersistentVolume> pvs = kubernetesClient.persistentVolumes().list().getItems();
            for (PersistentVolume pv : pvs) {
                GraphNode node = createPVNode(pv);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect PVs: {}", e.getMessage());
        }
        
        // StorageClasses
        try {
            List<io.fabric8.kubernetes.api.model.storage.StorageClass> scs = 
                kubernetesClient.storage().storageClasses().list().getItems();
            for (io.fabric8.kubernetes.api.model.storage.StorageClass sc : scs) {
                GraphNode node = createStorageClassNode(sc);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect StorageClasses: {}", e.getMessage());
        }
    }
    
    private void collectWorkloadResources(GraphData graphData, Map<String, GraphNode> nodeMap) {
        collectDeployments(graphData, nodeMap, null);
        collectPods(graphData, nodeMap, null);
        collectReplicaSets(graphData, nodeMap, null);
        collectStatefulSets(graphData, nodeMap, null);
        collectDaemonSets(graphData, nodeMap, null);
        collectJobs(graphData, nodeMap, null);
        collectCronJobs(graphData, nodeMap, null);
    }

    private void collectWorkloadResourcesByNamespace(GraphData graphData, Map<String, GraphNode> nodeMap, String ns) {
        collectDeployments(graphData, nodeMap, ns);
        collectPods(graphData, nodeMap, ns);
        collectReplicaSets(graphData, nodeMap, ns);
        collectStatefulSets(graphData, nodeMap, ns);
        collectDaemonSets(graphData, nodeMap, ns);
        collectJobs(graphData, nodeMap, ns);
        collectCronJobs(graphData, nodeMap, ns);
    }

    private void collectNetworkResources(GraphData graphData, Map<String, GraphNode> nodeMap) {
        collectServices(graphData, nodeMap, null);
        collectIngresses(graphData, nodeMap, null);
        collectNetworkPolicies(graphData, nodeMap, null);
    }

    private void collectNetworkResourcesByNamespace(GraphData graphData, Map<String, GraphNode> nodeMap, String ns) {
        collectServices(graphData, nodeMap, ns);
        collectIngresses(graphData, nodeMap, ns);
        collectNetworkPolicies(graphData, nodeMap, ns);
    }

    private void collectConfigResources(GraphData graphData, Map<String, GraphNode> nodeMap) {
        collectConfigMaps(graphData, nodeMap, null);
        collectSecrets(graphData, nodeMap, null);
        collectPVCs(graphData, nodeMap, null);
        collectServiceAccounts(graphData, nodeMap, null);
    }

    private void collectConfigResourcesByNamespace(GraphData graphData, Map<String, GraphNode> nodeMap, String ns) {
        collectConfigMaps(graphData, nodeMap, ns);
        collectSecrets(graphData, nodeMap, ns);
        collectPVCs(graphData, nodeMap, ns);
        collectServiceAccounts(graphData, nodeMap, ns);
    }

    // ========== 具体采集方法 ==========

    private void collectDeployments(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<Deployment> items = namespace == null
                ? kubernetesClient.apps().deployments().inAnyNamespace().list().getItems()
                : kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems();
            for (Deployment d : items) {
                GraphNode node = createDeploymentNode(d);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Deployments: {}", e.getMessage());
        }
    }

    private void collectPods(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<Pod> items = namespace == null
                ? kubernetesClient.pods().inAnyNamespace().list().getItems()
                : kubernetesClient.pods().inNamespace(namespace).list().getItems();
            for (Pod p : items) {
                GraphNode node = createPodNode(p);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Pods: {}", e.getMessage());
        }
    }

    private void collectReplicaSets(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<ReplicaSet> items = namespace == null
                ? kubernetesClient.apps().replicaSets().inAnyNamespace().list().getItems()
                : kubernetesClient.apps().replicaSets().inNamespace(namespace).list().getItems();
            for (ReplicaSet rs : items) {
                GraphNode node = createReplicaSetNode(rs);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect ReplicaSets: {}", e.getMessage());
        }
    }

    private void collectStatefulSets(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<StatefulSet> items = namespace == null
                ? kubernetesClient.apps().statefulSets().inAnyNamespace().list().getItems()
                : kubernetesClient.apps().statefulSets().inNamespace(namespace).list().getItems();
            for (StatefulSet ss : items) {
                GraphNode node = createStatefulSetNode(ss);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect StatefulSets: {}", e.getMessage());
        }
    }

    private void collectDaemonSets(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<DaemonSet> items = namespace == null
                ? kubernetesClient.apps().daemonSets().inAnyNamespace().list().getItems()
                : kubernetesClient.apps().daemonSets().inNamespace(namespace).list().getItems();
            for (DaemonSet ds : items) {
                GraphNode node = createDaemonSetNode(ds);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect DaemonSets: {}", e.getMessage());
        }
    }

    private void collectJobs(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<Job> items = namespace == null
                ? kubernetesClient.batch().v1().jobs().inAnyNamespace().list().getItems()
                : kubernetesClient.batch().v1().jobs().inNamespace(namespace).list().getItems();
            for (Job j : items) {
                GraphNode node = createJobNode(j);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Jobs: {}", e.getMessage());
        }
    }

    private void collectCronJobs(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<CronJob> items = namespace == null
                ? kubernetesClient.batch().v1().cronjobs().inAnyNamespace().list().getItems()
                : kubernetesClient.batch().v1().cronjobs().inNamespace(namespace).list().getItems();
            for (CronJob cj : items) {
                GraphNode node = createCronJobNode(cj);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect CronJobs: {}", e.getMessage());
        }
    }

    private void collectServices(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<io.fabric8.kubernetes.api.model.Service> items = namespace == null
                ? kubernetesClient.services().inAnyNamespace().list().getItems()
                : kubernetesClient.services().inNamespace(namespace).list().getItems();
            for (io.fabric8.kubernetes.api.model.Service s : items) {
                GraphNode node = createServiceNode(s);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Services: {}", e.getMessage());
        }
    }

    private void collectIngresses(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<Ingress> items = namespace == null
                ? kubernetesClient.network().v1().ingresses().inAnyNamespace().list().getItems()
                : kubernetesClient.network().v1().ingresses().inNamespace(namespace).list().getItems();
            for (Ingress i : items) {
                GraphNode node = createIngressNode(i);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Ingresses: {}", e.getMessage());
        }
    }

    private void collectNetworkPolicies(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<NetworkPolicy> items = namespace == null
                ? kubernetesClient.network().v1().networkPolicies().inAnyNamespace().list().getItems()
                : kubernetesClient.network().v1().networkPolicies().inNamespace(namespace).list().getItems();
            for (NetworkPolicy np : items) {
                GraphNode node = createNetworkPolicyNode(np);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect NetworkPolicies: {}", e.getMessage());
        }
    }

    private void collectConfigMaps(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<ConfigMap> items = namespace == null
                ? kubernetesClient.configMaps().inAnyNamespace().list().getItems()
                : kubernetesClient.configMaps().inNamespace(namespace).list().getItems();
            for (ConfigMap cm : items) {
                GraphNode node = createConfigMapNode(cm);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect ConfigMaps: {}", e.getMessage());
        }
    }

    private void collectSecrets(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<Secret> items = namespace == null
                ? kubernetesClient.secrets().inAnyNamespace().list().getItems()
                : kubernetesClient.secrets().inNamespace(namespace).list().getItems();
            for (Secret s : items) {
                GraphNode node = createSecretNode(s);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect Secrets: {}", e.getMessage());
        }
    }

    private void collectPVCs(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<PersistentVolumeClaim> items = namespace == null
                ? kubernetesClient.persistentVolumeClaims().inAnyNamespace().list().getItems()
                : kubernetesClient.persistentVolumeClaims().inNamespace(namespace).list().getItems();
            for (PersistentVolumeClaim pvc : items) {
                GraphNode node = createPVCNode(pvc);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect PVCs: {}", e.getMessage());
        }
    }

    private void collectServiceAccounts(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            List<ServiceAccount> items = namespace == null
                ? kubernetesClient.serviceAccounts().inAnyNamespace().list().getItems()
                : kubernetesClient.serviceAccounts().inNamespace(namespace).list().getItems();
            for (ServiceAccount sa : items) {
                GraphNode node = createServiceAccountNode(sa);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect ServiceAccounts: {}", e.getMessage());
        }
    }

    private void collectNamespaceNode(GraphData graphData, Map<String, GraphNode> nodeMap, String namespace) {
        try {
            Namespace ns = kubernetesClient.namespaces().withName(namespace).get();
            if (ns != null) {
                GraphNode node = createNamespaceNode(ns);
                graphData.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect namespace {}: {}", namespace, e.getMessage());
        }
    }

    // ========== 边关系构建 ==========

    @SuppressWarnings("unchecked")
    private void buildEdges(GraphData graphData, Map<String, GraphNode> nodeMap) {
        logger.debug("Building edge relationships...");

        for (GraphNode node : graphData.getNodes()) {
            String nodeType = node.getType();

            // 1. Namespace contains 关系
            if (node.getNamespace() != null && !node.getNamespace().isEmpty()) {
                String nsId = GraphNode.generateId(ResourceType.NAMESPACE, null, node.getNamespace());
                if (nodeMap.containsKey(nsId)) {
                    graphData.addEdge(GraphEdge.create(EdgeType.CONTAINS, nsId, node.getId()));
                }
            }

            // 2. Pod runs_on Node 关系
            if (ResourceType.POD.equals(nodeType)) {
                String nodeName = (String) node.getProperties().get("nodeName");
                if (nodeName != null) {
                    String k8sNodeId = GraphNode.generateId(ResourceType.NODE, null, nodeName);
                    if (nodeMap.containsKey(k8sNodeId)) {
                        graphData.addEdge(GraphEdge.create(EdgeType.RUNS_ON, node.getId(), k8sNodeId));
                    }
                }

                // Pod uses ServiceAccount
                String saName = (String) node.getProperties().get("serviceAccountName");
                if (saName != null && node.getNamespace() != null) {
                    String saId = GraphNode.generateId(ResourceType.SERVICE_ACCOUNT, node.getNamespace(), saName);
                    if (nodeMap.containsKey(saId)) {
                        graphData.addEdge(GraphEdge.create(EdgeType.USES, node.getId(), saId));
                    }
                }
            }

            // 3. OwnerReferences 关系 (owns/manages)
            List<OwnerReference> owners = (List<OwnerReference>) node.getProperties().get("ownerReferences");
            if (owners != null) {
                for (OwnerReference owner : owners) {
                    String ownerType = mapOwnerKindToResourceType(owner.getKind());
                    if (ownerType != null) {
                        String ownerId = GraphNode.generateId(ownerType, node.getNamespace(), owner.getName());
                        if (nodeMap.containsKey(ownerId)) {
                            graphData.addEdge(GraphEdge.create(EdgeType.OWNS, ownerId, node.getId()));
                        }
                    }
                }
            }

            // 4. Service selects Pod 关系
            if (ResourceType.SERVICE.equals(nodeType)) {
                Map<String, String> selector = (Map<String, String>) node.getProperties().get("selector");
                if (selector != null && !selector.isEmpty()) {
                    for (GraphNode podNode : graphData.getNodes()) {
                        if (ResourceType.POD.equals(podNode.getType())
                            && node.getNamespace().equals(podNode.getNamespace())
                            && matchesLabels(podNode.getLabels(), selector)) {
                            graphData.addEdge(GraphEdge.create(EdgeType.SELECTS, node.getId(), podNode.getId()));
                        }
                    }
                }
            }

            // 5. Ingress routes_to Service 关系
            if (ResourceType.INGRESS.equals(nodeType)) {
                buildIngressToServiceEdges(graphData, node, nodeMap);
            }

            // 6. PVC claims PV 关系
            if (ResourceType.PVC.equals(nodeType)) {
                String volumeName = (String) node.getProperties().get("volumeName");
                if (volumeName != null) {
                    String pvId = GraphNode.generateId(ResourceType.PERSISTENT_VOLUME, null, volumeName);
                    if (nodeMap.containsKey(pvId)) {
                        graphData.addEdge(GraphEdge.create(EdgeType.CLAIMS, node.getId(), pvId));
                    }
                }
            }
        }

        logger.debug("Built {} edges", graphData.getEdges().size());
    }

    private String mapOwnerKindToResourceType(String kind) {
        switch (kind) {
            case "Deployment": return ResourceType.DEPLOYMENT;
            case "ReplicaSet": return ResourceType.REPLICA_SET;
            case "StatefulSet": return ResourceType.STATEFUL_SET;
            case "DaemonSet": return ResourceType.DAEMON_SET;
            case "Job": return ResourceType.JOB;
            case "CronJob": return ResourceType.CRON_JOB;
            default: return null;
        }
    }

    private boolean matchesLabels(Map<String, String> labels, Map<String, String> selector) {
        if (labels == null || selector == null) return false;
        for (Map.Entry<String, String> entry : selector.entrySet()) {
            if (!entry.getValue().equals(labels.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void buildIngressToServiceEdges(GraphData graphData, GraphNode ingressNode, Map<String, GraphNode> nodeMap) {
        List<?> rules = (List<?>) ingressNode.getProperties().get("rules");
        if (rules == null) return;

        // 简化处理：遍历规则中的后端服务
        for (Object rule : rules) {
            if (rule instanceof io.fabric8.kubernetes.api.model.networking.v1.IngressRule) {
                io.fabric8.kubernetes.api.model.networking.v1.IngressRule ingressRule =
                    (io.fabric8.kubernetes.api.model.networking.v1.IngressRule) rule;
                if (ingressRule.getHttp() != null && ingressRule.getHttp().getPaths() != null) {
                    for (io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath path : ingressRule.getHttp().getPaths()) {
                        if (path.getBackend() != null && path.getBackend().getService() != null) {
                            String serviceName = path.getBackend().getService().getName();
                            String serviceId = GraphNode.generateId(ResourceType.SERVICE, ingressNode.getNamespace(), serviceName);
                            if (nodeMap.containsKey(serviceId)) {
                                graphData.addEdge(GraphEdge.create(EdgeType.ROUTES_TO, ingressNode.getId(), serviceId));
                            }
                        }
                    }
                }
            }
        }
    }
}
