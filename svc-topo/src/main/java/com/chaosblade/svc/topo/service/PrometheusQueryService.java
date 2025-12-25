package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

/**
 * Prometheus 查询服务
 * 负责从 Prometheus 查询指标数据
 */
@Service
public class PrometheusQueryService {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusQueryService.class);

    @Value("${prometheus.host:prometheus.monitoring.svc.cluster.local}")
    private String prometheusHost;

    @Value("${prometheus.port:9090}")
    private int prometheusPort;

    @Value("${prometheus.query-timeout:30}")
    private int queryTimeout;

    @Value("${prometheus.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;

    public PrometheusQueryService() {
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Prometheus integration is disabled");
            return;
        }
        this.httpClient = HttpClients.createDefault();
        logger.info("Prometheus query service initialized with host: {}:{}", prometheusHost, prometheusPort);
    }

    @PreDestroy
    public void cleanup() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                logger.error("Failed to close HTTP client", e);
            }
        }
    }

    /**
     * 查询 Pod 的 Prometheus 指标
     */
    public PrometheusMetrics queryPodMetrics(String namespace, String podName) {
        if (!enabled) {
            logger.debug("Prometheus is disabled, skipping metrics query");
            return null;
        }

        logger.debug("Querying Prometheus metrics for pod: {}/{}", namespace, podName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询资源指标
            ResourceMetrics resourceMetrics = queryPodResourceMetrics(namespace, podName);
            metrics.setResources(resourceMetrics);

            // 查询性能指标
            PerformanceMetrics performanceMetrics = queryPodPerformanceMetrics(namespace, podName);
            metrics.setPerformance(performanceMetrics);

            // 查询健康状态
            HealthMetrics healthMetrics = queryPodHealthMetrics(namespace, podName);
            metrics.setHealth(healthMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for pod: {}/{}", namespace, podName, e);
        }

        return metrics;
    }

    /**
     * 查询 Node 的 Prometheus 指标
     */
    public PrometheusMetrics queryNodeMetrics(String nodeName) {
        if (!enabled) {
            logger.debug("Prometheus is disabled, skipping metrics query");
            return null;
        }

        logger.debug("Querying Prometheus metrics for node: {}", nodeName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询节点资源指标
            ResourceMetrics resourceMetrics = queryNodeResourceMetrics(nodeName);
            metrics.setResources(resourceMetrics);

            // 查询节点健康状态
            HealthMetrics healthMetrics = queryNodeHealthMetrics(nodeName);
            metrics.setHealth(healthMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for node: {}", nodeName, e);
        }

        return metrics;
    }

    /**
     * 查询 Container 的 Prometheus 指标
     */
    public PrometheusMetrics queryContainerMetrics(String namespace, String podName, String containerName) {
        if (!enabled) {
            logger.debug("Prometheus is disabled, skipping metrics query");
            return null;
        }

        logger.debug("Querying Prometheus metrics for container: {}/{}/{}", namespace, podName, containerName);

        PrometheusMetrics metrics = new PrometheusMetrics();

        try {
            // 查询容器资源指标
            ResourceMetrics resourceMetrics = queryContainerResourceMetrics(namespace, podName, containerName);
            metrics.setResources(resourceMetrics);

            metrics.setTimestamp(System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to query Prometheus metrics for container: {}/{}/{}",
                namespace, podName, containerName, e);
        }

        return metrics;
    }

    /**
     * 查询 Pod 资源指标
     */
    private ResourceMetrics queryPodResourceMetrics(String namespace, String podName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // CPU 使用率
        String cpuQuery = String.format(
            "sum(rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=\"%s\",container!=\"\"}[5m]))",
            namespace, podName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        if (cpuUsage != null) {
            metrics.setCpuUsageCores(cpuUsage);
        }

        // CPU 限制
        String cpuLimitQuery = String.format(
            "sum(container_spec_cpu_quota{namespace=\"%s\",pod=\"%s\"}/100000)",
            namespace, podName
        );
        Double cpuLimit = executeQuery(cpuLimitQuery);
        if (cpuLimit != null && cpuLimit > 0 && cpuUsage != null) {
            metrics.setCpuUsagePercent((cpuUsage / cpuLimit) * 100);
        }

        // 内存使用
        String memoryQuery = String.format(
            "sum(container_memory_usage_bytes{namespace=\"%s\",pod=\"%s\",container!=\"\"})",
            namespace, podName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        if (memoryUsage != null) {
            metrics.setMemoryUsageBytes(memoryUsage.longValue());
        }

        // 内存限制
        String memoryLimitQuery = String.format(
            "sum(container_spec_memory_limit_bytes{namespace=\"%s\",pod=\"%s\"})",
            namespace, podName
        );
        Double memoryLimit = executeQuery(memoryLimitQuery);
        if (memoryLimit != null && memoryLimit > 0 && memoryUsage != null) {
            metrics.setMemoryUsagePercent((memoryUsage / memoryLimit) * 100);
        }

        // 网络接收速率
        String networkRxQuery = String.format(
            "sum(rate(container_network_receive_bytes_total{namespace=\"%s\",pod=\"%s\"}[5m]))",
            namespace, podName
        );
        Double networkRx = executeQuery(networkRxQuery);
        if (networkRx != null) {
            metrics.setNetworkReceiveBytesPerSec(networkRx);
        }

        // 网络发送速率
        String networkTxQuery = String.format(
            "sum(rate(container_network_transmit_bytes_total{namespace=\"%s\",pod=\"%s\"}[5m]))",
            namespace, podName
        );
        Double networkTx = executeQuery(networkTxQuery);
        if (networkTx != null) {
            metrics.setNetworkTransmitBytesPerSec(networkTx);
        }

        return metrics;
    }

    /**
     * 查询 Pod 性能指标
     */
    private PerformanceMetrics queryPodPerformanceMetrics(String namespace, String podName) throws Exception {
        PerformanceMetrics metrics = new PerformanceMetrics();
        // 这里可以查询应用级别的指标，如果应用暴露了 Prometheus 指标
        return metrics;
    }

    /**
     * 查询 Pod 健康状态
     */
    private HealthMetrics queryPodHealthMetrics(String namespace, String podName) throws Exception {
        HealthMetrics metrics = new HealthMetrics();

        // 重启次数
        String restartQuery = String.format(
            "sum(kube_pod_container_status_restarts_total{namespace=\"%s\",pod=\"%s\"})",
            namespace, podName
        );
        Double restartCount = executeQuery(restartQuery);
        if (restartCount != null) {
            metrics.setRestartCount(restartCount.intValue());
        }

        // Pod 状态
        String statusQuery = String.format(
            "kube_pod_status_phase{namespace=\"%s\",pod=\"%s\",phase=\"Running\"}",
            namespace, podName
        );
        Double isRunning = executeQuery(statusQuery);

        if (isRunning != null && isRunning > 0) {
            metrics.setStatus(HealthMetrics.HealthStatus.HEALTHY);
        } else {
            metrics.setStatus(HealthMetrics.HealthStatus.UNHEALTHY);
        }

        return metrics;
    }

    /**
     * 查询 Node 资源指标
     */
    private ResourceMetrics queryNodeResourceMetrics(String nodeName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // 节点 CPU 使用率
        String cpuQuery = String.format(
            "100 - (avg(irate(node_cpu_seconds_total{mode=\"idle\",instance=~\"%s.*\"}[5m])) * 100)",
            nodeName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        if (cpuUsage != null) {
            metrics.setCpuUsagePercent(cpuUsage);
        }

        // 节点内存使用率
        String memoryQuery = String.format(
            "(1 - (node_memory_MemAvailable_bytes{instance=~\"%s.*\"} / node_memory_MemTotal_bytes{instance=~\"%s.*\"})) * 100",
            nodeName, nodeName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        if (memoryUsage != null) {
            metrics.setMemoryUsagePercent(memoryUsage);
        }

        return metrics;
    }

    /**
     * 查询 Node 健康状态
     */
    private HealthMetrics queryNodeHealthMetrics(String nodeName) throws Exception {
        HealthMetrics metrics = new HealthMetrics();

        // 节点就绪状态
        String readyQuery = String.format(
            "kube_node_status_condition{node=\"%s\",condition=\"Ready\",status=\"true\"}",
            nodeName
        );
        Double isReady = executeQuery(readyQuery);

        if (isReady != null && isReady > 0) {
            metrics.setStatus(HealthMetrics.HealthStatus.HEALTHY);
            metrics.addCheck("ready", true);
        } else {
            metrics.setStatus(HealthMetrics.HealthStatus.UNHEALTHY);
            metrics.addCheck("ready", false);
        }

        return metrics;
    }

    /**
     * 查询 Container 资源指标
     */
    private ResourceMetrics queryContainerResourceMetrics(String namespace, String podName, String containerName) throws Exception {
        ResourceMetrics metrics = new ResourceMetrics();

        // 容器 CPU 使用
        String cpuQuery = String.format(
            "rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=\"%s\",container=\"%s\"}[5m])",
            namespace, podName, containerName
        );
        Double cpuUsage = executeQuery(cpuQuery);
        if (cpuUsage != null) {
            metrics.setCpuUsageCores(cpuUsage);
        }

        // 容器内存使用
        String memoryQuery = String.format(
            "container_memory_usage_bytes{namespace=\"%s\",pod=\"%s\",container=\"%s\"}",
            namespace, podName, containerName
        );
        Double memoryUsage = executeQuery(memoryQuery);
        if (memoryUsage != null) {
            metrics.setMemoryUsageBytes(memoryUsage.longValue());
        }

        return metrics;
    }

    /**
     * 执行 Prometheus 查询
     */
    private Double executeQuery(String query) throws Exception {
        String url = String.format("http://%s:%d/api/v1/query?query=%s",
            prometheusHost, prometheusPort, java.net.URLEncoder.encode(query, "UTF-8"));

        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                logger.warn("Prometheus query failed with status code: {}", statusCode);
                return null;
            }

            String content = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(content);

            JsonNode result = root.path("data").path("result");
            if (result.isArray() && result.size() > 0) {
                JsonNode value = result.get(0).path("value");
                if (value.isArray() && value.size() > 1) {
                    return value.get(1).asDouble();
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Failed to execute Prometheus query: {}", query, e);
            throw e;
        }
    }
}

