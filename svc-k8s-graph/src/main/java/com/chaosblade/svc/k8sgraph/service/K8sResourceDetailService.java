package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.GraphNode;
import com.chaosblade.svc.k8sgraph.domain.ResourceDetail;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * K8s 资源详情服务 - 获取任意 K8s 资源的基本信息
 */
@Service
public class K8sResourceDetailService {
    
    private static final Logger logger = LoggerFactory.getLogger(K8sResourceDetailService.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    /**
     * 获取 K8s 资源的基本详情
     * @param resourceType 资源类型（service, pod, deployment, node, ingress 等）
     * @param resourceName 资源名称
     * @param namespace 命名空间（可选，某些资源如 node 不需要）
     */
    public ResourceDetail getResourceDetail(String resourceType, String resourceName, String namespace) {
        logger.info("Getting resource detail: type={}, name={}, namespace={}", resourceType, resourceName, namespace);
        
        ResourceDetail detail = new ResourceDetail(resourceType, resourceName, namespace);
        
        String type = resourceType.toLowerCase();
        switch (type) {
            case "service":
                collectServiceDetail(detail, resourceName, namespace);
                break;
            case "pod":
                collectPodDetail(detail, resourceName, namespace);
                break;
            case "deployment":
                collectDeploymentDetail(detail, resourceName, namespace);
                break;
            case "statefulset":
                collectStatefulSetDetail(detail, resourceName, namespace);
                break;
            case "daemonset":
                collectDaemonSetDetail(detail, resourceName, namespace);
                break;
            case "node":
                collectNodeDetail(detail, resourceName);
                break;
            case "ingress":
                collectIngressDetail(detail, resourceName, namespace);
                break;
            default:
                logger.warn("Unsupported resource type: {}", resourceType);
        }
        
        return detail;
    }
    
    private void collectServiceDetail(ResourceDetail detail, String name, String namespace) {
        try {
            io.fabric8.kubernetes.api.model.Service svc = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (svc == null) {
                logger.warn("Service not found: {}/{}", namespace, name);
                return;
            }
            
            fillCommonMetadata(detail, svc.getMetadata());
            detail.setResourceNode(createServiceNode(svc));
            
            // Service 特有属性
            if (svc.getSpec() != null) {
                detail.addProperty("type", svc.getSpec().getType());
                detail.addProperty("clusterIP", svc.getSpec().getClusterIP());
                detail.addProperty("ports", svc.getSpec().getPorts());
                detail.addProperty("selector", svc.getSpec().getSelector());
            }
            
        } catch (Exception e) {
            logger.error("Failed to get service detail: {}", e.getMessage(), e);
        }
    }
    
    private void collectPodDetail(ResourceDetail detail, String name, String namespace) {
        try {
            Pod pod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (pod == null) {
                logger.warn("Pod not found: {}/{}", namespace, name);
                return;
            }
            
            fillCommonMetadata(detail, pod.getMetadata());
            detail.setResourceNode(createPodNode(pod));
            
            // Pod 特有属性
            if (pod.getSpec() != null) {
                detail.addProperty("nodeName", pod.getSpec().getNodeName());
                detail.addProperty("hostIP", pod.getSpec().getHostNetwork());
                detail.addProperty("containers", pod.getSpec().getContainers().size());
            }
            if (pod.getStatus() != null) {
                detail.setStatus(pod.getStatus().getPhase());
                detail.addProperty("podIP", pod.getStatus().getPodIP());
                detail.addProperty("startTime", pod.getStatus().getStartTime());
            }
            
        } catch (Exception e) {
            logger.error("Failed to get pod detail: {}", e.getMessage(), e);
        }
    }
    
    private void collectDeploymentDetail(ResourceDetail detail, String name, String namespace) {
        try {
            Deployment dep = kubernetesClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            
            if (dep == null) {
                logger.warn("Deployment not found: {}/{}", namespace, name);
                return;
            }
            
            fillCommonMetadata(detail, dep.getMetadata());
            detail.setResourceNode(createDeploymentNode(dep));
            
            // Deployment 特有属性
            if (dep.getSpec() != null) {
                detail.addProperty("replicas", dep.getSpec().getReplicas());
                detail.addProperty("strategy", dep.getSpec().getStrategy() != null ? 
                        dep.getSpec().getStrategy().getType() : null);
            }
            if (dep.getStatus() != null) {
                detail.addProperty("readyReplicas", dep.getStatus().getReadyReplicas());
                detail.addProperty("availableReplicas", dep.getStatus().getAvailableReplicas());
                detail.setStatus(dep.getStatus().getReadyReplicas() != null && 
                        dep.getStatus().getReadyReplicas() > 0 ? "Available" : "Unavailable");
            }
            
        } catch (Exception e) {
            logger.error("Failed to get deployment detail: {}", e.getMessage(), e);
        }
    }

    private void collectStatefulSetDetail(ResourceDetail detail, String name, String namespace) {
        try {
            StatefulSet sts = kubernetesClient.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (sts == null) {
                logger.warn("StatefulSet not found: {}/{}", namespace, name);
                return;
            }

            fillCommonMetadata(detail, sts.getMetadata());
            detail.setResourceNode(createStatefulSetNode(sts));

            if (sts.getSpec() != null) {
                detail.addProperty("replicas", sts.getSpec().getReplicas());
                detail.addProperty("serviceName", sts.getSpec().getServiceName());
            }
            if (sts.getStatus() != null) {
                detail.addProperty("readyReplicas", sts.getStatus().getReadyReplicas());
                detail.setStatus(sts.getStatus().getReadyReplicas() != null &&
                        sts.getStatus().getReadyReplicas() > 0 ? "Available" : "Unavailable");
            }

        } catch (Exception e) {
            logger.error("Failed to get statefulset detail: {}", e.getMessage(), e);
        }
    }

    private void collectDaemonSetDetail(ResourceDetail detail, String name, String namespace) {
        try {
            DaemonSet ds = kubernetesClient.apps().daemonSets()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (ds == null) {
                logger.warn("DaemonSet not found: {}/{}", namespace, name);
                return;
            }

            fillCommonMetadata(detail, ds.getMetadata());
            detail.setResourceNode(createDaemonSetNode(ds));

            if (ds.getStatus() != null) {
                detail.addProperty("desiredNumberScheduled", ds.getStatus().getDesiredNumberScheduled());
                detail.addProperty("numberReady", ds.getStatus().getNumberReady());
                detail.setStatus(ds.getStatus().getNumberReady() != null &&
                        ds.getStatus().getNumberReady() > 0 ? "Available" : "Unavailable");
            }

        } catch (Exception e) {
            logger.error("Failed to get daemonset detail: {}", e.getMessage(), e);
        }
    }

    private void collectNodeDetail(ResourceDetail detail, String name) {
        try {
            Node node = kubernetesClient.nodes().withName(name).get();

            if (node == null) {
                logger.warn("Node not found: {}", name);
                return;
            }

            fillCommonMetadata(detail, node.getMetadata());
            detail.setResourceNode(createNodeNode(node));

            if (node.getStatus() != null) {
                if (node.getStatus().getConditions() != null) {
                    for (NodeCondition condition : node.getStatus().getConditions()) {
                        if ("Ready".equals(condition.getType())) {
                            detail.setStatus("True".equals(condition.getStatus()) ? "Ready" : "NotReady");
                            break;
                        }
                    }
                }
                if (node.getStatus().getAddresses() != null) {
                    for (NodeAddress addr : node.getStatus().getAddresses()) {
                        detail.addProperty(addr.getType(), addr.getAddress());
                    }
                }
                if (node.getStatus().getNodeInfo() != null) {
                    detail.addProperty("osImage", node.getStatus().getNodeInfo().getOsImage());
                    detail.addProperty("kubeletVersion", node.getStatus().getNodeInfo().getKubeletVersion());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to get node detail: {}", e.getMessage(), e);
        }
    }

    private void collectIngressDetail(ResourceDetail detail, String name, String namespace) {
        try {
            Ingress ingress = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (ingress == null) {
                logger.warn("Ingress not found: {}/{}", namespace, name);
                return;
            }

            fillCommonMetadata(detail, ingress.getMetadata());
            detail.setResourceNode(createIngressNode(ingress));
            detail.setStatus("Active");

            if (ingress.getSpec() != null) {
                detail.addProperty("ingressClassName", ingress.getSpec().getIngressClassName());
                detail.addProperty("rules", ingress.getSpec().getRules() != null ?
                        ingress.getSpec().getRules().size() : 0);
            }

        } catch (Exception e) {
            logger.error("Failed to get ingress detail: {}", e.getMessage(), e);
        }
    }

    // ========== 辅助方法 ==========

    private void fillCommonMetadata(ResourceDetail detail, ObjectMeta meta) {
        if (meta == null) return;
        detail.setUid(meta.getUid());
        detail.setCreationTimestamp(meta.getCreationTimestamp());
        if (meta.getLabels() != null) detail.setLabels(meta.getLabels());
        if (meta.getAnnotations() != null) detail.setAnnotations(meta.getAnnotations());
    }

    private GraphNode createServiceNode(io.fabric8.kubernetes.api.model.Service svc) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.service", svc.getMetadata().getNamespace(), svc.getMetadata().getName()));
        node.setType("k8s.service");
        node.setDomain("k8s");
        node.setName(svc.getMetadata().getName());
        node.setNamespace(svc.getMetadata().getNamespace());
        return node;
    }

    private GraphNode createPodNode(Pod pod) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.pod", pod.getMetadata().getNamespace(), pod.getMetadata().getName()));
        node.setType("k8s.pod");
        node.setDomain("k8s");
        node.setName(pod.getMetadata().getName());
        node.setNamespace(pod.getMetadata().getNamespace());
        return node;
    }

    private GraphNode createDeploymentNode(Deployment dep) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.deployment", dep.getMetadata().getNamespace(), dep.getMetadata().getName()));
        node.setType("k8s.deployment");
        node.setDomain("k8s");
        node.setName(dep.getMetadata().getName());
        node.setNamespace(dep.getMetadata().getNamespace());
        return node;
    }

    private GraphNode createStatefulSetNode(StatefulSet sts) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.statefulset", sts.getMetadata().getNamespace(), sts.getMetadata().getName()));
        node.setType("k8s.statefulset");
        node.setDomain("k8s");
        node.setName(sts.getMetadata().getName());
        node.setNamespace(sts.getMetadata().getNamespace());
        return node;
    }

    private GraphNode createDaemonSetNode(DaemonSet ds) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.daemonset", ds.getMetadata().getNamespace(), ds.getMetadata().getName()));
        node.setType("k8s.daemonset");
        node.setDomain("k8s");
        node.setName(ds.getMetadata().getName());
        node.setNamespace(ds.getMetadata().getNamespace());
        return node;
    }

    private GraphNode createNodeNode(Node n) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.node", "", n.getMetadata().getName()));
        node.setType("k8s.node");
        node.setDomain("k8s");
        node.setName(n.getMetadata().getName());
        return node;
    }

    private GraphNode createIngressNode(Ingress ingress) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.ingress", ingress.getMetadata().getNamespace(), ingress.getMetadata().getName()));
        node.setType("k8s.ingress");
        node.setDomain("k8s");
        node.setName(ingress.getMetadata().getName());
        node.setNamespace(ingress.getMetadata().getNamespace());
        return node;
    }
}
