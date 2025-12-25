package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.EdgeType;
import com.chaosblade.svc.k8sgraph.domain.GraphEdge;
import com.chaosblade.svc.k8sgraph.domain.GraphNode;
import com.chaosblade.svc.k8sgraph.domain.service.*;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceInfo;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceListResponse;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * 服务详情服务 - 获取 K8s Service 详细信息
 */
@org.springframework.stereotype.Service
public class ServiceDetailService {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceDetailService.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    @Autowired
    private ServiceMapService serviceMapService;
    
    @Autowired
    private TraceService traceService;
    
    /**
     * 获取服务基本详情
     */
    public ServiceDetail getServiceDetail(String serviceName, String namespace) {
        logger.info("Getting service detail: {}/{}", namespace, serviceName);
        
        ServiceDetail detail = new ServiceDetail(serviceName, namespace);
        
        try {
            Service service = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();
            
            if (service != null) {
                // 基本信息
                detail.setLabels(service.getMetadata().getLabels());
                detail.setAnnotations(service.getMetadata().getAnnotations());
                
                // 服务规格
                if (service.getSpec() != null) {
                    detail.setServiceType(service.getSpec().getType());
                    detail.setClusterIP(service.getSpec().getClusterIP());
                    detail.setSelector(service.getSpec().getSelector());
                    
                    // 端口信息
                    List<Map<String, Object>> ports = new ArrayList<>();
                    if (service.getSpec().getPorts() != null) {
                        for (ServicePort port : service.getSpec().getPorts()) {
                            Map<String, Object> portInfo = new HashMap<>();
                            portInfo.put("name", port.getName());
                            portInfo.put("port", port.getPort());
                            portInfo.put("targetPort", port.getTargetPort());
                            portInfo.put("protocol", port.getProtocol());
                            if (port.getNodePort() != null) {
                                portInfo.put("nodePort", port.getNodePort());
                            }
                            ports.add(portInfo);
                        }
                    }
                    detail.setPorts(ports);
                }
                
                // 关联的 Pod 数量
                int podCount = countSelectedPods(service, namespace);
                detail.setPodCount(podCount);
                
                // 健康状态
                detail.setHealthStatus(podCount > 0 ? "Healthy" : "No Endpoints");
                
                // 创建 GraphNode
                detail.setServiceNode(createServiceGraphNode(service));
            }
        } catch (Exception e) {
            logger.error("Failed to get service detail: {}", e.getMessage(), e);
        }
        
        return detail;
    }
    
    /**
     * 获取服务的 YAML 定义
     */
    public String getServiceYaml(String serviceName, String namespace) {
        logger.info("Getting service YAML: {}/{}", namespace, serviceName);

        try {
            Service service = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();

            if (service != null) {
                // 清理一些敏感或无用字段
                if (service.getMetadata() != null) {
                    service.getMetadata().setManagedFields(null);
                    service.getMetadata().setResourceVersion(null);
                    service.getMetadata().setUid(null);
                    service.getMetadata().setCreationTimestamp(null);
                    service.getMetadata().setGeneration(null);
                }

                // 清理 status 中的无用字段
                service.setStatus(null);

                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                options.setPrettyFlow(true);
                options.setIndent(2);
                options.setIndicatorIndent(0);
                Yaml yaml = new Yaml(options);
                return yaml.dump(convertToMap(service));
            } else {
                logger.warn("Service not found: {}/{}", namespace, serviceName);
            }
        } catch (Exception e) {
            logger.error("Failed to get service YAML: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 获取服务性能指标
     */
    public ServiceMetrics getServiceMetrics(String serviceName, long fromMs, long toMs) {
        logger.info("Getting service metrics: {}, from={}, to={}", serviceName, fromMs, toMs);

        ServiceMetrics metrics = new ServiceMetrics(serviceName);
        metrics.setFromTime(fromMs);
        metrics.setToTime(toMs);

        // 设置默认值
        metrics.setTotalRequests(0L);
        metrics.setErrorRequests(0L);
        metrics.setSuccessRequests(0L);
        metrics.setErrorRate(0.0);
        metrics.setRequestsPerSecond(0.0);
        metrics.setAvgLatency(0.0);
        metrics.setP50Latency(0.0);
        metrics.setP95Latency(0.0);
        metrics.setP99Latency(0.0);

        try {
            // 通过 TraceService 获取 trace 数据来计算指标
            TraceListResponse traceListResponse = traceService.getTraceList(serviceName, fromMs, toMs);
            List<TraceInfo> traces = traceListResponse.getTraces();

            logger.info("Retrieved {} traces for service {}",
                    traces != null ? traces.size() : 0, serviceName);

            if (traces != null && !traces.isEmpty()) {
                long totalRequests = traces.size();
                long errorRequests = traces.stream().filter(t -> Boolean.TRUE.equals(t.getHasError())).count();

                List<Long> durations = new ArrayList<>();
                for (TraceInfo trace : traces) {
                    if (trace.getDuration() != null && trace.getDuration() > 0) {
                        durations.add(trace.getDuration());
                    }
                }

                // 计算指标
                metrics.setTotalRequests(totalRequests);
                metrics.setErrorRequests(errorRequests);
                metrics.setSuccessRequests(totalRequests - errorRequests);
                metrics.setErrorRate(totalRequests > 0 ? (double) errorRequests / totalRequests * 100 : 0.0);

                // 计算延迟百分位数
                if (!durations.isEmpty()) {
                    Collections.sort(durations);
                    // 将纳秒转换为毫秒
                    metrics.setAvgLatency(durations.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0);
                    metrics.setP50Latency(getPercentile(durations, 50) / 1_000_000.0);
                    metrics.setP95Latency(getPercentile(durations, 95) / 1_000_000.0);
                    metrics.setP99Latency(getPercentile(durations, 99) / 1_000_000.0);
                }

                // 计算 RPS
                long durationSeconds = (toMs - fromMs) / 1000;
                if (durationSeconds > 0) {
                    metrics.setRequestsPerSecond((double) totalRequests / durationSeconds);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to calculate service metrics for {}: {}", serviceName, e.getMessage(), e);
        }

        return metrics;
    }

    /**
     * 获取服务的完整关系（技术层面 + 业务层面）
     * 不需要时间参数，因为 K8s 资源关系相对稳定
     */
    public ServiceRelations getServiceRelations(String serviceName, String namespace) {
        logger.info("Getting service relations: {}/{}", namespace, serviceName);

        ServiceRelations relations = new ServiceRelations(serviceName, namespace);

        try {
            Service service = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();

            if (service == null) {
                logger.warn("Service not found: {}/{}", namespace, serviceName);
                return relations;
            }

            // 设置服务节点
            relations.setServiceNode(createServiceGraphNode(service));

            // 获取技术层面关系
            collectTechnicalRelations(relations, service, namespace);

            // 获取业务层面关系（使用默认时间范围：最近1小时）
            long now = System.currentTimeMillis();
            long oneHourAgo = now - 60 * 60 * 1000;
            collectBusinessRelations(relations, serviceName, oneHourAgo, now);

        } catch (Exception e) {
            logger.error("Failed to get service relations: {}", e.getMessage(), e);
        }

        return relations;
    }

    /**
     * 收集技术层面关系（K8s 资源）
     */
    private void collectTechnicalRelations(ServiceRelations relations, Service service, String namespace) {
        Map<String, String> selector = service.getSpec() != null ? service.getSpec().getSelector() : null;

        // 1. 获取关联的 Pods
        if (selector != null && !selector.isEmpty()) {
            try {
                List<Pod> pods = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabels(selector)
                        .list()
                        .getItems();

                for (Pod pod : pods) {
                    GraphNode podNode = createPodGraphNode(pod);
                    relations.addPod(podNode);

                    // 添加 Service -> Pod 的选择关系边
                    GraphEdge edge = GraphEdge.create(EdgeType.SELECTS,
                            relations.getServiceNode().getId(), podNode.getId());
                    relations.addTechnicalEdge(edge);

                    // 获取 Pod 的 Owner（Deployment/ReplicaSet 等）
                    collectPodOwners(relations, pod, namespace);
                }
            } catch (Exception e) {
                logger.warn("Failed to get pods for service: {}", e.getMessage());
            }
        }

        // 2. 获取路由到该 Service 的 Ingress
        try {
            List<io.fabric8.kubernetes.api.model.networking.v1.Ingress> ingresses =
                    kubernetesClient.network().v1().ingresses()
                            .inNamespace(namespace)
                            .list()
                            .getItems();

            for (io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress : ingresses) {
                if (ingressRoutesToService(ingress, relations.getServiceName())) {
                    GraphNode ingressNode = createIngressGraphNode(ingress);
                    relations.addIngress(ingressNode);

                    // 添加 Ingress -> Service 的路由关系边
                    GraphEdge edge = GraphEdge.create(EdgeType.ROUTES_TO,
                            ingressNode.getId(), relations.getServiceNode().getId());
                    relations.addTechnicalEdge(edge);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get ingresses: {}", e.getMessage());
        }
    }

    /**
     * 收集 Pod 的 Owner 资源
     */
    private void collectPodOwners(ServiceRelations relations, Pod pod, String namespace) {
        if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null) {
            return;
        }

        for (OwnerReference owner : pod.getMetadata().getOwnerReferences()) {
            try {
                GraphNode workloadNode = null;
                String ownerKind = owner.getKind();
                String ownerName = owner.getName();

                if ("ReplicaSet".equals(ownerKind)) {
                    // 查找 ReplicaSet 的 Owner (Deployment)
                    io.fabric8.kubernetes.api.model.apps.ReplicaSet rs =
                            kubernetesClient.apps().replicaSets()
                                    .inNamespace(namespace)
                                    .withName(ownerName)
                                    .get();
                    if (rs != null && rs.getMetadata().getOwnerReferences() != null) {
                        for (OwnerReference rsOwner : rs.getMetadata().getOwnerReferences()) {
                            if ("Deployment".equals(rsOwner.getKind())) {
                                workloadNode = new GraphNode();
                                workloadNode.setId(GraphNode.generateId("k8s.workload.deployment", namespace, rsOwner.getName()));
                                workloadNode.setType("k8s.workload.deployment");
                                workloadNode.setDomain("k8s.workload");
                                workloadNode.setName(rsOwner.getName());
                                workloadNode.setNamespace(namespace);
                            }
                        }
                    }
                } else if ("StatefulSet".equals(ownerKind) || "DaemonSet".equals(ownerKind)) {
                    workloadNode = new GraphNode();
                    String type = "k8s.workload." + ownerKind.toLowerCase();
                    workloadNode.setId(GraphNode.generateId(type, namespace, ownerName));
                    workloadNode.setType(type);
                    workloadNode.setDomain("k8s.workload");
                    workloadNode.setName(ownerName);
                    workloadNode.setNamespace(namespace);
                }

                if (workloadNode != null && !containsWorkload(relations, workloadNode.getId())) {
                    relations.addWorkload(workloadNode);
                }
            } catch (Exception e) {
                logger.warn("Failed to get owner for pod: {}", e.getMessage());
            }
        }
    }

    /**
     * 收集业务层面关系（服务调用）
     */
    private void collectBusinessRelations(ServiceRelations relations, String serviceName, long fromMs, long toMs) {
        try {
            Map<String, List<String>> dependencies = serviceMapService.getServiceDependencies(serviceName, fromMs, toMs);
            relations.setUpstreamServices(dependencies.get("upstream"));
            relations.setDownstreamServices(dependencies.get("downstream"));

            // 获取详细的调用边信息
            ServiceMapData mapData = serviceMapService.getServiceMap(fromMs, toMs);
            for (ServiceMapEdge edge : mapData.getEdges()) {
                if (edge.getSourceService().equals(serviceName) || edge.getTargetService().equals(serviceName)) {
                    relations.addBusinessEdge(edge);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get business relations: {}", e.getMessage());
        }
    }

    private boolean ingressRoutesToService(io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress, String serviceName) {
        if (ingress.getSpec() == null || ingress.getSpec().getRules() == null) {
            return false;
        }
        for (io.fabric8.kubernetes.api.model.networking.v1.IngressRule rule : ingress.getSpec().getRules()) {
            if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                for (io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath path : rule.getHttp().getPaths()) {
                    if (path.getBackend() != null && path.getBackend().getService() != null
                            && serviceName.equals(path.getBackend().getService().getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsWorkload(ServiceRelations relations, String workloadId) {
        for (GraphNode workload : relations.getWorkloads()) {
            if (workload.getId().equals(workloadId)) {
                return true;
            }
        }
        return false;
    }

    private GraphNode createPodGraphNode(Pod pod) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.workload.pod",
                pod.getMetadata().getNamespace(),
                pod.getMetadata().getName()));
        node.setType("k8s.workload.pod");
        node.setDomain("k8s.workload");
        node.setName(pod.getMetadata().getName());
        node.setNamespace(pod.getMetadata().getNamespace());
        node.setLabels(pod.getMetadata().getLabels());

        // 添加状态信息
        if (pod.getStatus() != null && pod.getStatus().getPhase() != null) {
            node.addProperty("phase", pod.getStatus().getPhase());
        }
        return node;
    }

    private GraphNode createIngressGraphNode(io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.network.ingress",
                ingress.getMetadata().getNamespace(),
                ingress.getMetadata().getName()));
        node.setType("k8s.network.ingress");
        node.setDomain("k8s.network");
        node.setName(ingress.getMetadata().getName());
        node.setNamespace(ingress.getMetadata().getNamespace());
        node.setLabels(ingress.getMetadata().getLabels());
        return node;
    }

    // ========== 辅助方法 ==========

    private int countSelectedPods(Service service, String namespace) {
        if (service.getSpec() == null || service.getSpec().getSelector() == null) {
            return 0;
        }

        try {
            List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabels(service.getSpec().getSelector())
                    .list()
                    .getItems();
            return pods.size();
        } catch (Exception e) {
            logger.warn("Failed to count pods: {}", e.getMessage());
            return 0;
        }
    }

    private GraphNode createServiceGraphNode(Service service) {
        GraphNode node = new GraphNode();
        node.setId(GraphNode.generateId("k8s.network.service",
                service.getMetadata().getNamespace(),
                service.getMetadata().getName()));
        node.setType("k8s.network.service");
        node.setDomain("k8s.network");
        node.setName(service.getMetadata().getName());
        node.setNamespace(service.getMetadata().getNamespace());
        node.setLabels(service.getMetadata().getLabels());
        return node;
    }

    private Map<String, Object> convertToMap(Service service) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", "v1");
        result.put("kind", "Service");

        // metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (service.getMetadata() != null) {
            metadata.put("name", service.getMetadata().getName());
            metadata.put("namespace", service.getMetadata().getNamespace());
            if (service.getMetadata().getLabels() != null && !service.getMetadata().getLabels().isEmpty()) {
                metadata.put("labels", service.getMetadata().getLabels());
            }
            if (service.getMetadata().getAnnotations() != null && !service.getMetadata().getAnnotations().isEmpty()) {
                // 过滤掉一些系统注解
                Map<String, String> filteredAnnotations = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : service.getMetadata().getAnnotations().entrySet()) {
                    if (!entry.getKey().startsWith("kubectl.kubernetes.io/")) {
                        filteredAnnotations.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!filteredAnnotations.isEmpty()) {
                    metadata.put("annotations", filteredAnnotations);
                }
            }
        }
        result.put("metadata", metadata);

        // spec
        if (service.getSpec() != null) {
            Map<String, Object> spec = new LinkedHashMap<>();
            if (service.getSpec().getType() != null) {
                spec.put("type", service.getSpec().getType());
            }
            if (service.getSpec().getSelector() != null && !service.getSpec().getSelector().isEmpty()) {
                spec.put("selector", service.getSpec().getSelector());
            }
            if (service.getSpec().getPorts() != null && !service.getSpec().getPorts().isEmpty()) {
                List<Map<String, Object>> ports = new ArrayList<>();
                for (ServicePort port : service.getSpec().getPorts()) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    if (port.getName() != null) {
                        p.put("name", port.getName());
                    }
                    if (port.getPort() != null) {
                        p.put("port", port.getPort());
                    }
                    if (port.getTargetPort() != null) {
                        // targetPort 可能是整数或字符串
                        if (port.getTargetPort().getIntVal() != null) {
                            p.put("targetPort", port.getTargetPort().getIntVal());
                        } else if (port.getTargetPort().getStrVal() != null) {
                            p.put("targetPort", port.getTargetPort().getStrVal());
                        }
                    }
                    if (port.getProtocol() != null) {
                        p.put("protocol", port.getProtocol());
                    }
                    if (port.getNodePort() != null && port.getNodePort() > 0) {
                        p.put("nodePort", port.getNodePort());
                    }
                    ports.add(p);
                }
                spec.put("ports", ports);
            }
            if (service.getSpec().getClusterIP() != null && !"None".equals(service.getSpec().getClusterIP())) {
                spec.put("clusterIP", service.getSpec().getClusterIP());
            }
            if (service.getSpec().getSessionAffinity() != null) {
                spec.put("sessionAffinity", service.getSpec().getSessionAffinity());
            }
            result.put("spec", spec);
        }

        return result;
    }

    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}

