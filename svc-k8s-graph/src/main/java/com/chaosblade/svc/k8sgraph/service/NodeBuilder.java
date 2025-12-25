package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点构建器 - 将K8s资源转换为图节点
 */
public final class NodeBuilder {
    
    private NodeBuilder() {}
    
    // ========== 基础设施域节点 ==========
    
    public static GraphNode createNamespaceNode(Namespace ns) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.NAMESPACE);
        node.setDomain(Domain.DOMAIN_INFRA);
        node.setName(ns.getMetadata().getName());
        node.setNamespace(null); // 集群级资源
        node.setId(GraphNode.generateId(node.getType(), null, node.getName()));
        node.setLabels(ns.getMetadata().getLabels() != null ? ns.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getNamespaceStatus(ns));
        node.addProperty("phase", ns.getStatus() != null ? ns.getStatus().getPhase() : "Unknown");
        return node;
    }
    
    public static GraphNode createK8sNodeNode(Node n) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.NODE);
        node.setDomain(Domain.DOMAIN_INFRA);
        node.setName(n.getMetadata().getName());
        node.setNamespace(null);
        node.setId(GraphNode.generateId(node.getType(), null, node.getName()));
        node.setLabels(n.getMetadata().getLabels() != null ? n.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getNodeStatus(n));
        
        if (n.getStatus() != null) {
            io.fabric8.kubernetes.api.model.NodeStatus nodeInfo = n.getStatus();
            if (nodeInfo.getNodeInfo() != null) {
                node.addProperty("kubeletVersion", nodeInfo.getNodeInfo().getKubeletVersion());
                node.addProperty("osImage", nodeInfo.getNodeInfo().getOsImage());
                node.addProperty("containerRuntime", nodeInfo.getNodeInfo().getContainerRuntimeVersion());
            }
            if (nodeInfo.getCapacity() != null) {
                node.addProperty("capacityCpu", nodeInfo.getCapacity().get("cpu"));
                node.addProperty("capacityMemory", nodeInfo.getCapacity().get("memory"));
            }
        }
        return node;
    }
    
    public static GraphNode createPVNode(PersistentVolume pv) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.PERSISTENT_VOLUME);
        node.setDomain(Domain.DOMAIN_INFRA);
        node.setName(pv.getMetadata().getName());
        node.setNamespace(null);
        node.setId(GraphNode.generateId(node.getType(), null, node.getName()));
        node.setLabels(pv.getMetadata().getLabels() != null ? pv.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getPVStatus(pv));
        
        if (pv.getSpec() != null) {
            node.addProperty("capacity", pv.getSpec().getCapacity());
            node.addProperty("accessModes", pv.getSpec().getAccessModes());
            node.addProperty("storageClassName", pv.getSpec().getStorageClassName());
        }
        if (pv.getStatus() != null) {
            node.addProperty("phase", pv.getStatus().getPhase());
        }
        return node;
    }
    
    public static GraphNode createStorageClassNode(io.fabric8.kubernetes.api.model.storage.StorageClass sc) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.STORAGE_CLASS);
        node.setDomain(Domain.DOMAIN_INFRA);
        node.setName(sc.getMetadata().getName());
        node.setNamespace(null);
        node.setId(GraphNode.generateId(node.getType(), null, node.getName()));
        node.setLabels(sc.getMetadata().getLabels() != null ? sc.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        node.addProperty("provisioner", sc.getProvisioner());
        node.addProperty("reclaimPolicy", sc.getReclaimPolicy());
        return node;
    }
    
    // ========== 工作负载域节点 ==========
    
    public static GraphNode createDeploymentNode(Deployment d) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.DEPLOYMENT);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(d.getMetadata().getName());
        node.setNamespace(d.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(d.getMetadata().getLabels() != null ? d.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getDeploymentStatus(d));
        
        if (d.getSpec() != null) {
            node.addProperty("replicas", d.getSpec().getReplicas());
            if (d.getSpec().getSelector() != null) {
                node.addProperty("selector", d.getSpec().getSelector().getMatchLabels());
            }
        }
        if (d.getStatus() != null) {
            node.addProperty("availableReplicas", d.getStatus().getAvailableReplicas());
            node.addProperty("readyReplicas", d.getStatus().getReadyReplicas());
        }
        return node;
    }
    
    public static GraphNode createPodNode(Pod p) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.POD);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(p.getMetadata().getName());
        node.setNamespace(p.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(p.getMetadata().getLabels() != null ? p.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getPodStatus(p));
        
        if (p.getStatus() != null) {
            node.addProperty("phase", p.getStatus().getPhase());
            node.addProperty("podIP", p.getStatus().getPodIP());
            node.addProperty("hostIP", p.getStatus().getHostIP());
        }
        if (p.getSpec() != null) {
            node.addProperty("nodeName", p.getSpec().getNodeName());
            node.addProperty("serviceAccountName", p.getSpec().getServiceAccountName());
        }
        // 存储OwnerReferences用于建立边关系
        if (p.getMetadata().getOwnerReferences() != null) {
            node.addProperty("ownerReferences", p.getMetadata().getOwnerReferences());
        }
        return node;
    }
    
    public static GraphNode createReplicaSetNode(ReplicaSet rs) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.REPLICA_SET);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(rs.getMetadata().getName());
        node.setNamespace(rs.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(rs.getMetadata().getLabels() != null ? rs.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getReplicaSetStatus(rs));
        
        if (rs.getSpec() != null) {
            node.addProperty("replicas", rs.getSpec().getReplicas());
        }
        if (rs.getMetadata().getOwnerReferences() != null) {
            node.addProperty("ownerReferences", rs.getMetadata().getOwnerReferences());
        }
        return node;
    }

    public static GraphNode createStatefulSetNode(StatefulSet ss) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.STATEFUL_SET);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(ss.getMetadata().getName());
        node.setNamespace(ss.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(ss.getMetadata().getLabels() != null ? ss.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getStatefulSetStatus(ss));
        if (ss.getSpec() != null) {
            node.addProperty("replicas", ss.getSpec().getReplicas());
            node.addProperty("serviceName", ss.getSpec().getServiceName());
        }
        return node;
    }

    public static GraphNode createDaemonSetNode(DaemonSet ds) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.DAEMON_SET);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(ds.getMetadata().getName());
        node.setNamespace(ds.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(ds.getMetadata().getLabels() != null ? ds.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getDaemonSetStatus(ds));
        if (ds.getStatus() != null) {
            node.addProperty("desiredNumberScheduled", ds.getStatus().getDesiredNumberScheduled());
            node.addProperty("numberReady", ds.getStatus().getNumberReady());
        }
        return node;
    }

    public static GraphNode createJobNode(Job j) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.JOB);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(j.getMetadata().getName());
        node.setNamespace(j.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(j.getMetadata().getLabels() != null ? j.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getJobStatus(j));
        if (j.getMetadata().getOwnerReferences() != null) {
            node.addProperty("ownerReferences", j.getMetadata().getOwnerReferences());
        }
        return node;
    }

    public static GraphNode createCronJobNode(CronJob cj) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.CRON_JOB);
        node.setDomain(Domain.DOMAIN_WORKLOAD);
        node.setName(cj.getMetadata().getName());
        node.setNamespace(cj.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(cj.getMetadata().getLabels() != null ? cj.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        if (cj.getSpec() != null) {
            node.addProperty("schedule", cj.getSpec().getSchedule());
            node.addProperty("suspend", cj.getSpec().getSuspend());
        }
        return node;
    }

    // ========== 网络域节点 ==========

    public static GraphNode createServiceNode(io.fabric8.kubernetes.api.model.Service s) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.SERVICE);
        node.setDomain(Domain.DOMAIN_NETWORK);
        node.setName(s.getMetadata().getName());
        node.setNamespace(s.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(s.getMetadata().getLabels() != null ? s.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        if (s.getSpec() != null) {
            node.addProperty("type", s.getSpec().getType());
            node.addProperty("clusterIP", s.getSpec().getClusterIP());
            node.addProperty("selector", s.getSpec().getSelector());
            node.addProperty("ports", s.getSpec().getPorts());
        }
        return node;
    }

    public static GraphNode createIngressNode(Ingress i) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.INGRESS);
        node.setDomain(Domain.DOMAIN_NETWORK);
        node.setName(i.getMetadata().getName());
        node.setNamespace(i.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(i.getMetadata().getLabels() != null ? i.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        if (i.getSpec() != null) {
            node.addProperty("ingressClassName", i.getSpec().getIngressClassName());
            node.addProperty("rules", i.getSpec().getRules());
        }
        return node;
    }

    public static GraphNode createNetworkPolicyNode(NetworkPolicy np) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.NETWORK_POLICY);
        node.setDomain(Domain.DOMAIN_NETWORK);
        node.setName(np.getMetadata().getName());
        node.setNamespace(np.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(np.getMetadata().getLabels() != null ? np.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        return node;
    }

    // ========== 配置与安全域节点 ==========

    public static GraphNode createConfigMapNode(ConfigMap cm) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.CONFIG_MAP);
        node.setDomain(Domain.DOMAIN_CONFIG);
        node.setName(cm.getMetadata().getName());
        node.setNamespace(cm.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(cm.getMetadata().getLabels() != null ? cm.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        node.addProperty("dataKeys", cm.getData() != null ? cm.getData().keySet() : null);
        return node;
    }

    public static GraphNode createSecretNode(Secret s) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.SECRET);
        node.setDomain(Domain.DOMAIN_CONFIG);
        node.setName(s.getMetadata().getName());
        node.setNamespace(s.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(s.getMetadata().getLabels() != null ? s.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        node.addProperty("type", s.getType());
        return node;
    }

    public static GraphNode createPVCNode(PersistentVolumeClaim pvc) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.PVC);
        node.setDomain(Domain.DOMAIN_CONFIG);
        node.setName(pvc.getMetadata().getName());
        node.setNamespace(pvc.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(pvc.getMetadata().getLabels() != null ? pvc.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(getPVCStatus(pvc));
        if (pvc.getStatus() != null) {
            node.addProperty("phase", pvc.getStatus().getPhase());
        }
        if (pvc.getSpec() != null) {
            node.addProperty("volumeName", pvc.getSpec().getVolumeName());
            node.addProperty("storageClassName", pvc.getSpec().getStorageClassName());
        }
        return node;
    }

    public static GraphNode createServiceAccountNode(ServiceAccount sa) {
        GraphNode node = new GraphNode();
        node.setType(ResourceType.SERVICE_ACCOUNT);
        node.setDomain(Domain.DOMAIN_CONFIG);
        node.setName(sa.getMetadata().getName());
        node.setNamespace(sa.getMetadata().getNamespace());
        node.setId(GraphNode.generateId(node.getType(), node.getNamespace(), node.getName()));
        node.setLabels(sa.getMetadata().getLabels() != null ? sa.getMetadata().getLabels() : new HashMap<>());
        node.setStatus(com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING);
        return node;
    }

    // ========== 状态判断方法 ==========

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getNamespaceStatus(Namespace ns) {
        if (ns.getStatus() != null && "Active".equals(ns.getStatus().getPhase())) {
            return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getNodeStatus(Node n) {
        if (n.getStatus() != null && n.getStatus().getConditions() != null) {
            for (NodeCondition c : n.getStatus().getConditions()) {
                if ("Ready".equals(c.getType()) && "True".equals(c.getStatus())) {
                    return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
                }
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.WARNING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getPVStatus(PersistentVolume pv) {
        if (pv.getStatus() != null) {
            String phase = pv.getStatus().getPhase();
            if ("Bound".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            if ("Available".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            if ("Released".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.WARNING;
            if ("Failed".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.ERROR;
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getDeploymentStatus(Deployment d) {
        if (d.getStatus() != null) {
            Integer replicas = d.getSpec() != null ? d.getSpec().getReplicas() : 0;
            Integer available = d.getStatus().getAvailableReplicas();
            if (available != null && available.equals(replicas)) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
            if (available != null && available > 0) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.WARNING;
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getPodStatus(Pod p) {
        if (p.getStatus() != null) {
            String phase = p.getStatus().getPhase();
            if ("Running".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            if ("Succeeded".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            if ("Failed".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.ERROR;
            if ("Unknown".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.WARNING;
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getReplicaSetStatus(ReplicaSet rs) {
        if (rs.getStatus() != null) {
            Integer replicas = rs.getSpec() != null ? rs.getSpec().getReplicas() : 0;
            Integer ready = rs.getStatus().getReadyReplicas();
            if (ready != null && ready.equals(replicas)) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getStatefulSetStatus(StatefulSet ss) {
        if (ss.getStatus() != null) {
            Integer replicas = ss.getSpec() != null ? ss.getSpec().getReplicas() : 0;
            Integer ready = ss.getStatus().getReadyReplicas();
            if (ready != null && ready.equals(replicas)) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getDaemonSetStatus(DaemonSet ds) {
        if (ds.getStatus() != null) {
            Integer desired = ds.getStatus().getDesiredNumberScheduled();
            Integer ready = ds.getStatus().getNumberReady();
            if (ready != null && ready.equals(desired)) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getJobStatus(Job j) {
        if (j.getStatus() != null) {
            if (j.getStatus().getSucceeded() != null && j.getStatus().getSucceeded() > 0) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
            if (j.getStatus().getFailed() != null && j.getStatus().getFailed() > 0) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.ERROR;
            }
            if (j.getStatus().getActive() != null && j.getStatus().getActive() > 0) {
                return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            }
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }

    private static com.chaosblade.svc.k8sgraph.domain.NodeStatus getPVCStatus(PersistentVolumeClaim pvc) {
        if (pvc.getStatus() != null) {
            String phase = pvc.getStatus().getPhase();
            if ("Bound".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.RUNNING;
            if ("Lost".equals(phase)) return com.chaosblade.svc.k8sgraph.domain.NodeStatus.ERROR;
        }
        return com.chaosblade.svc.k8sgraph.domain.NodeStatus.PENDING;
    }
}
