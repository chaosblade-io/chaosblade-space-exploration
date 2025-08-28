package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.TraceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 拓扑数据自动刷新服务
 * 
 * 功能：
 * 1. 每隔 15 秒自动刷新拓扑数据
 * 2. 从 Jaeger 查询最新的 trace 数据
 * 3. 转换为拓扑图并更新 currentTopology
 */
@Service
public class TopologyAutoRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(TopologyAutoRefreshService.class);

    @Autowired
    private JaegerQueryService jaegerQueryService;

    @Autowired
    private TopologyConverterService topologyConverterService;

    @Autowired
    private TraceParserService traceParserService;

    // Jaeger 配置参数，可通过 application.yml 配置
    @Value("${topology.auto-refresh.jaeger.host:localhost}")
    private String jaegerHost;

    @Value("${topology.auto-refresh.jaeger.port:14250}")
    private int jaegerPort;

    @Value("${topology.auto-refresh.service-name:frontend}")
    private String serviceName;

    @Value("${topology.auto-refresh.operation-name:all}")
    private String operationName;

    @Value("${topology.auto-refresh.time-range-minutes:15}")
    private int timeRangeMinutes;

    @Value("${topology.auto-refresh.enabled:true}")
    private boolean autoRefreshEnabled;

    private volatile boolean isRefreshing = false;
    private long lastRefreshTime = 0;
    private int successfulRefreshCount = 0;
    private int failedRefreshCount = 0;

    /**
     * 定时刷新拓扑数据 - 每隔 15 秒执行一次
     */
    @Scheduled(fixedRate = 15000) // 15 秒 = 15000 毫秒
    public void refreshTopologyPeriodically() {
        if (!autoRefreshEnabled) {
            logger.debug("自动刷新功能已禁用");
            return;
        }

        if (isRefreshing) {
            logger.warn("上次刷新仍在进行中，跳过本次刷新");
            return;
        }

        try {
            isRefreshing = true;
            refreshTopologyData();
            successfulRefreshCount++;
            lastRefreshTime = System.currentTimeMillis();
            
            logger.info("拓扑数据自动刷新完成 (成功次数: {}, 失败次数: {})", 
                       successfulRefreshCount, failedRefreshCount);
        } catch (Exception e) {
            failedRefreshCount++;
            logger.error("拓扑数据自动刷新失败 (成功次数: {}, 失败次数: {}): {}", 
                        successfulRefreshCount, failedRefreshCount, e.getMessage(), e);
        } finally {
            isRefreshing = false;
        }
    }

    /**
     * 手动触发拓扑数据刷新
     */
    public void manualRefresh() {
        logger.info("手动触发拓扑数据刷新");
        try {
            refreshTopologyData();
            logger.info("手动刷新拓扑数据完成");
        } catch (Exception e) {
            logger.error("手动刷新拓扑数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("手动刷新失败", e);
        }
    }

    /**
     * 执行拓扑数据刷新的核心逻辑
     */
    private void refreshTopologyData() {
        logger.debug("开始刷新拓扑数据，从 Jaeger 查询 trace 数据");

        // 计算查询时间范围：当前时间向前推 timeRangeMinutes 分钟
        long endTime = System.currentTimeMillis() * 1000; // 转换为微秒
        long startTime = endTime - Duration.ofMinutes(timeRangeMinutes).toNanos() / 1000; // 向前推指定分钟数

        try {
            // 1. 从 Jaeger 查询最新的 trace 数据
            TraceData traceData = jaegerQueryService.queryTracesByOperation(
                    jaegerHost, jaegerPort, serviceName, operationName, startTime, endTime);

            if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
                logger.warn("从 Jaeger 查询到的 trace 数据为空，使用当前拓扑数据");
                return;
            }

            logger.debug("从 Jaeger 查询到 {} 条 trace 记录", traceData.getData().size());

            // 2. 转换为拓扑图
            TopologyGraph newTopology = topologyConverterService.convertTraceToTopology(traceData);

            // 3. 更新当前拓扑
            topologyConverterService.setCurrentTopology(newTopology);

            logger.debug("成功更新拓扑数据：{} 个节点，{} 条边", 
                        newTopology.getNodes().size(), newTopology.getEdges().size());

        } catch (IllegalArgumentException e) {
            logger.error("Jaeger 查询参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.error("查询 Jaeger 失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("刷新拓扑数据时发生未知错误", e);
            throw new RuntimeException("刷新拓扑数据失败", e);
        }
    }

    /**
     * 获取刷新状态信息
     */
    public RefreshStatus getRefreshStatus() {
        RefreshStatus status = new RefreshStatus();
        status.setEnabled(autoRefreshEnabled);
        status.setRefreshing(isRefreshing);
        status.setLastRefreshTime(lastRefreshTime);
        status.setSuccessfulRefreshCount(successfulRefreshCount);
        status.setFailedRefreshCount(failedRefreshCount);
        status.setJaegerHost(jaegerHost);
        status.setJaegerPort(jaegerPort);
        status.setServiceName(serviceName);
        status.setOperationName(operationName);
        status.setTimeRangeMinutes(timeRangeMinutes);
        return status;
    }

    /**
     * 启用自动刷新
     */
    public void enableAutoRefresh() {
        this.autoRefreshEnabled = true;
        logger.info("已启用拓扑数据自动刷新");
    }

    /**
     * 禁用自动刷新
     */
    public void disableAutoRefresh() {
        this.autoRefreshEnabled = false;
        logger.info("已禁用拓扑数据自动刷新");
    }

    /**
     * 更新 Jaeger 配置
     */
    public void updateJaegerConfig(String host, int port, String service, String operation, int timeRange) {
        this.jaegerHost = host;
        this.jaegerPort = port;
        this.serviceName = service;
        this.operationName = operation;
        this.timeRangeMinutes = timeRange;
        
        logger.info("已更新 Jaeger 配置: host={}, port={}, service={}, operation={}, timeRange={}分钟", 
                   host, port, service, operation, timeRange);
    }

    /**
     * 刷新状态信息类
     */
    public static class RefreshStatus {
        private boolean enabled;
        private boolean refreshing;
        private long lastRefreshTime;
        private int successfulRefreshCount;
        private int failedRefreshCount;
        private String jaegerHost;
        private int jaegerPort;
        private String serviceName;
        private String operationName;
        private int timeRangeMinutes;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRefreshing() {
            return refreshing;
        }

        public void setRefreshing(boolean refreshing) {
            this.refreshing = refreshing;
        }

        public long getLastRefreshTime() {
            return lastRefreshTime;
        }

        public void setLastRefreshTime(long lastRefreshTime) {
            this.lastRefreshTime = lastRefreshTime;
        }

        public int getSuccessfulRefreshCount() {
            return successfulRefreshCount;
        }

        public void setSuccessfulRefreshCount(int successfulRefreshCount) {
            this.successfulRefreshCount = successfulRefreshCount;
        }

        public int getFailedRefreshCount() {
            return failedRefreshCount;
        }

        public void setFailedRefreshCount(int failedRefreshCount) {
            this.failedRefreshCount = failedRefreshCount;
        }

        public String getJaegerHost() {
            return jaegerHost;
        }

        public void setJaegerHost(String jaegerHost) {
            this.jaegerHost = jaegerHost;
        }

        public int getJaegerPort() {
            return jaegerPort;
        }

        public void setJaegerPort(int jaegerPort) {
            this.jaegerPort = jaegerPort;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getOperationName() {
            return operationName;
        }

        public void setOperationName(String operationName) {
            this.operationName = operationName;
        }

        public int getTimeRangeMinutes() {
            return timeRangeMinutes;
        }

        public void setTimeRangeMinutes(int timeRangeMinutes) {
            this.timeRangeMinutes = timeRangeMinutes;
        }
    }
}