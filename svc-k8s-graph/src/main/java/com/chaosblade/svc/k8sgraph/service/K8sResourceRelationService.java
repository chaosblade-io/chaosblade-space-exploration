package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.EdgeType;
import com.chaosblade.svc.k8sgraph.domain.GraphEdge;
import com.chaosblade.svc.k8sgraph.domain.GraphNode;
import com.chaosblade.svc.k8sgraph.domain.ResourceRelations;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * K8s 资源关系服务 - 查询任意 K8s 资源的技术关系
 */
@Service
public class K8sResourceRelationService {
    
    private static final Logger logger = LoggerFactory.getLogger(K8sResourceRelationService.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    /**
     * 获取 K8s 资源的关系
     * @param resourceType 资源类型（service, pod, deployment, node, ingress 等）
     * @param resourceName 资源名称
     * @param namespace 命名空间（可选，某些资源如 node 不需要）
     */
    public ResourceRelations getResourceRelations(String resourceType, String resourceName, String namespace) {
        logger.info("Getting resource relations: type={}, name={}, namespace={}", resourceType, resourceName, namespace);
        
        ResourceRelations relations = new ResourceRelations(resourceType, resourceName, namespace);
        
        String type = resourceType.toLowerCase();
        switch (type) {
            case "service":
                collectServiceRelations(relations, resourceName, namespace);
                break;
            case "pod":
                collectPodRelations(relations, resourceName, namespace);
                break;
            case "deployment":
                collectDeploymentRelations(relations, resourceName, namespace);
                break;
            case "statefulset":
                collectStatefulSetRelations(relations, resourceName, namespace);
                break;
            case "daemonset":
                collectDaemonSetRelations(relations, resourceName, namespace);
                break;
            case "node":
                collectNodeRelations(relations, resourceName);
                break;
            case "ingress":
                collectIngressRelations(relations, resourceName, namespace);
                break;
            default:
                logger.warn("Unsupported resource type: {}", resourceType);
        }
        
        return relations;
    }
    
    /**
     * Service 的关系：Pod, Ingress, Endpoints
     */
    private void collectServiceRelations(ResourceRelations relations, String name, String namespace) {
        try {
            io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (service == null) {
                logger.warn("Service not found: {}/{}", namespace, name);
                return;
            }
            
            // 设置当前资源节点
            relations.setResourceNode(createServiceNode(service));
            
            // 获取关联的 Pods
            Map<String, String> selector = service.getSpec() != null ? service.getSpec().getSelector() : null;
            if (selector != null && !selector.isEmpty()) {
                List<Pod> pods = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabels(selector)
                        .list()
                        .getItems();
                
                for (Pod pod : pods) {
                    GraphNode podNode = createPodNode(pod);
                    relations.addRelatedNode(podNode);
                    relations.addEdge(GraphEdge.create(EdgeType.SELECTS, 
                            relations.getResourceNode().getId(), podNode.getId()));
                }
            }
            
            // 获取路由到该 Service 的 Ingress
            List<Ingress> ingresses = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .list()
                    .getItems();
            
            for (Ingress ingress : ingresses) {
                if (ingressRoutesToService(ingress, name)) {
                    GraphNode ingressNode = createIngressNode(ingress);
                    relations.addRelatedNode(ingressNode);
                    relations.addEdge(GraphEdge.create(EdgeType.ROUTES_TO,
                            ingressNode.getId(), relations.getResourceNode().getId()));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to collect service relations: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Pod 的关系：Node, Service, Owner（Deployment/ReplicaSet 等）
     */
    private void collectPodRelations(ResourceRelations relations, String name, String namespace) {
        try {
            Pod pod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (pod == null) {
                logger.warn("Pod not found: {}/{}", namespace, name);
                return;
            }
            
            relations.setResourceNode(createPodNode(pod));
            
            // Pod 所在的 Node
            String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
            if (nodeName != null) {
                Node node = kubernetesClient.nodes().withName(nodeName).get();
                if (node != null) {
                    GraphNode nodeNode = createNodeNode(node);
                    relations.addRelatedNode(nodeNode);
                    relations.addEdge(GraphEdge.create(EdgeType.RUNS_ON,
                            relations.getResourceNode().getId(), nodeNode.getId()));
                }
            }
            
            // Pod 的 Owner
            collectPodOwners(relations, pod, namespace);

        } catch (Exception e) {
            logger.error("Failed to collect pod relations: {}", e.getMessage(), e);
        }
    }

    /**
     * Deployment 的关系：ReplicaSet, Pod
     */
    private void collectDeploymentRelations(ResourceRelations relations, String name, String namespace) {
        try {
            Deployment deployment = kubernetesClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (deployment == null) {
                logger.warn("Deployment not found: {}/{}", namespace, name);
                return;
            }

            relations.setResourceNode(createDeploymentNode(deployment));

            // 获取关联的 Pod
            Map<String, String> selector = deployment.getSpec().getSelector().getMatchLabels();
            if (selector != null) {
                collectWorkloadPods(relations, selector, namespace);
            }

        } catch (Exception e) {
            logger.error("Failed to collect deployment relations: {}", e.getMessage(), e);
        }
    }

    /**
     * StatefulSet 的关系：Pod
     */
    private void collectStatefulSetRelations(ResourceRelations relations, String name, String namespace) {
        try {
            StatefulSet sts = kubernetesClient.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (sts == null) {
                logger.warn("StatefulSet not found: {}/{}", namespace, name);
                return;
            }

            relations.setResourceNode(createStatefulSetNode(sts));
            collectWorkloadPods(relations, sts.getSpec().getSelector().getMatchLabels(), namespace);

        } catch (Exception e) {
            logger.error("Failed to collect statefulset relations: {}", e.getMessage(), e);
        }
    }

    /**
     * DaemonSet 的关系：Pod
     */
    private void collectDaemonSetRelations(ResourceRelations relations, String name, String namespace) {
        try {
            DaemonSet ds = kubernetesClient.apps().daemonSets()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (ds == null) {
                logger.warn("DaemonSet not found: {}/{}", namespace, name);
                return;
            }

            relations.setResourceNode(createDaemonSetNode(ds));
            collectWorkloadPods(relations, ds.getSpec().getSelector().getMatchLabels(), namespace);

        } catch (Exception e) {
            logger.error("Failed to collect daemonset relations: {}", e.getMessage(), e);
        }
    }

    /**
     * Node 的关系：Pod
     */
    private void collectNodeRelations(ResourceRelations relations, String name) {
        try {
            Node node = kubernetesClient.nodes().withName(name).get();

            if (node == null) {
                logger.warn("Node not found: {}", name);
                return;
            }

            relations.setResourceNode(createNodeNode(node));

            // 获取该 Node 上的所有 Pod
            List<Pod> pods = kubernetesClient.pods()
                    .inAnyNamespace()
                    .withField("spec.nodeName", name)
                    .list()
                    .getItems();

            for (Pod pod : pods) {
                GraphNode podNode = createPodNode(pod);
                relations.addRelatedNode(podNode);
                relations.addEdge(GraphEdge.create(EdgeType.RUNS_ON,
                        podNode.getId(), relations.getResourceNode().getId()));
            }

        } catch (Exception e) {
            logger.error("Failed to collect node relations: {}", e.getMessage(), e);
        }
    }

    /**
     * Ingress 的关系：Service
     */
    private void collectIngressRelations(ResourceRelations relations, String name, String namespace) {
        try {
            Ingress ingress = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (ingress == null) {
                logger.warn("Ingress not found: {}/{}", namespace, name);
                return;
            }

            relations.setResourceNode(createIngressNode(ingress));

            // 获取 Ingress 路由到的所有 Service
            Set<String> serviceNames = new HashSet<>();
            if (ingress.getSpec() != null && ingress.getSpec().getRules() != null) {
                for (IngressRule rule : ingress.getSpec().getRules()) {
                    if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                        for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                            if (path.getBackend() != null && path.getBackend().getService() != null) {
                                serviceNames.add(path.getBackend().getService().getName());
                            }
                        }
                    }
                }
            }

            for (String svcName : serviceNames) {
                io.fabric8.kubernetes.api.model.Service svc = kubernetesClient.services()
                        .inNamespace(namespace)
                        .withName(svcName)
                        .get();
                if (svc != null) {
                    GraphNode svcNode = createServiceNode(svc);
                    relations.addRelatedNode(svcNode);
                    relations.addEdge(GraphEdge.create(EdgeType.ROUTES_TO,
                            relations.getResourceNode().getId(), svcNode.getId()));
                }
            }

        } catch (Exception e) {
            logger.error("Failed to collect ingress relations: {}", e.getMessage(), e);
        }
    }

    // ========== 辅助方法 ==========

    private void collectWorkloadPods(ResourceRelations relations, Map<String, String> selector, String namespace) {
        if (selector == null || selector.isEmpty()) return;

        List<Pod> pods = kubernetesClient.pods()
                .inNamespace(namespace)
                .withLabels(selector)
                .list()
                .getItems();

        for (Pod pod : pods) {
            GraphNode podNode = createPodNode(pod);
            relations.addRelatedNode(podNode);
            relations.addEdge(GraphEdge.create(EdgeType.OWNS,
                    relations.getResourceNode().getId(), podNode.getId()));
        }
    }

    private void collectPodOwners(ResourceRelations relations, Pod pod, String namespace) {
        if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null) return;

        for (OwnerReference owner : pod.getMetadata().getOwnerReferences()) {
            GraphNode ownerNode = null;
            String kind = owner.getKind();
            String ownerName = owner.getName();

            switch (kind) {
                case "ReplicaSet":
                    ReplicaSet rs = kubernetesClient.apps().replicaSets()
                            .inNamespace(namespace).withName(ownerName).get();
                    if (rs != null) {
                        ownerNode = createReplicaSetNode(rs);
                    }
                    break;
                case "StatefulSet":
                    StatefulSet sts = kubernetesClient.apps().statefulSets()
                            .inNamespace(namespace).withName(ownerName).get();
                    if (sts != null) {
                        ownerNode = createStatefulSetNode(sts);
                    }
                    break;
                case "DaemonSet":
                    DaemonSet ds = kubernetesClient.apps().daemonSets()
                            .inNamespace(namespace).withName(ownerName).get();
                    if (ds != null) {
                        ownerNode = createDaemonSetNode(ds);
                    }
                    break;
            }

            if (ownerNode != null) {
                relations.addRelatedNode(ownerNode);
                relations.addEdge(GraphEdge.create(EdgeType.OWNS,
                        ownerNode.getId(), relations.getResourceNode().getId()));
            }
        }
    }

    private boolean ingressRoutesToService(Ingress ingress, String serviceName) {
        if (ingress.getSpec() == null || ingress.getSpec().getRules() == null) return false;
        for (IngressRule rule : ingress.getSpec().getRules()) {
            if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                    if (path.getBackend() != null && path.getBackend().getService() != null
                            && serviceName.equals(path.getBackend().getService().getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ========== Node 创建方法 ==========

    private GraphNode createServiceNode(io.fabric8.kubernetes.api.model.Service svc) {
        GraphNode node = new GraphNode();
        String ns = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.service", ns, name));
        node.setType("k8s.service");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createPodNode(Pod pod) {
        GraphNode node = new GraphNode();
        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.pod", ns, name));
        node.setType("k8s.pod");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createNodeNode(Node n) {
        GraphNode node = new GraphNode();
        String name = n.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.node", "", name));
        node.setType("k8s.node");
        node.setDomain("k8s");
        node.setName(name);
        return node;
    }

    private GraphNode createDeploymentNode(Deployment dep) {
        GraphNode node = new GraphNode();
        String ns = dep.getMetadata().getNamespace();
        String name = dep.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.deployment", ns, name));
        node.setType("k8s.deployment");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createReplicaSetNode(ReplicaSet rs) {
        GraphNode node = new GraphNode();
        String ns = rs.getMetadata().getNamespace();
        String name = rs.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.replicaset", ns, name));
        node.setType("k8s.replicaset");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createStatefulSetNode(StatefulSet sts) {
        GraphNode node = new GraphNode();
        String ns = sts.getMetadata().getNamespace();
        String name = sts.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.statefulset", ns, name));
        node.setType("k8s.statefulset");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createDaemonSetNode(DaemonSet ds) {
        GraphNode node = new GraphNode();
        String ns = ds.getMetadata().getNamespace();
        String name = ds.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.daemonset", ns, name));
        node.setType("k8s.daemonset");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }

    private GraphNode createIngressNode(Ingress ingress) {
        GraphNode node = new GraphNode();
        String ns = ingress.getMetadata().getNamespace();
        String name = ingress.getMetadata().getName();
        node.setId(GraphNode.generateId("k8s.ingress", ns, name));
        node.setType("k8s.ingress");
        node.setDomain("k8s");
        node.setName(name);
        node.setNamespace(ns);
        return node;
    }
}
