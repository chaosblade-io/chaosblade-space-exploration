package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.EntityType;
import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.k8s.KubernetesMetadata;
import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据源编排服务
 * 负责协调 Jaeger、Prometheus、Kubernetes 三个数据源的数据获取和整合
 */
@Service
public class DataSourceOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceOrchestrator.class);

    @Autowired
    private PrometheusQueryService prometheusQueryService;

    @Autowired
    private KubernetesQueryService kubernetesQueryService;

    @Autowired
    private MetricsConverterService metricsConverterService;

    @Value("${prometheus.enabled:true}")
    private boolean prometheusEnabled;

    @Value("${kubernetes.enabled:true}")
    private boolean kubernetesEnabled;

    // 线程池用于并行查询
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 为拓扑节点增强数据
     * 从 Prometheus 和 Kubernetes 获取额外的指标和元数据
     */
    public void enrichTopologyNodes(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        logger.info("Enriching {} topology nodes with Prometheus and Kubernetes data", nodes.size());

        // 并行处理所有节点
        CompletableFuture<?>[] futures = nodes.stream()
            .map(node -> CompletableFuture.runAsync(() -> enrichNode(node), executorService))
            .toArray(CompletableFuture[]::new);

        // 等待所有任务完成
        CompletableFuture.allOf(futures).join();

        logger.info("Finished enriching topology nodes");
    }

    /**
     * 增强单个节点
     */
    private void enrichNode(Node node) {
        if (node == null) {
            return;
        }

        EntityType entityType = node.getEntityType();
        if (entityType == null) {
            return;
        }

        try {
            switch (entityType) {
                case POD:
                    enrichPodNode(node);
                    break;
                case NODE:
                    enrichNodeNode(node);
                    break;
                case CONTAINER:
                    enrichContainerNode(node);
                    break;
                default:
                    // 其他类型的节点暂不增强
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to enrich node: {}", node.getNodeId(), e);
        }
    }

    /**
     * 增强 Pod 节点
     */
    private void enrichPodNode(Node node) {
        String podName = extractPodName(node);
        String namespace = extractNamespace(node);

        if (podName == null || namespace == null) {
            logger.debug("Cannot extract pod name or namespace from node: {}", node.getNodeId());
            return;
        }

        logger.debug("Enriching Pod node: {}/{}", namespace, podName);

        // 并行查询 Prometheus 和 Kubernetes
        CompletableFuture<PrometheusMetrics> prometheusFuture = CompletableFuture.supplyAsync(
            () -> prometheusEnabled ? prometheusQueryService.queryPodMetrics(namespace, podName) : null,
            executorService
        );

        CompletableFuture<KubernetesMetadata> kubernetesFuture = CompletableFuture.supplyAsync(
            () -> kubernetesEnabled ? kubernetesQueryService.queryPodMetadata(namespace, podName) : null,
            executorService
        );

        // 等待两个查询完成
        CompletableFuture.allOf(prometheusFuture, kubernetesFuture).join();

        try {
            // 获取查询结果
            PrometheusMetrics prometheusMetrics = prometheusFuture.get();
            KubernetesMetadata kubernetesMetadata = kubernetesFuture.get();

            // 附加到节点
            if (prometheusMetrics != null) {
                metricsConverterService.attachMetricsToNode(node, prometheusMetrics);
            }

            if (kubernetesMetadata != null) {
                attachKubernetesMetadataToNode(node, kubernetesMetadata);
            }

        } catch (Exception e) {
            logger.error("Failed to get enrichment data for Pod: {}/{}", namespace, podName, e);
        }
    }

    /**
     * 增强 Node 节点
     */
    private void enrichNodeNode(Node node) {
        String nodeName = extractNodeName(node);

        if (nodeName == null) {
            logger.debug("Cannot extract node name from node: {}", node.getNodeId());
            return;
        }

        logger.debug("Enriching Node node: {}", nodeName);

        // 并行查询 Prometheus 和 Kubernetes
        CompletableFuture<PrometheusMetrics> prometheusFuture = CompletableFuture.supplyAsync(
            () -> prometheusEnabled ? prometheusQueryService.queryNodeMetrics(nodeName) : null,
            executorService
        );

        CompletableFuture<KubernetesMetadata> kubernetesFuture = CompletableFuture.supplyAsync(
            () -> kubernetesEnabled ? kubernetesQueryService.queryNodeMetadata(nodeName) : null,
            executorService
        );

        // 等待两个查询完成
        CompletableFuture.allOf(prometheusFuture, kubernetesFuture).join();

        try {
            // 获取查询结果
            PrometheusMetrics prometheusMetrics = prometheusFuture.get();
            KubernetesMetadata kubernetesMetadata = kubernetesFuture.get();

            // 附加到节点
            if (prometheusMetrics != null) {
                metricsConverterService.attachMetricsToNode(node, prometheusMetrics);
            }

            if (kubernetesMetadata != null) {
                attachKubernetesMetadataToNode(node, kubernetesMetadata);
            }

        } catch (Exception e) {
            logger.error("Failed to get enrichment data for Node: {}", nodeName, e);
        }
    }

    /**
     * 增强 Container 节点
     */
    private void enrichContainerNode(Node node) {
        String containerName = extractContainerName(node);
        String podName = extractPodName(node);
        String namespace = extractNamespace(node);

        if (containerName == null || podName == null || namespace == null) {
            logger.debug("Cannot extract container/pod/namespace from node: {}", node.getNodeId());
            return;
        }

        logger.debug("Enriching Container node: {}/{}/{}", namespace, podName, containerName);

        // 查询 Prometheus 指标
        if (prometheusEnabled) {
            PrometheusMetrics prometheusMetrics = prometheusQueryService.queryContainerMetrics(
                namespace, podName, containerName);

            if (prometheusMetrics != null) {
                metricsConverterService.attachMetricsToNode(node, prometheusMetrics);
            }
        }
    }

    /**
     * 将 Kubernetes 元数据附加到节点
     */
    private void attachKubernetesMetadataToNode(Node node, KubernetesMetadata metadata) {
        if (node == null || metadata == null) {
            return;
        }

        logger.debug("Attaching Kubernetes metadata to node: {}", node.getNodeId());

        // 获取或创建节点属性
        Node.NodeAttributes attrs = node.getAttrs();
        if (attrs == null) {
            attrs = new Node.NodeAttributes();
            node.setAttrs(attrs);
        }

        // 设置 Kubernetes 元数据
        attrs.setKubernetes(metadata);
    }

    /**
     * 从节点中提取 Pod 名称
     */
    private String extractPodName(Node node) {
        // 尝试从节点 ID 或名称中提取
        String nodeId = node.getNodeId();
        if (nodeId != null && nodeId.contains("/")) {
            String[] parts = nodeId.split("/");
            if (parts.length >= 2) {
                return parts[1]; // namespace/podName 格式
            }
        }

        // 尝试从扩展属性中获取
        if (node.getAttrs() != null && node.getAttrs().getExtensions() != null) {
            Object podName = node.getAttrs().getExtensions().get("podName");
            if (podName != null) {
                return podName.toString();
            }
        }

        return null;
    }

    /**
     * 从节点中提取命名空间
     */
    private String extractNamespace(Node node) {
        // 尝试从节点 ID 中提取
        String nodeId = node.getNodeId();
        if (nodeId != null && nodeId.contains("/")) {
            String[] parts = nodeId.split("/");
            if (parts.length >= 2) {
                return parts[0]; // namespace/podName 格式
            }
        }

        // 尝试从扩展属性中获取
        if (node.getAttrs() != null && node.getAttrs().getExtensions() != null) {
            Object namespace = node.getAttrs().getExtensions().get("namespace");
            if (namespace != null) {
                return namespace.toString();
            }
        }

        return "default"; // 默认命名空间
    }

    /**
     * 从节点中提取 Node 名称
     */
    private String extractNodeName(Node node) {
        // 尝试从节点 ID 或名称中提取
        String nodeId = node.getNodeId();
        if (nodeId != null) {
            return nodeId;
        }

        return null;
    }

    /**
     * 从节点中提取 Container 名称
     */
    private String extractContainerName(Node node) {
        // 尝试从节点 ID 中提取
        String nodeId = node.getNodeId();
        if (nodeId != null && nodeId.contains("/")) {
            String[] parts = nodeId.split("/");
            if (parts.length >= 3) {
                return parts[2]; // namespace/podName/containerName 格式
            }
        }

        // 尝试从扩展属性中获取
        if (node.getAttrs() != null && node.getAttrs().getExtensions() != null) {
            Object containerName = node.getAttrs().getExtensions().get("containerName");
            if (containerName != null) {
                return containerName.toString();
            }
        }

        return null;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}

