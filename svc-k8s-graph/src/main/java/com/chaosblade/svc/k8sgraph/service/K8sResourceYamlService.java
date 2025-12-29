package com.chaosblade.svc.k8sgraph.service;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * K8s 资源 YAML 服务 - 获取任意 K8s 资源的 YAML 定义
 * 
 * 支持的资源类型：
 * - 基础设施：Namespace, Node, PersistentVolume, StorageClass, ClusterRole, ClusterRoleBinding
 * - 工作负载：Deployment, ReplicaSet, StatefulSet, DaemonSet, Job, CronJob, Pod, ReplicationController
 * - 网络：Service, Ingress, IngressClass, NetworkPolicy, Endpoints, EndpointSlice
 * - 配置与安全：ConfigMap, Secret, PVC, ServiceAccount, Role, RoleBinding, LimitRange, ResourceQuota, HPA
 */
@Service
public class K8sResourceYamlService {
    
    private static final Logger logger = LoggerFactory.getLogger(K8sResourceYamlService.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    /**
     * 获取 K8s 资源的 YAML 定义
     * @param resourceType 资源类型（使用 Type 标识，如 k8s.workload.deployment）
     * @param resourceName 资源名称
     * @param namespace 命名空间（集群级资源可为 null）
     * @return YAML 字符串，资源不存在返回 null
     */
    public String getResourceYaml(String resourceType, String resourceName, String namespace) {
        logger.info("Getting resource YAML: type={}, name={}, namespace={}", resourceType, resourceName, namespace);
        
        try {
            HasMetadata resource = fetchResource(resourceType, resourceName, namespace);
            if (resource == null) {
                logger.warn("Resource not found: type={}, name={}, namespace={}", resourceType, resourceName, namespace);
                return null;
            }
            
            return convertToYaml(resource);
        } catch (Exception e) {
            logger.error("Failed to get resource YAML: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get resource YAML: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据资源类型获取资源对象
     */
    private HasMetadata fetchResource(String resourceType, String name, String namespace) {
        String type = resourceType.toLowerCase();
        
        // 基础设施资源（集群级）
        switch (type) {
            case "k8s.infra.namespace":
            case "namespace":
                return kubernetesClient.namespaces().withName(name).get();
            case "k8s.infra.node":
            case "node":
                return kubernetesClient.nodes().withName(name).get();
            case "k8s.infra.persistentvolume":
            case "persistentvolume":
            case "pv":
                return kubernetesClient.persistentVolumes().withName(name).get();
            case "k8s.infra.storageclass":
            case "storageclass":
            case "sc":
                return kubernetesClient.storage().v1().storageClasses().withName(name).get();
            case "k8s.infra.clusterrole":
            case "clusterrole":
                return kubernetesClient.rbac().clusterRoles().withName(name).get();
            case "k8s.infra.clusterrolebinding":
            case "clusterrolebinding":
                return kubernetesClient.rbac().clusterRoleBindings().withName(name).get();
        }
        
        // 命名空间级资源需要 namespace
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        
        // 工作负载资源
        switch (type) {
            case "k8s.workload.deployment":
            case "deployment":
                return kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
            case "k8s.workload.replicaset":
            case "replicaset":
            case "rs":
                return kubernetesClient.apps().replicaSets().inNamespace(namespace).withName(name).get();
            case "k8s.workload.statefulset":
            case "statefulset":
            case "sts":
                return kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
            case "k8s.workload.daemonset":
            case "daemonset":
            case "ds":
                return kubernetesClient.apps().daemonSets().inNamespace(namespace).withName(name).get();
            case "k8s.workload.job":
            case "job":
                return kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(name).get();
            case "k8s.workload.cronjob":
            case "cronjob":
            case "cj":
                return kubernetesClient.batch().v1().cronjobs().inNamespace(namespace).withName(name).get();
            case "k8s.workload.pod":
            case "pod":
            case "po":
                return kubernetesClient.pods().inNamespace(namespace).withName(name).get();
            case "k8s.workload.replicationcontroller":
            case "replicationcontroller":
            case "rc":
                return kubernetesClient.replicationControllers().inNamespace(namespace).withName(name).get();
        }
        
        // 网络资源
        switch (type) {
            case "k8s.network.service":
            case "service":
            case "svc":
                return kubernetesClient.services().inNamespace(namespace).withName(name).get();
            case "k8s.network.ingress":
            case "ingress":
            case "ing":
                return kubernetesClient.network().v1().ingresses().inNamespace(namespace).withName(name).get();
            case "k8s.network.ingressclass":
            case "ingressclass":
                return kubernetesClient.network().v1().ingressClasses().withName(name).get();
            case "k8s.network.networkpolicy":
            case "networkpolicy":
            case "netpol":
                return kubernetesClient.network().v1().networkPolicies().inNamespace(namespace).withName(name).get();
            case "k8s.network.endpoints":
            case "endpoints":
            case "ep":
                return kubernetesClient.endpoints().inNamespace(namespace).withName(name).get();
            case "k8s.network.endpointslice":
            case "endpointslice":
                return kubernetesClient.discovery().v1().endpointSlices().inNamespace(namespace).withName(name).get();
        }
        
        // 配置与安全资源
        return fetchConfigResource(type, name, namespace);
    }
    
    private HasMetadata fetchConfigResource(String type, String name, String namespace) {
        switch (type) {
            case "k8s.config.configmap":
            case "configmap":
            case "cm":
                return kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();
            case "k8s.config.secret":
            case "secret":
                return kubernetesClient.secrets().inNamespace(namespace).withName(name).get();
            case "k8s.config.persistentvolumeclaim":
            case "persistentvolumeclaim":
            case "pvc":
                return kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
            case "k8s.config.serviceaccount":
            case "serviceaccount":
            case "sa":
                return kubernetesClient.serviceAccounts().inNamespace(namespace).withName(name).get();
            case "k8s.config.role":
            case "role":
                return kubernetesClient.rbac().roles().inNamespace(namespace).withName(name).get();
            case "k8s.config.rolebinding":
            case "rolebinding":
                return kubernetesClient.rbac().roleBindings().inNamespace(namespace).withName(name).get();
            case "k8s.config.limitrange":
            case "limitrange":
            case "limits":
                return kubernetesClient.limitRanges().inNamespace(namespace).withName(name).get();
            case "k8s.config.resourcequota":
            case "resourcequota":
            case "quota":
                return kubernetesClient.resourceQuotas().inNamespace(namespace).withName(name).get();
            case "k8s.config.horizontalpodautoscaler":
            case "horizontalpodautoscaler":
            case "hpa":
                return kubernetesClient.autoscaling().v1().horizontalPodAutoscalers()
                        .inNamespace(namespace).withName(name).get();
            default:
                logger.warn("Unsupported resource type: {}", type);
                return null;
        }
    }

    /**
     * 将 K8s 资源对象转换为 YAML 字符串
     */
    private String convertToYaml(HasMetadata resource) {
        // 清理元数据中的系统字段
        cleanMetadata(resource.getMetadata());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        Yaml yaml = new Yaml(options);
        Map<String, Object> resourceMap = buildResourceMap(resource);
        return yaml.dump(resourceMap);
    }

    /**
     * 清理元数据中的系统字段
     */
    private void cleanMetadata(ObjectMeta meta) {
        if (meta == null) return;
        meta.setManagedFields(null);
        meta.setResourceVersion(null);
        meta.setUid(null);
        meta.setCreationTimestamp(null);
        meta.setGeneration(null);
        meta.setSelfLink(null);

        // 过滤系统注解
        if (meta.getAnnotations() != null) {
            Map<String, String> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : meta.getAnnotations().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("kubectl.kubernetes.io/")
                    && !key.startsWith("kubernetes.io/")
                    && !key.startsWith("deployment.kubernetes.io/")) {
                    filtered.put(key, entry.getValue());
                }
            }
            if (filtered.isEmpty()) {
                meta.setAnnotations(null);
            } else {
                meta.setAnnotations(filtered);
            }
        }
    }

    /**
     * 构建资源的 Map 结构用于 YAML 输出
     */
    private Map<String, Object> buildResourceMap(HasMetadata resource) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("apiVersion", resource.getApiVersion());
        result.put("kind", resource.getKind());

        // metadata
        result.put("metadata", buildMetadataMap(resource.getMetadata()));

        // 使用反射获取 spec 和 status（如果存在）
        try {
            java.lang.reflect.Method getSpec = resource.getClass().getMethod("getSpec");
            Object spec = getSpec.invoke(resource);
            if (spec != null) {
                result.put("spec", spec);
            }
        } catch (NoSuchMethodException e) {
            // 某些资源没有 spec，如 Namespace
        } catch (Exception e) {
            logger.debug("Failed to get spec: {}", e.getMessage());
        }

        // 可选：添加 data（适用于 ConfigMap 和 Secret）
        try {
            java.lang.reflect.Method getData = resource.getClass().getMethod("getData");
            Object data = getData.invoke(resource);
            if (data != null) {
                result.put("data", data);
            }
        } catch (NoSuchMethodException e) {
            // 大多数资源没有 data 字段
        } catch (Exception e) {
            logger.debug("Failed to get data: {}", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> buildMetadataMap(ObjectMeta meta) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (meta == null) return metadata;

        metadata.put("name", meta.getName());
        if (meta.getNamespace() != null) {
            metadata.put("namespace", meta.getNamespace());
        }
        if (meta.getLabels() != null && !meta.getLabels().isEmpty()) {
            metadata.put("labels", meta.getLabels());
        }
        if (meta.getAnnotations() != null && !meta.getAnnotations().isEmpty()) {
            metadata.put("annotations", meta.getAnnotations());
        }

        return metadata;
    }
}

