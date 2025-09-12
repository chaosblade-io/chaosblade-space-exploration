package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.TraceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
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

    @Value("${topology.auto-refresh.jaeger.port:16685}")
    private int jaegerPort;

    // 添加HTTP API端口配置
    @Value("${topology.auto-refresh.jaeger.http-port:16686}")
    private int jaegerHttpPort;

    @Value("${topology.auto-refresh.service-name:frontend}")
    private String serviceName;

    @Value("${topology.auto-refresh.operation-name:all}")
    private String operationName;

    @Value("${topology.auto-refresh.time-range-minutes:15}")
    private int timeRangeMinutes;

    @Value("${topology.auto-refresh.enabled:true}")
    private boolean autoRefreshEnabled;

    // 添加Jaeger查询方式配置：grpc 或 http
    @Value("${topology.auto-refresh.jaeger.query-method:grpc}")
    private String jaegerQueryMethod;

    // 是否启用mock模式，如果为true则从本地文件读取trace数据而不是从Jaeger拉取
    @Value("${topo.visualizer.mock:false}")
    private boolean mockMode;

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

        try {
            TraceData traceData;

            if (mockMode) {
                // 如果启用mock模式，从本地文件读取trace数据
                traceData = loadMockTraceData();
                logger.info("使用mock模式加载trace数据");
            } else {
                // 检查环境变量并覆盖配置
                String envJaegerHost = System.getenv("JaegerHost");
                String envJaegerPort = System.getenv("JaegerPort");
                String envEntryService = System.getenv("EntryService");

                String effectiveJaegerHost = (envJaegerHost != null && !envJaegerHost.isEmpty()) ? envJaegerHost : jaegerHost;
                int effectiveJaegerPort = jaegerPort;
                int effectiveJaegerHttpPort = jaegerHttpPort;
                String effectiveServiceName = (envEntryService != null && !envEntryService.isEmpty()) ? envEntryService : serviceName;

                // 解析端口环境变量
                if (envJaegerPort != null && !envJaegerPort.isEmpty()) {
                    try {
                        effectiveJaegerHttpPort = Integer.parseInt(envJaegerPort);
                    } catch (NumberFormatException e) {
                        logger.warn("无效的 JaegerPort 环境变量值: {}, 使用默认值: {}", envJaegerPort, jaegerHttpPort);
                    }
                }

                // 计算查询时间范围：当前时间向前推 timeRangeMinutes 分钟
                long endTime = System.currentTimeMillis() * 1000; // 转换为微秒
                long startTime = endTime - Duration.ofMinutes(timeRangeMinutes).toNanos() / 1000; // 向前推指定分钟数

                // 根据配置选择查询方式
                if ("http".equalsIgnoreCase(jaegerQueryMethod)) {
                    // 使用HTTP API查询
                    if (effectiveServiceName != null && !effectiveServiceName.isEmpty() && operationName != null && !operationName.isEmpty() &&
                        !"all".equalsIgnoreCase(operationName)) {
                        // 如果指定了服务和操作，使用queryTracesByOperationHttp方法
                        traceData = jaegerQueryService.queryTracesByOperationHttp(
                                effectiveJaegerHost, effectiveJaegerHttpPort, effectiveServiceName, operationName, startTime, endTime);
                        logger.info("使用HTTP API查询Jaeger数据（指定服务和操作）: host={}, port={}, service={}, operation={}",
                                   effectiveJaegerHost, effectiveJaegerHttpPort, effectiveServiceName, operationName);
                    } else if (effectiveServiceName != null && !effectiveServiceName.isEmpty()) {
                        // 如果只指定了服务，使用queryTracesByServiceHttp方法
                        traceData = jaegerQueryService.queryTracesByServiceHttp(
                                effectiveJaegerHost, effectiveJaegerHttpPort, effectiveServiceName, startTime, endTime);
                        logger.info("使用HTTP API查询Jaeger数据（仅指定服务）: host={}, port={}, service={}",
                                   effectiveJaegerHost, effectiveJaegerHttpPort, effectiveServiceName);
                    } else {
                        // 否则使用queryTracesHttp方法（不指定特定服务和操作）
                        traceData = jaegerQueryService.queryTracesHttp(
                                effectiveJaegerHost, effectiveJaegerHttpPort, startTime, endTime);
                        logger.info("使用HTTP API查询Jaeger数据（不指定服务和操作）: host={}, port={}",
                                   effectiveJaegerHost, effectiveJaegerHttpPort);
                    }
                } else {
                    // 默认使用gRPC查询
                    traceData = jaegerQueryService.queryTracesByOperation(
                            effectiveJaegerHost, effectiveJaegerPort, effectiveServiceName, operationName, startTime, endTime);
                    logger.info("使用gRPC查询Jaeger数据: host={}, port={}, service={}, operation={}",
                               effectiveJaegerHost, effectiveJaegerPort, effectiveServiceName, operationName);
                }
            }

            if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
                logger.warn("获取 trace 记录为空");
                return;
            }

            logger.debug("获取到 {} 条 trace 记录", traceData.getData().size());

            // 转换为拓扑图
            TopologyGraph newTopology = topologyConverterService.convertTraceToTopology(traceData);

            // 更新当前拓扑
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
     * 从本地文件加载mock的trace数据
     */
    private TraceData loadMockTraceData() {
        try {
            logger.debug("从本地文件加载mock trace数据");

            // 从classpath加载trace文件
            ClassPathResource resource = new ClassPathResource("topo-schema/trace-mock-tt.json");
            InputStream inputStream = resource.getInputStream();

            // 使用ObjectMapper解析JSON
            ObjectMapper objectMapper = new ObjectMapper();
            TraceData traceData = objectMapper.readValue(inputStream, TraceData.class);

            logger.debug("成功加载mock trace数据，包含 {} 条记录",
                        traceData.getData() != null ? traceData.getData().size() : 0);

            return traceData;
        } catch (Exception e) {
            logger.error("加载mock trace数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load mock trace data", e);
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
        status.setJaegerHttpPort(jaegerHttpPort);
        status.setServiceName(serviceName);
        status.setOperationName(operationName);
        status.setTimeRangeMinutes(timeRangeMinutes);
        status.setMockMode(mockMode);
        status.setJaegerQueryMethod(jaegerQueryMethod);
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
     * 更新 Jaeger 配置（包括HTTP端口和查询方式）
     */
    public void updateJaegerConfig(String host, int grpcPort, int httpPort, String service, String operation,
                                  int timeRange, String queryMethod) {
        this.jaegerHost = host;
        this.jaegerPort = grpcPort;
        this.jaegerHttpPort = httpPort;
        this.serviceName = service;
        this.operationName = operation;
        this.timeRangeMinutes = timeRange;
        this.jaegerQueryMethod = queryMethod;

        logger.info("已更新 Jaeger 配置: host={}, grpcPort={}, httpPort={}, service={}, operation={}, timeRange={}分钟, queryMethod={}",
                   host, grpcPort, httpPort, service, operation, timeRange, queryMethod);
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
        private int jaegerHttpPort;
        private String serviceName;
        private String operationName;
        private int timeRangeMinutes;
        private boolean mockMode;
        private String jaegerQueryMethod;

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

        public int getJaegerHttpPort() {
            return jaegerHttpPort;
        }

        public void setJaegerHttpPort(int jaegerHttpPort) {
            this.jaegerHttpPort = jaegerHttpPort;
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

        public boolean isMockMode() {
            return mockMode;
        }

        public void setMockMode(boolean mockMode) {
            this.mockMode = mockMode;
        }

        public String getJaegerQueryMethod() {
            return jaegerQueryMethod;
        }

        public void setJaegerQueryMethod(String jaegerQueryMethod) {
            this.jaegerQueryMethod = jaegerQueryMethod;
        }
    }

}
