package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.JaegerSource;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import com.chaosblade.svc.topo.model.trace.TraceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Duration;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 拓扑数据自动刷新服务
 *
 * <p>功能： 1. 每隔 interval-seconds 秒自动刷新拓扑数据 2. 从 Jaeger 查询最新的 trace 数据 3. 转换为拓扑图并更新 currentTopology
 */
@Service
public class TopologyAutoRefreshService {

  private static final Logger logger = LoggerFactory.getLogger(TopologyAutoRefreshService.class);

  // 基础时间间隔（秒）
  private static final int BASE_INTERVAL_SECONDS = 15;

  @Autowired private JaegerQueryService jaegerQueryService;

  @Autowired private TopologyConverterService topologyConverterService;

  @Autowired private TraceParserService traceParserService;

  // 添加缓存服务
  @Autowired private TopologyCacheService topologyCacheService;

  // 注入JaegerSource bean，它已经正确处理了环境变量
  @Autowired private JaegerSource jaegerSource;

  // Jaeger 配置参数，可通过 application.yml 配置
  @Value("${topology.jaeger.host:localhost}")
  private String jaegerHost;

  @Value("${topology.jaeger.port:16685}")
  private int jaegerPort;

  // 添加HTTP API端口配置
  @Value("${topology.jaeger.http-port:16686}")
  private int jaegerHttpPort;

  @Value("${topology.sut.service-name:frontend}")
  private String serviceName;

  @Value("${topology.sut.operation-name:all}")
  private String operationName;

  @Value("${topology.auto-refresh.time-range-seconds:15}")
  private int timeRangeSeconds;

  @Value("${topology.auto-refresh.enabled:true}")
  private boolean autoRefreshEnabled;

  // 添加Jaeger查询方式配置：grpc 或 http
  @Value("${topology.jaeger.query-method:grpc}")
  private String jaegerQueryMethod;

  // 是否启用mock模式，如果为true则从本地文件读取trace数据而不是从Jaeger拉取
  @Value("${topo.visualizer.mock:false}")
  private boolean mockMode;

  // 全局变量，代表历史上首次获取 trace 不为空的时间区间
  private volatile TopologyCacheService.TimeKey lastHistoricalTimeKey = null;

  private volatile boolean isRefreshing = false;
  private long lastRefreshTime = 0;
  private int successfulRefreshCount = 0;
  private int failedRefreshCount = 0;

  /** 初始化方法，在服务启动时执行 */
  @PostConstruct
  public void init() {
    logger.info("拓扑数据自动刷新服务初始化开始");
    if (!mockMode) {
      // 在非mock模式下，寻找历史数据
      findHistoricalTimeKey();
    }
    logger.info("拓扑数据自动刷新服务初始化完成");
  }

  /** 使用指数退避方式向前寻找首个获取 trace 记录不为空的时间区间 */
  private void findHistoricalTimeKey() {
    logger.info("开始寻找历史时间区间");

    long currentTime = System.currentTimeMillis();
    long endTime = currentTime;
    int multiplier = 1; // 倍数，用于指数增长

    // 12小时的毫秒数
    long twelveHoursInMillis = 12 * 60 * 60 * 1000L;

    // 按照 15*1, 15*2, 15*4, 15*8 的方式向前查找，直到累积到大约12小时的回推时间
    while (multiplier <= 4 * 60 * 12) {
      int interval = BASE_INTERVAL_SECONDS * multiplier;
      long startTime = endTime - Duration.ofSeconds(interval).toMillis();

      // 检查是否超过了12小时的回推时间限制
      if (currentTime - startTime > twelveHoursInMillis) {
        logger.info("回推时间已超过12小时限制，停止寻找历史时间区间");
        break;
      }

      logger.debug("尝试查询时间区间: {} - {}", startTime, endTime);

      try {
        // 尝试查询该时间区间的 trace 数据
        TraceData traceData = queryTracesByConfiguredMethod(startTime, endTime);

        if (traceData != null && traceData.getData() != null && !traceData.getData().isEmpty()) {
          // 找到了有数据的时间区间
          lastHistoricalTimeKey = new TopologyCacheService.TimeKey(startTime, endTime);
          logger.info("找到历史时间区间: {} - {}, 区间长度: {}秒", startTime, endTime, interval);

          // 将该时间区间的拓扑数据存入缓存
          TopologyGraph topology = topologyConverterService.convertTraceToTopology(traceData);
          topologyCacheService.put(startTime, endTime, topology);
          logger.debug("历史时间区间的拓扑数据已存入缓存");
          return;
        } else {
          logger.debug("时间区间 {} - {} 没有找到 trace 数据", startTime, endTime);
        }
      } catch (Exception e) {
        logger.warn("查询时间区间 {} - {} 时发生异常: {}", startTime, endTime, e.getMessage());
      }

      // 更新 endTime 和 multiplier，进行指数退避
      endTime = startTime;
      multiplier *= 2; // 指数增长
    }

    logger.info("未找到历史时间区间，将使用当前时间区间作为默认值");
    // 如果没找到历史数据，使用当前时间区间作为默认值
    long startTime = currentTime - Duration.ofSeconds(timeRangeSeconds).toMillis();
    lastHistoricalTimeKey = new TopologyCacheService.TimeKey(startTime, currentTime);
  }

  /** 根据配置的方法查询 trace 数据 */
  private TraceData queryTracesByConfiguredMethod(long startTime, long endTime) {
    if ("http".equalsIgnoreCase(jaegerQueryMethod)) {
      // 使用HTTP API查询
      if (serviceName != null
          && !serviceName.isEmpty()
          && operationName != null
          && !operationName.isEmpty()
          && !"all".equalsIgnoreCase(operationName)) {
        // 如果指定了服务和操作，使用queryTracesByOperationHttp方法
        // 使用注入的JaegerSource bean，但更新service和operation信息
        JaegerSource effectiveJaegerSource = new JaegerSource();
        effectiveJaegerSource.setHost(jaegerSource.getHost());
        effectiveJaegerSource.setHttpPort(jaegerSource.getHttpPort());
        effectiveJaegerSource.setEntryService(serviceName);
        effectiveJaegerSource.setBasePath(jaegerSource.getBasePath());
        effectiveJaegerSource.setLimit(jaegerSource.getLimit());
        effectiveJaegerSource.setSystemKey(jaegerSource.getSystemKey());

        return jaegerQueryService.queryTracesByOperationHttp(
            effectiveJaegerSource, serviceName, operationName, startTime, endTime);
      } else if (serviceName != null && !serviceName.isEmpty()) {
        // 如果只指定了服务，使用queryTracesByServiceHttp方法
        // 使用注入的JaegerSource bean，但更新service信息
        JaegerSource effectiveJaegerSource = new JaegerSource();
        effectiveJaegerSource.setHost(jaegerSource.getHost());
        effectiveJaegerSource.setHttpPort(jaegerSource.getHttpPort());
        effectiveJaegerSource.setEntryService(serviceName);
        effectiveJaegerSource.setBasePath(jaegerSource.getBasePath());
        effectiveJaegerSource.setLimit(jaegerSource.getLimit());
        effectiveJaegerSource.setSystemKey(jaegerSource.getSystemKey());

        return jaegerQueryService.queryTracesByServiceHttp(
            effectiveJaegerSource, startTime, endTime);
      } else {
        // 否则使用queryTracesByServiceHttp方法（不指定特定服务和操作）
        // 直接使用注入的JaegerSource bean
        return jaegerQueryService.queryTracesByServiceHttp(jaegerSource, startTime, endTime);
      }
    } else {
      // 默认使用gRPC查询
      return jaegerQueryService.queryTracesByOperation(
          jaegerSource.getHost(),
          jaegerPort,
          jaegerSource.getEntryService(),
          operationName,
          startTime,
          endTime);
    }
  }

  /** 定时刷新拓扑数据 - 每隔 interval-seconds 秒执行一次 */
  @Scheduled(
      fixedRateString = "${topology.auto-refresh.interval-seconds:15}000") // interval-seconds 秒 =
  // interval-seconds*1000 毫秒
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

      logger.info("拓扑数据自动刷新完成 (成功次数: {}, 失败次数: {})", successfulRefreshCount, failedRefreshCount);
    } catch (Exception e) {
      failedRefreshCount++;
      logger.error(
          "拓扑数据自动刷新失败 (成功次数: {}, 失败次数: {}): {}",
          successfulRefreshCount,
          failedRefreshCount,
          e.getMessage(),
          e);
    } finally {
      isRefreshing = false;
    }
  }

  /** 手动触发拓扑数据刷新 */
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

  /** 执行拓扑数据刷新的核心逻辑 */
  private void refreshTopologyData() {
    logger.debug("开始刷新拓扑数据，从 Jaeger 查询 trace 数据");

    try {
      TraceData traceData;

      if (mockMode) {
        // 如果启用mock模式，从本地文件读取trace数据
        traceData = loadMockTraceData();
        logger.info("使用mock模式加载trace数据");
      } else {
        // 使用注入的JaegerSource bean中的配置作为基础配置
        String effectiveJaegerHost = jaegerSource.getHost();
        int effectiveJaegerPort = jaegerPort;
        int effectiveJaegerHttpPort = jaegerSource.getHttpPort();
        String effectiveServiceName = jaegerSource.getEntryService();

        // 检查环境变量并覆盖配置（保持原有的环境变量处理逻辑）
        // 环境变量优先级高于application.yml配置和注入的bean配置
        String envJaegerHost = System.getenv("JaegerHost");
        String envJaegerPort = System.getenv("JaegerPort");
        String envEntryService = System.getenv("EntryService");

        // 添加调试日志
        logger.debug(
            "环境变量检查: JaegerHost={}, JaegerPort={}, EntryService={}",
            envJaegerHost,
            envJaegerPort,
            envEntryService);
        logger.debug(
            "注入的JaegerSource配置: host={}, httpPort={}, entryService={}",
            jaegerSource.getHost(),
            jaegerSource.getHttpPort(),
            jaegerSource.getEntryService());

        // 使用环境变量覆盖配置
        if (envJaegerHost != null && !envJaegerHost.isEmpty()) {
          effectiveJaegerHost = envJaegerHost;
        }
        if (envEntryService != null && !envEntryService.isEmpty()) {
          effectiveServiceName = envEntryService;
        }

        // 解析端口环境变量（JaegerPort环境变量对应HTTP端口）
        if (envJaegerPort != null && !envJaegerPort.isEmpty()) {
          try {
            effectiveJaegerHttpPort = Integer.parseInt(envJaegerPort);
          } catch (NumberFormatException e) {
            logger.warn("无效的 JaegerPort 环境变量值: {}, 使用默认值: {}", envJaegerPort, jaegerHttpPort);
          }
        }

        // 添加调试日志
        logger.debug(
            "生效的配置: effectiveJaegerHost={}, effectiveJaegerHttpPort={}, effectiveServiceName={}",
            effectiveJaegerHost,
            effectiveJaegerHttpPort,
            effectiveServiceName);

        // 计算查询时间范围：当前时间向前推 timeRangeSeconds 秒 (使用毫秒时间戳)
        long endTime = System.currentTimeMillis(); // 毫秒时间戳
        long startTime = endTime - Duration.ofSeconds(timeRangeSeconds).toMillis(); // 向前推指定秒数

        // 根据配置选择查询方式
        if ("http".equalsIgnoreCase(jaegerQueryMethod)) {
          // 使用HTTP API查询
          if (effectiveServiceName != null
              && !effectiveServiceName.isEmpty()
              && operationName != null
              && !operationName.isEmpty()
              && !"all".equalsIgnoreCase(operationName)) {
            // 如果指定了服务和操作，使用queryTracesByOperationHttp方法
            JaegerSource effectiveJaegerSource = new JaegerSource();
            effectiveJaegerSource.setHost(effectiveJaegerHost);
            effectiveJaegerSource.setHttpPort(effectiveJaegerHttpPort);
            effectiveJaegerSource.setEntryService(effectiveServiceName); // 确保设置entryService
            effectiveJaegerSource.setBasePath(jaegerSource.getBasePath());
            effectiveJaegerSource.setLimit(jaegerSource.getLimit());
            effectiveJaegerSource.setSystemKey(jaegerSource.getSystemKey());

            traceData =
                jaegerQueryService.queryTracesByOperationHttp(
                    effectiveJaegerSource, effectiveServiceName, operationName, startTime, endTime);
            logger.info(
                "使用HTTP API查询Jaeger数据（指定服务和操作）: host={}, port={}, service={}, operation={}",
                effectiveJaegerHost,
                effectiveJaegerHttpPort,
                effectiveServiceName,
                operationName);
          } else if (effectiveServiceName != null && !effectiveServiceName.isEmpty()) {
            // 如果只指定了服务，使用queryTracesByServiceHttp方法
            JaegerSource effectiveJaegerSource = new JaegerSource();
            effectiveJaegerSource.setHost(effectiveJaegerHost);
            effectiveJaegerSource.setHttpPort(effectiveJaegerHttpPort);
            effectiveJaegerSource.setEntryService(effectiveServiceName); // 确保设置entryService
            effectiveJaegerSource.setBasePath(jaegerSource.getBasePath());
            effectiveJaegerSource.setLimit(jaegerSource.getLimit());
            effectiveJaegerSource.setSystemKey(jaegerSource.getSystemKey());

            traceData =
                jaegerQueryService.queryTracesByServiceHttp(
                    effectiveJaegerSource, startTime, endTime);
            logger.info(
                "使用HTTP API查询Jaeger数据（仅指定服务）: host={}, port={}, service={}",
                effectiveJaegerHost,
                effectiveJaegerHttpPort,
                effectiveServiceName);
          } else {
            // 否则使用queryTracesByServiceHttp方法（不指定特定服务和操作）
            JaegerSource effectiveJaegerSource = new JaegerSource();
            effectiveJaegerSource.setHost(effectiveJaegerHost);
            effectiveJaegerSource.setHttpPort(effectiveJaegerHttpPort);
            effectiveJaegerSource.setEntryService(serviceName); // 使用默认服务名
            effectiveJaegerSource.setBasePath(jaegerSource.getBasePath());
            effectiveJaegerSource.setLimit(jaegerSource.getLimit());
            effectiveJaegerSource.setSystemKey(jaegerSource.getSystemKey());

            traceData =
                jaegerQueryService.queryTracesByServiceHttp(
                    effectiveJaegerSource, startTime, endTime);
            logger.info(
                "使用HTTP API查询Jaeger数据（使用默认服务）: host={}, port={}, service={}",
                effectiveJaegerHost,
                effectiveJaegerHttpPort,
                serviceName);
          }
        } else {
          // 默认使用gRPC查询
          traceData =
              jaegerQueryService.queryTracesByOperation(
                  effectiveJaegerHost,
                  effectiveJaegerPort,
                  effectiveServiceName,
                  operationName,
                  startTime,
                  endTime);
          logger.info(
              "使用gRPC查询Jaeger数据: host={}, port={}, service={}, operation={}",
              effectiveJaegerHost,
              effectiveJaegerPort,
              effectiveServiceName,
              operationName);
        }

        // 如果当前时间查询不到数据，使用历史时间区间的拓扑数据
        if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
          logger.warn("获取当前时间 trace 记录为空，尝试使用历史时间区间的拓扑数据");

          if (lastHistoricalTimeKey != null) {
            // 从缓存中获取历史时间区间的拓扑数据
            TopologyGraph historicalTopology =
                topologyCacheService.get(
                    lastHistoricalTimeKey.getStart(), lastHistoricalTimeKey.getEnd());

            if (historicalTopology != null) {
              logger.info(
                  "使用历史时间区间的拓扑数据: {} - {}",
                  lastHistoricalTimeKey.getStart(),
                  lastHistoricalTimeKey.getEnd());

              // 更新当前拓扑
              topologyConverterService.setCurrentTopology(historicalTopology);
              return; // 直接返回，不继续执行后续逻辑
            } else {
              logger.warn("缓存中未找到历史时间区间的拓扑数据");
            }
          } else {
            logger.warn("未设置历史时间区间");
          }
        }
      }

      if (traceData == null || traceData.getData() == null || traceData.getData().isEmpty()) {
        logger.warn("获取 trace 记录为空");
        return;
      }

      logger.debug("获取到 {} 条 trace 记录", traceData.getData().size());

      // 转换为拓扑图
      TopologyGraph newTopology = topologyConverterService.convertTraceToTopology(traceData);

      // 将拓扑图存入缓存 (使用毫秒时间戳)
      long endTime = System.currentTimeMillis(); // 毫秒时间戳
      long startTime = endTime - Duration.ofSeconds(timeRangeSeconds).toMillis(); // 向前推指定秒数
      topologyCacheService.put(startTime, endTime, newTopology);

      // 更新 lastHistoricalTimeKey 为当前时间区间
      lastHistoricalTimeKey = new TopologyCacheService.TimeKey(startTime, endTime);
      logger.debug("更新 lastHistoricalTimeKey 为当前时间区间: {} - {}", startTime, endTime);

      // 更新当前拓扑
      topologyConverterService.setCurrentTopology(newTopology);

      logger.debug(
          "成功更新拓扑数据：{} 个节点，{} 条边", newTopology.getNodes().size(), newTopology.getEdges().size());

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

  /** 从本地文件加载mock的trace数据 */
  private TraceData loadMockTraceData() {
    try {
      logger.debug("从本地文件加载mock trace数据");

      // 从classpath加载trace文件
      ClassPathResource resource = new ClassPathResource("topo-schema/trace-mock-tt.json");
      InputStream inputStream = resource.getInputStream();

      // 使用ObjectMapper解析JSON
      ObjectMapper objectMapper = new ObjectMapper();
      TraceData traceData = objectMapper.readValue(inputStream, TraceData.class);

      logger.debug(
          "成功加载mock trace数据，包含 {} 条记录",
          traceData.getData() != null ? traceData.getData().size() : 0);

      return traceData;
    } catch (Exception e) {
      logger.error("加载mock trace数据失败: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to load mock trace data", e);
    }
  }

  /** 获取刷新状态信息 */
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
    status.setTimeRangeSeconds(timeRangeSeconds);
    status.setMockMode(mockMode);
    status.setJaegerQueryMethod(jaegerQueryMethod);
    status.setLastHistoricalTimeKey(lastHistoricalTimeKey);
    return status;
  }

  /** 启用自动刷新 */
  public void enableAutoRefresh() {
    this.autoRefreshEnabled = true;
    logger.info("已启用拓扑数据自动刷新");
  }

  /** 禁用自动刷新 */
  public void disableAutoRefresh() {
    this.autoRefreshEnabled = false;
    logger.info("已禁用拓扑数据自动刷新");
  }

  /** 更新 Jaeger 配置 */
  public void updateJaegerConfig(
      String host, int port, String service, String operation, int timeRange) {
    this.jaegerHost = host;
    this.jaegerPort = port;
    this.serviceName = service;
    this.operationName = operation;
    this.timeRangeSeconds = timeRange;

    logger.info(
        "已更新 Jaeger 配置: host={}, port={}, service={}, operation={}, timeRange={}秒",
        host,
        port,
        service,
        operation,
        timeRange);
  }

  /** 更新 Jaeger 配置（包括HTTP端口和查询方式） */
  public void updateJaegerConfig(
      String host,
      int grpcPort,
      int httpPort,
      String service,
      String operation,
      int timeRange,
      String queryMethod) {
    this.jaegerHost = host;
    this.jaegerPort = grpcPort;
    this.jaegerHttpPort = httpPort;
    this.serviceName = service;
    this.operationName = operation;
    this.timeRangeSeconds = timeRange;
    this.jaegerQueryMethod = queryMethod;

    logger.info(
        "已更新 Jaeger 配置: host={}, grpcPort={}, httpPort={}, service={}, operation={}, timeRange={}秒,"
            + " queryMethod={}",
        host,
        grpcPort,
        httpPort,
        service,
        operation,
        timeRange,
        queryMethod);
  }

  /** 获取 lastHistoricalTimeKey */
  public TopologyCacheService.TimeKey getLastHistoricalTimeKey() {
    return lastHistoricalTimeKey;
  }

  /** 刷新状态信息类 */
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
    private int timeRangeSeconds;
    private boolean mockMode;
    private String jaegerQueryMethod;
    private TopologyCacheService.TimeKey lastHistoricalTimeKey;

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

    public int getTimeRangeSeconds() {
      return timeRangeSeconds;
    }

    public void setTimeRangeSeconds(int timeRangeSeconds) {
      this.timeRangeSeconds = timeRangeSeconds;
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

    public TopologyCacheService.TimeKey getLastHistoricalTimeKey() {
      return lastHistoricalTimeKey;
    }

    public void setLastHistoricalTimeKey(TopologyCacheService.TimeKey lastHistoricalTimeKey) {
      this.lastHistoricalTimeKey = lastHistoricalTimeKey;
    }
  }
}
