package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.entity.Node;
import com.chaosblade.svc.topo.model.metrics.PrometheusMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 指标转换服务
 * 将 Prometheus 指标数据转换并附加到拓扑节点
 */
@Service
public class MetricsConverterService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsConverterService.class);

    /**
     * 将 Prometheus 指标附加到节点
     */
    public void attachMetricsToNode(Node node, PrometheusMetrics metrics) {
        if (node == null || metrics == null) {
            return;
        }

        logger.debug("Attaching Prometheus metrics to node: {}", node.getNodeId());

        // 获取或创建节点属性
        Node.NodeAttributes attrs = node.getAttrs();
        if (attrs == null) {
            attrs = new Node.NodeAttributes();
            node.setAttrs(attrs);
        }

        // 设置 Prometheus 指标
        attrs.setPrometheus(metrics);

        // 可以在这里进行额外的数据转换或聚合
        // 例如：将资源使用率转换为健康评分
        calculateHealthScore(node, metrics);
    }

    /**
     * 计算节点健康评分
     */
    private void calculateHealthScore(Node node, PrometheusMetrics metrics) {
        if (metrics.getResources() == null) {
            return;
        }

        double healthScore = 100.0;

        // 根据 CPU 使用率扣分
        Double cpuUsage = metrics.getResources().getCpuUsagePercent();
        if (cpuUsage != null) {
            if (cpuUsage > 90) {
                healthScore -= 30;
            } else if (cpuUsage > 70) {
                healthScore -= 15;
            }
        }

        // 根据内存使用率扣分
        Double memoryUsage = metrics.getResources().getMemoryUsagePercent();
        if (memoryUsage != null) {
            if (memoryUsage > 90) {
                healthScore -= 30;
            } else if (memoryUsage > 70) {
                healthScore -= 15;
            }
        }

        // 根据重启次数扣分
        if (metrics.getHealth() != null && metrics.getHealth().getRestartCount() != null) {
            int restarts = metrics.getHealth().getRestartCount();
            if (restarts > 10) {
                healthScore -= 20;
            } else if (restarts > 5) {
                healthScore -= 10;
            }
        }

        // 将健康评分存储到扩展属性
        node.getAttrs().addExtension("healthScore", Math.max(0, healthScore));
    }
}

