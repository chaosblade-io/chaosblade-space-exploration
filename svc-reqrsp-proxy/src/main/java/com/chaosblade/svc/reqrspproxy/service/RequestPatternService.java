package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingSettings;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.HttpReqDef;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.repository.HttpReqDefRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** 请求模式获取服务 核心业务逻辑：根据 reqDefId 获取请求定义，启动录制模式，发起请求，收集请求模式，停止录制 */
@Service
public class RequestPatternService {

  private static final Logger logger = LoggerFactory.getLogger(RequestPatternService.class);

  @Autowired private HttpReqDefRepository httpReqDefRepository;

  @Autowired private RecordingService recordingService;

  @Autowired private HttpRequestExecutor httpRequestExecutor;

  @Autowired private RequestPatternAnalyzer requestPatternAnalyzer;

  @Autowired private TaskStateManager taskStateManager;

  @Autowired private TapCollector tapCollector;

  @Autowired private RecordingSettings recordingSettings;

  @Autowired private StringRedisTemplate redis;

  @Autowired private K8sTapManager k8sTapManager;

  @Autowired private RecordingStateService recordingStateService;

  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Autowired
  private com.chaosblade.svc.reqrspproxy.repository.RequestPatternRepository
      requestPatternRepository;

  /** 测试 Redis 连接 */
  public void testRedisConnection() {
    recordingStateService.testConnection();
  }

  /** 获取 Redis 数据统计 */
  public Map<String, Object> getRedisStats() {
    return recordingStateService.getRedisStats();
  }

  /** 获取请求模式 */
  public RequestPatternResponse getRequestPattern(RequestPatternRequest request) {
    // 60 秒幂等防抖：基于 reqDefId + namespace + 排序后的 serviceList
    List<String> svc = new ArrayList<>(request.getServiceList());
    Collections.sort(svc);
    String raw = request.getReqDefId() + "|" + request.getNamespace() + "|" + String.join(",", svc);
    String idempKey = "task:pattern:idemp:" + sha256Hex(raw);

    String taskId = generateTaskId();
    Boolean acquired = redis.opsForValue().setIfAbsent(idempKey, taskId, Duration.ofSeconds(60));
    if (Boolean.FALSE.equals(acquired)) {
      // 已存在同参数任务，复用其 taskId
      String existingTaskId = redis.opsForValue().get(idempKey);
      if (existingTaskId != null) {
        RequestPatternResponse existing = taskStateManager.getTaskState(existingTaskId);
        if (existing != null) {
          logger.info(
              "Idempotent hit: reuse existing task {} for same parameters within 60s",
              existingTaskId);
          return existing;
        } else {
          // 仍返回占位响应，提示复用
          RequestPatternResponse placeholder =
              new RequestPatternResponse(
                  existingTaskId,
                  TaskStateManager.TaskPhase.INITIALIZING.name(),
                  "已有相同参数任务在 60 秒窗口内，复用中...");
          placeholder.setReqDefId(request.getReqDefId());
          placeholder.setNamespace(request.getNamespace());
          placeholder.setServiceList(request.getServiceList());
          placeholder.setStartTime(LocalDateTime.now());
          return placeholder;
        }
      }
    }

    logger.info(
        "Starting request pattern task {}: reqDefId={}, namespace={}, services={}",
        taskId,
        request.getReqDefId(),
        request.getNamespace(),
        request.getServiceList());

    RequestPatternResponse response =
        new RequestPatternResponse(taskId, TaskStateManager.TaskPhase.INITIALIZING.name(), "任务已启动");
    response.setReqDefId(request.getReqDefId());
    response.setNamespace(request.getNamespace());
    response.setServiceList(request.getServiceList());
    response.setExecutionId(request.getExecutionId());
    response.setStartTime(LocalDateTime.now());

    // 保存初始状态
    taskStateManager.saveTaskState(response);

    // 异步执行完整流程
    CompletableFuture.runAsync(() -> executeRequestPatternFlow(request, response));

    return response;
  }

  /** 获取任务状态 */
  public RequestPatternResponse getTaskStatus(String taskId) {
    RequestPatternResponse response = taskStateManager.getTaskState(taskId);
    if (response == null) {
      response = new RequestPatternResponse();
      response.setTaskId(taskId);
      response.setStatus("NOT_FOUND");
      response.setMessage("任务不存在");
    }
    return response;
  }

  /** 手动触发任务的请求发起 */
  public boolean triggerRequestsForTask(String taskId) {
    RequestPatternResponse response = taskStateManager.getTaskState(taskId);
    if (response == null) {
      logger.warn("Task {} not found", taskId);
      return false;
    }

    // 检查任务状态是否允许触发请求
    String status = response.getStatus();
    if (!"TRIGGERING_REQUESTS".equals(status) && !"ROLLING_UPDATE".equals(status)) {
      logger.warn("Task {} is in status {}, cannot trigger requests", taskId, status);
      return false;
    }

    try {
      // 获取请求定义
      HttpReqDef reqDef = getRequestDefinition(response.getReqDefId());

      // 创建请求参数
      RequestPatternRequest request = new RequestPatternRequest();
      request.setReqDefId(response.getReqDefId());
      request.setNamespace(response.getNamespace());
      request.setServiceList(response.getServiceList());
      request.setRequestCount(1);

      // 更新状态
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.TRIGGERING_REQUESTS, "手动触发HTTP请求中...");

      // 发起请求
      triggerHttpRequests(reqDef, request);

      logger.info("Requests triggered successfully for task {}", taskId);
      return true;

    } catch (Exception e) {
      logger.error("Failed to trigger requests for task {}: {}", taskId, e.getMessage(), e);
      taskStateManager.setTaskFailed(taskId, "手动触发请求失败: " + e.getMessage());
      return false;
    }
  }

  /** 获取任务详细信息 */
  public RequestPatternResponse getTaskDetails(String taskId) {
    RequestPatternResponse response = taskStateManager.getTaskState(taskId);
    if (response == null) {
      response = new RequestPatternResponse();
      response.setTaskId(taskId);
      response.setStatus("NOT_FOUND");
      response.setMessage("任务不存在");
    }
    return response;
  }

  /** 停止请求模式记录 */
  public boolean stopRequestPatternRecording(String taskId) {
    RequestPatternResponse response = taskStateManager.getTaskState(taskId);
    if (response == null) {
      logger.warn("Task {} not found", taskId);
      return false;
    }

    String status = response.getStatus();

    // 检查任务状态是否允许停止
    if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
      logger.warn("Task {} is already in final state: {}", taskId, status);
      return false;
    }

    try {
      logger.info("Stopping request pattern recording for task {}", taskId);

      // 更新任务状态为停止中
      taskStateManager.updateTaskPhase(taskId, TaskStateManager.TaskPhase.COMPLETED, "手动停止录制中...");

      // 获取录制ID并停止录制
      String recordingId = response.getRecordingId();
      if (recordingId != null) {
        // 如果有多个录制ID，需要从任务状态中获取所有录制ID
        // 这里简化处理，假设只有一个主录制ID
        try {
          recordingService.stop(recordingId);
          logger.info("Recording {} stopped successfully", recordingId);
        } catch (Exception e) {
          logger.error("Failed to stop recording {}: {}", recordingId, e.getMessage(), e);
        }
      }

      // 清理相关的 ConfigMap
      try {
        logger.info("Cleaning up ConfigMaps for task {}", taskId);
        cleanupConfigMaps(response.getServiceList(), response.getNamespace());
      } catch (Exception e) {
        logger.error("Failed to cleanup ConfigMaps for task {}: {}", taskId, e.getMessage(), e);
      }

      // 收集已有的录制数据并分析
      CompletableFuture.runAsync(
          () -> {
            try {
              // 收集录制数据
              List<RecordedEntry> recordedEntries = new ArrayList<>();
              if (recordingId != null) {
                recordedEntries = recordingService.getEntries(recordingId, 0, 1000);
              }

              // 分析请求模式
              List<ServiceRequestPattern> patterns =
                  requestPatternAnalyzer.analyzeRequestPatterns(
                      recordedEntries, response.getServiceList());

              // 更新最终结果
              response.setRequestPatterns(patterns);
              response.setTotalRecordedRequests(recordedEntries.size());
              response.setAnalyzedServices(patterns.size());

              taskStateManager.setTaskCompleted(taskId, response);

              logger.info(
                  "Task {} stopped and completed with {} patterns", taskId, patterns.size());

            } catch (Exception e) {
              logger.error("Failed to complete stopped task {}: {}", taskId, e.getMessage(), e);
              taskStateManager.setTaskFailed(taskId, "停止后处理失败: " + e.getMessage());
            }
          });

      return true;

    } catch (Exception e) {
      logger.error("Failed to stop recording for task {}: {}", taskId, e.getMessage(), e);
      taskStateManager.setTaskFailed(taskId, "停止录制失败: " + e.getMessage());
      return false;
    }
  }

  /** 执行完整的请求模式获取流程 */
  private void executeRequestPatternFlow(
      RequestPatternRequest request, RequestPatternResponse response) {
    String taskId = response.getTaskId();
    List<String> recordingIds = new ArrayList<>();

    try {
      // 1. 获取请求定义
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.INITIALIZING, "获取请求定义中...");
      logger.info("Step 1: Getting request definition for reqDefId={}", request.getReqDefId());
      HttpReqDef reqDef = getRequestDefinition(request.getReqDefId());

      // 输出请求定义详情用于调试
      logger.info("=== 数据库请求定义详情 ===");
      logger.info("ID: {}", reqDef.getId());
      logger.info("Code: {}", reqDef.getCode());
      logger.info("Name: {}", reqDef.getName());
      logger.info("Method: {}", reqDef.getMethod());
      logger.info("URL Template: {}", reqDef.getUrlTemplate());
      logger.info("Headers: {}", reqDef.getHeaders());
      logger.info("Query Params: {}", reqDef.getQueryParams());
      logger.info("Body Mode: {}", reqDef.getBodyMode());
      logger.info("Content Type: {}", reqDef.getContentType());
      logger.info("Body Template: {}", reqDef.getBodyTemplate());
      logger.info("Raw Body: {}", reqDef.getRawBody());
      logger.info("=== 请求定义详情结束 ===");

      // 2. 启动录制模式（应用规则和 Envoy 配置）
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.APPLYING_RULES, "为所有服务应用录制规则和 Envoy 配置中...");
      logger.info(
          "Step 2: Starting recording for services (includes Envoy configuration): {}",
          request.getServiceList());
      recordingIds = startRecordingForServices(request);
      response.setRecordingId(recordingIds.isEmpty() ? null : recordingIds.get(0));
      taskStateManager.saveTaskState(response);

      // 3. 等待滚动更新完成
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.ROLLING_UPDATE, "等待服务滚动更新完成...");
      logger.info("Step 3: Waiting for rolling update to complete...");
      waitForRollingUpdateComplete(request);

      // 4. 等待录制启动完成
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.APPLYING_RULES, "等待录制模式启动完成...");
      logger.info("Step 4: Waiting for recording to be ready...");
      waitForAllRecordingsReady(recordingIds);

      // 5. 发起请求（如果启用自动触发）
      if (request.getAutoTriggerRequest()) {
        taskStateManager.updateTaskPhase(
            taskId, TaskStateManager.TaskPhase.TRIGGERING_REQUESTS, "发起HTTP请求中...");
        logger.info("Step 5: Triggering HTTP requests...");
        triggerHttpRequests(reqDef, request);
      } else {
        taskStateManager.updateTaskPhase(
            taskId, TaskStateManager.TaskPhase.TRIGGERING_REQUESTS, "等待外部触发请求，请手动执行相关业务操作...");
        logger.info("Step 5: Waiting for external request triggering...");
      }

      // 6. 等待请求完成和数据收集
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.COLLECTING_DATA, "等待数据收集完成...");
      logger.info("Step 6: Waiting for data collection...");
      waitForDataCollection(request);

      // 7. 收集录制数据（前添加调试导出步骤）
      logger.info("Step 7: Collecting recorded data...");
      // 调试：导出每个服务的原始录制数据（结构化 entries）以及容器内原始 tap JSON 文件
      try {
        Path debugDir = createRecordingDebugDir();
        exportRawDataPerService(debugDir, request.getServiceList(), recordingIds);
        // 可选：导出容器内 Envoy 生成的 tap 原始 JSON 文件，默认关闭
        if (recordingSettings.isDebugExportRawEnabled()) {
          for (String recId : recordingIds) {
            try {
              RecordingState st = recordingStateService.loadState(recId);
              Path rawDir = debugDir.resolve("tap-raw-files");
              int n = tapCollector.exportRawTapFiles(recId, st, rawDir);
              logger.info(
                  "Exported {} raw tap files for recording {} to {}",
                  n,
                  recId,
                  rawDir.toAbsolutePath());
            } catch (Exception ex) {
              logger.warn("Export raw tap files failed for {}: {}", recId, ex.getMessage());
            }
          }
        }
      } catch (Exception e) {
        logger.warn("Debug export failed: {}", e.getMessage());
      }

      List<RecordedEntry> recordedEntries = collectAllRecordedData(recordingIds);
      taskStateManager.updateTaskProgress(taskId, recordedEntries.size(), null);

      // 8. 分析请求模式
      taskStateManager.updateTaskPhase(
          taskId, TaskStateManager.TaskPhase.ANALYZING_PATTERNS, "分析请求模式中...");
      logger.info("Step 8: Analyzing request patterns...");
      List<ServiceRequestPattern> patterns =
          requestPatternAnalyzer.analyzeRequestPatterns(recordedEntries, request.getServiceList());
      response.setRequestPatterns(patterns);
      response.setTotalRecordedRequests(recordedEntries.size());
      response.setAnalyzedServices(patterns.size());

      // 8.1 分析结果持久化到 MySQL
      try {
        persistPatterns(request.getExecutionId(), patterns);
      } catch (Exception persistEx) {
        logger.error("Persisting request patterns failed: {}", persistEx.getMessage(), persistEx);
      }

      // 9. 停止录制
      logger.info("Step 9: Stopping recording...");
      stopAllRecordings(recordingIds);

      // 10. 完成任务
      taskStateManager.setTaskCompleted(taskId, response);

      logger.info(
          "Request pattern task {} completed successfully. Found {} patterns for {} services",
          taskId,
          patterns.stream().mapToInt(p -> p.getRequestMode().size()).sum(),
          patterns.size());

    } catch (Exception e) {
      logger.error("Request pattern task {} failed: {}", taskId, e.getMessage(), e);

      // 确保停止所有录制
      if (!recordingIds.isEmpty()) {
        try {
          stopAllRecordings(recordingIds);
        } catch (Exception stopError) {
          logger.error("Failed to stop recordings: {}", stopError.getMessage());
        }
      }

      taskStateManager.setTaskFailed(taskId, e.getMessage());
    }
    // 统一的资源清理与生命周期收尾：无论成功或失败，均执行
    try {
      if (recordingIds != null && !recordingIds.isEmpty()) {
        logger.info(
            "Finalizing resources for task {}: cancelling auto-stop and cleaning states...",
            taskId);
        for (String recId : recordingIds) {
          try {
            // 先停止任何可能的自动停止任务
            k8sTapManager.cancelAutoStop(recId);
          } catch (Exception e) {
            logger.debug("Cancel auto-stop ignored for {}: {}", recId, e.getMessage());
          }
          try {
            // 清理 RecordingState，避免后台任务继续处理已结束的 recording
            if (recordingStateService.exists(recId)) {
              recordingStateService.deleteState(recId);
              logger.info("Recording state {} cleaned", recId);
            }
          } catch (Exception e) {
            logger.debug("Delete state ignored for {}: {}", recId, e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Finalize resources failed for task {}: {}", taskId, e.getMessage());
    }
  }

  /** 获取请求定义 */
  private HttpReqDef getRequestDefinition(Long reqDefId) {
    return httpReqDefRepository
        .findById(reqDefId)
        .orElseThrow(() -> new RuntimeException("Request definition not found: " + reqDefId));
  }

  /** 为所有服务启动录制模式（并发执行） */
  private List<String> startRecordingForServices(RequestPatternRequest request) {
    // 创建录制规则，监听所有 /api 前缀的请求
    // 注意：使用 "/api" 作为前缀匹配，可以匹配 /api、/api/、/api/v1 等所有情况
    List<RecordingRule> rules =
        Arrays.asList(
            new RecordingRule("/api", "GET"),
            new RecordingRule("/api", "POST"),
            new RecordingRule("/api", "PUT"),
            new RecordingRule("/api", "DELETE"),
            new RecordingRule("/api", "PATCH"));

    List<String> recordingIds = Collections.synchronizedList(new ArrayList<>());

    // 使用并发执行来为所有服务启动录制
    List<CompletableFuture<Void>> futures =
        request.getServiceList().stream()
            .map(
                serviceName ->
                    CompletableFuture.runAsync(
                        () -> {
                          try {
                            String recordingId =
                                startRecordingForSingleServiceWithRetry(
                                    serviceName,
                                    request.getNamespace(),
                                    rules,
                                    request.getDurationSec());
                            if (recordingId != null) {
                              recordingIds.add(recordingId);
                            }
                          } catch (Exception e) {
                            logger.error(
                                "Failed to start recording for service {} after retries: {}",
                                serviceName,
                                e.getMessage(),
                                e);
                          }
                        }))
            .collect(Collectors.toList());

    // 等待所有服务的录制启动完成
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(120, TimeUnit.SECONDS); // 最多等待2分钟
    } catch (Exception e) {
      logger.error("Timeout or error waiting for recording startup: {}", e.getMessage(), e);
    }

    logger.info(
        "Recording startup completed. Successfully started {} out of {} services",
        recordingIds.size(),
        request.getServiceList().size());

    // 详细记录每个服务的录制状态
    logger.info("Started recordings: {}", recordingIds);
    for (int i = 0; i < request.getServiceList().size(); i++) {
      String serviceName = request.getServiceList().get(i);
      boolean hasRecording = i < recordingIds.size();
      if (hasRecording) {
        logger.info("✅ Service {} → Recording started", serviceName);
      } else {
        logger.warn("❌ Service {} → No recording started", serviceName);
      }
    }

    return recordingIds;
  }

  /** 为单个服务启动录制（带重试机制） */
  private String startRecordingForSingleServiceWithRetry(
      String serviceName, String namespace, List<RecordingRule> rules, Integer durationSec) {

    int maxRetries = 3;
    int retryDelayMs = 1000; // 初始延迟1秒

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.info(
            "Starting recording for service: {} (attempt {}/{})", serviceName, attempt, maxRetries);

        StartRecordingRequest recordingRequest = new StartRecordingRequest();
        recordingRequest.setNamespace(namespace);
        recordingRequest.setServiceName(serviceName);
        recordingRequest.setRules(rules);
        recordingRequest.setDurationSec(durationSec);

        RecordingResponse recordingResponse = recordingService.start(recordingRequest);

        logger.info(
            "Recording started successfully for service {}: {}",
            serviceName,
            recordingResponse.getRecordingId());
        return recordingResponse.getRecordingId();

      } catch (Exception e) {
        logger.warn(
            "Failed to start recording for service {} (attempt {}/{}): {}",
            serviceName,
            attempt,
            maxRetries,
            e.getMessage());

        if (attempt < maxRetries) {
          try {
            // 指数退避策略
            int delay = retryDelayMs * (int) Math.pow(2, attempt - 1);
            logger.info("Retrying in {}ms for service {}", delay, serviceName);
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry delay", ie);
          }
        } else {
          logger.error(
              "Failed to start recording for service {} after {} attempts",
              serviceName,
              maxRetries);
          throw new RuntimeException("Failed to start recording after retries", e);
        }
      }
    }

    return null;
  }

  /** 为所有服务应用 Envoy 配置 */
  private void applyEnvoyConfigurationForServices(RequestPatternRequest request) {
    logger.info(
        "Applying Envoy configuration for {} services in namespace {}",
        request.getServiceList().size(),
        request.getNamespace());

    for (String serviceName : request.getServiceList()) {
      try {
        logger.info("Applying Envoy configuration for service: {}", serviceName);

        String configMapName = serviceName + "-envoy-config";

        // 1. 创建或更新 Envoy ConfigMap
        String envoyConfig = generateEnvoyConfig(serviceName);
        k8sTapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyConfig);

        // 2. 注入或更新 Envoy sidecar
        k8sTapManager.injectOrUpdateSidecar(request.getNamespace(), serviceName, configMapName);

        logger.info("Envoy configuration applied successfully for service: {}", serviceName);

      } catch (Exception e) {
        logger.error(
            "Failed to apply Envoy configuration for service {}: {}",
            serviceName,
            e.getMessage(),
            e);
        // 继续为其他服务应用配置
      }
    }

    logger.info("Envoy configuration application completed for all services");
  }

  /** 生成 Envoy 配置 */
  private String generateEnvoyConfig(String serviceName) {
    // 生成基础的 Envoy 配置，启用 HTTP tap 功能
    return String.format(
        """
        admin:
          address:
            socket_address:
              address: 127.0.0.1
              port_value: 9901

        static_resources:
          listeners:
          - name: listener_0
            address:
              socket_address:
                address: 0.0.0.0
                port_value: 8080
            filter_chains:
            - filters:
              - name: envoy.filters.network.http_connection_manager
                typed_config:
                  "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                  stat_prefix: ingress_http
                  access_log:
                  - name: envoy.access_loggers.stdout
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.access_loggers.stream.v3.StdoutAccessLog
                  http_filters:
                  - name: envoy.filters.http.tap
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.filters.http.tap.v3.Tap
                      common_config:
                        static_config:
                          match_config:
                            any_match: true
                          output_config:
                            sinks:
                            - format: JSON_BODY_AS_BYTES
                              file_per_tap:
                                path_prefix: /tmp/envoy_tap_%s
                  - name: envoy.filters.http.router
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
                  route_config:
                    name: local_route
                    virtual_hosts:
                    - name: local_service
                      domains: ["*"]
                      routes:
                      - match:
                          prefix: "/"
                        route:
                          cluster: %s_cluster

          clusters:
          - name: %s_cluster
            connect_timeout: 30s
            type: LOGICAL_DNS
            dns_lookup_family: V4_ONLY
            load_assignment:
              cluster_name: %s_cluster
              endpoints:
              - lb_endpoints:
                - endpoint:
                    address:
                      socket_address:
                        address: 127.0.0.1
                        port_value: 8080
        """,
        serviceName, serviceName, serviceName, serviceName);
  }

  /** 等待滚动更新完成 */
  private void waitForRollingUpdateComplete(RequestPatternRequest request)
      throws InterruptedException {
    logger.info(
        "Waiting for rolling update to complete for {} services in namespace {}",
        request.getServiceList().size(),
        request.getNamespace());

    // 使用 Kubernetes API 真正检查每个服务的 Deployment 状态
    for (String serviceName : request.getServiceList()) {
      try {
        logger.info("Waiting for deployment {} to be ready...", serviceName);
        k8sTapManager.waitRolloutReady(request.getNamespace(), serviceName);
        logger.info("Deployment {} is ready", serviceName);

      } catch (Exception e) {
        logger.error(
            "Failed to wait for deployment {} readiness: {}", serviceName, e.getMessage(), e);
        // 继续等待其他服务，不因为一个服务失败而中断
      }
    }

    // 所有服务就绪后，额外等待确保 Envoy 配置完全生效
    logger.info(
        "All deployments ready, waiting additional 15 seconds for Envoy configuration to"
            + " stabilize...");
    Thread.sleep(15000);

    logger.info("Rolling update completed for all services");
  }

  /** 等待所有录制准备就绪 */
  private void waitForAllRecordingsReady(List<String> recordingIds) throws InterruptedException {
    int maxWaitSeconds = 120; // 增加等待时间，因为要等待多个服务
    int waitedSeconds = 0;

    while (waitedSeconds < maxWaitSeconds) {
      boolean allReady = true;

      for (String recordingId : recordingIds) {
        try {
          RecordingStatusResponse status = recordingService.getStatus(recordingId);
          if (!"RECORDING".equals(status.getStatus())) {
            if ("ERROR".equals(status.getStatus())) {
              logger.error("Recording {} failed: {}", recordingId, status.getMessage());
            } else {
              allReady = false;
              logger.debug(
                  "Recording {} not ready yet, status: {}", recordingId, status.getStatus());
            }
          }
        } catch (Exception e) {
          logger.warn("Failed to check recording status for {}: {}", recordingId, e.getMessage());
          allReady = false;
        }
      }

      if (allReady) {
        logger.info("All {} recordings are ready", recordingIds.size());
        return;
      }

      Thread.sleep(10000); // 等待10秒
      waitedSeconds += 10;
    }

    throw new RuntimeException(
        "Not all recordings became ready within " + maxWaitSeconds + " seconds");
  }

  /** 收集所有录制数据 */
  private List<RecordedEntry> collectAllRecordedData(List<String> recordingIds) {
    // 并行收集每个 recordingId 的数据，提高整体吞吐
    List<CompletableFuture<List<RecordedEntry>>> futures = new ArrayList<>();
    for (String recordingId : recordingIds) {
      CompletableFuture<List<RecordedEntry>> f =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  List<RecordedEntry> entries = recordingService.getEntries(recordingId, 0, 2000);
                  logger.info(
                      "Collected {} entries from recording {}", entries.size(), recordingId);
                  return entries;
                } catch (Exception e) {
                  logger.error(
                      "Failed to collect data from recording {}: {}",
                      recordingId,
                      e.getMessage(),
                      e);
                  return Collections.emptyList();
                }
              });
      futures.add(f);
    }
    List<RecordedEntry> allEntries =
        futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    logger.info("Total collected entries: {}", allEntries.size());
    return allEntries;
  }

  /** 停止所有录制 */
  private void stopAllRecordings(List<String> recordingIds) {
    for (String recordingId : recordingIds) {
      try {
        recordingService.stop(recordingId);
        logger.info("Recording {} stopped successfully", recordingId);
      } catch (Exception e) {
        logger.error("Failed to stop recording {}: {}", recordingId, e.getMessage(), e);
      }
    }
  }

  /** 触发HTTP请求 */
  private void triggerHttpRequests(HttpReqDef reqDef, RequestPatternRequest request) {
    try {
      // 等待一段时间让录制稳定
      Thread.sleep(request.getRequestDelaySeconds() * 1000L);

      // 准备请求变量（可以从示例数据中提取）
      Map<String, Object> variables = prepareRequestVariables(reqDef);

      // 发起多次请求
      for (int i = 0; i < request.getRequestCount(); i++) {
        logger.info("Triggering HTTP request {}/{}", i + 1, request.getRequestCount());

        Mono<HttpRequestExecutor.HttpRequestResult> resultMono =
            httpRequestExecutor.executeRequest(reqDef, variables);

        HttpRequestExecutor.HttpRequestResult result = resultMono.block();

        if (result != null && result.isSuccess()) {
          logger.info(
              "Request {}/{} completed successfully with status {}",
              i + 1,
              request.getRequestCount(),
              result.getStatusCode());
        } else {
          logger.warn(
              "Request {}/{} failed: {}",
              i + 1,
              request.getRequestCount(),
              result != null ? result.getErrorMessage() : "Unknown error");
        }

        // 请求间隔
        if (i < request.getRequestCount() - 1) {
          Thread.sleep(2000); // 2秒间隔
        }
      }

      // 所有请求完成后，额外等待确保数据被 Envoy 完全捕获
      logger.info("All requests completed, waiting additional time for Envoy to capture data...");
      Thread.sleep(5000); // 额外等待5秒确保数据被捕获

    } catch (Exception e) {
      logger.error("Failed to trigger HTTP requests: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to trigger HTTP requests", e);
    }
  }

  /** 准备请求变量 */
  private Map<String, Object> prepareRequestVariables(HttpReqDef reqDef) {
    Map<String, Object> variables = new HashMap<>();

    logger.info("=== 准备请求变量 ===");
    logger.info("请求定义代码: {}", reqDef.getCode());

    try {
      // 从数据库的 body_template 中提取变量值
      if (reqDef.getBodyTemplate() != null) {
        logger.info("从 body_template 中提取变量: {}", reqDef.getBodyTemplate());

        // body_template 是 JSON 格式，直接包含了所有变量的实际值
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> templateMap =
            mapper.readValue(reqDef.getBodyTemplate(), new TypeReference<Map<String, Object>>() {});

        // 将模板中的值作为变量使用
        variables.putAll(templateMap);

        logger.info("从 body_template 提取到 {} 个变量", variables.size());

      } else if (StringUtils.hasText(reqDef.getRawBody())) {
        logger.info("从 raw_body 中提取变量模板: {}", reqDef.getRawBody());

        // 如果使用 raw_body，需要解析其中的 {{variable}} 占位符
        // 这里可以提供默认值或从其他配置中获取
        extractVariablesFromTemplate(reqDef.getRawBody(), variables);

      } else {
        logger.warn("请求定义中没有 body_template 或 raw_body，使用默认变量");
        // 提供一些默认变量
        variables.put("timestamp", System.currentTimeMillis());
        variables.put("uuid", UUID.randomUUID().toString());
      }

    } catch (Exception e) {
      logger.error("解析请求变量失败: {}", e.getMessage(), e);
      // 如果解析失败，提供默认变量
      variables.put("timestamp", System.currentTimeMillis());
      variables.put("uuid", UUID.randomUUID().toString());
    }

    logger.info("准备的变量数量: {}", variables.size());
    logger.info("变量详情: {}", variables);
    logger.info("=== 请求变量准备完成 ===");

    return variables;
  }

  /** 从模板字符串中提取变量占位符并提供默认值 */
  private void extractVariablesFromTemplate(String template, Map<String, Object> variables) {
    // 使用正则表达式找到所有 {{variable}} 占位符
    Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    Matcher matcher = pattern.matcher(template);

    while (matcher.find()) {
      String varName = matcher.group(1);

      // 根据变量名提供合适的默认值
      Object defaultValue = getDefaultValueForVariable(varName);
      variables.put(varName, defaultValue);

      logger.debug("提取变量: {} = {}", varName, defaultValue);
    }
  }

  /** 根据变量名提供默认值 */
  private Object getDefaultValueForVariable(String varName) {
    // 根据变量名的模式提供合适的默认值
    switch (varName.toLowerCase()) {
      case "accountid":
        return "4d2a46c7-71cb-4cf1-b5bb-b68406d9da6f";
      case "contactsid":
        return "34e24904-36d7-49b7-b69f-ad6f8a286901";
      case "tripid":
        return "D1345";
      case "seattype":
        return "2";
      case "date":
      case "handledate":
        return "2025-08-31";
      case "from":
        return "Shang Hai";
      case "to":
        return "Su Zhou";
      case "assurance":
        return "0";
      case "foodtype":
        return 2;
      case "stationname":
        return "suzhou";
      case "storename":
        return "Roman Holiday";
      case "foodname":
        return "Soup";
      case "foodprice":
        return 3.7;
      case "consigneename":
        return "11";
      case "consigneephone":
        return "22";
      case "consigneeweight":
        return 33;
      case "iswithin":
        return false;
      default:
        // 对于未知变量，根据名称模式猜测类型
        if (varName.toLowerCase().contains("id")) {
          return UUID.randomUUID().toString();
        } else if (varName.toLowerCase().contains("date")
            || varName.toLowerCase().contains("time")) {
          return "2025-08-31";
        } else if (varName.toLowerCase().contains("price")
            || varName.toLowerCase().contains("weight")) {
          return 0;
        } else {
          return "default_" + varName;
        }
    }
  }

  /** 等待数据收集 */
  private void waitForDataCollection(RequestPatternRequest request) throws InterruptedException {
    // 计算等待时间：确保有足够时间让 Envoy 捕获和写入数据
    int baseWaitSeconds = 60; // 基础等待时间
    int requestBasedWait = request.getRequestCount() * 2; // 每个请求额外等待2秒
    int serviceBasedWait = request.getServiceList().size() * 1; // 每个服务额外等待1秒

    int totalWaitSeconds = Math.min(60, baseWaitSeconds + requestBasedWait + serviceBasedWait);

    logger.info(
        "Waiting {} seconds for data collection (base: {}s, requests: {}s, services: {}s)...",
        totalWaitSeconds,
        baseWaitSeconds,
        requestBasedWait,
        serviceBasedWait);

    // 分段等待，每10秒输出一次进度
    int remainingSeconds = totalWaitSeconds;
    while (remainingSeconds > 0) {
      int waitChunk = Math.min(10, remainingSeconds);
      Thread.sleep(waitChunk * 1000L);
      remainingSeconds -= waitChunk;

      if (remainingSeconds > 0) {
        logger.info("Still waiting for data collection... {} seconds remaining", remainingSeconds);
      }
    }

    logger.info("Data collection wait period completed");
  }

  /** 清理相关的 ConfigMap */
  private void cleanupConfigMaps(List<String> serviceList, String namespace) {
    if (serviceList == null || serviceList.isEmpty()) {
      logger.warn("Service list is empty, skipping ConfigMap cleanup");
      return;
    }

    logger.info(
        "Starting ConfigMap cleanup for {} services in namespace {}",
        serviceList.size(),
        namespace);

    for (String serviceName : serviceList) {
      try {
        // 清理录制相关的 ConfigMap
        String recordingConfigMapName = serviceName + "-recording-config";
        k8sTapManager.deleteConfigMap(namespace, recordingConfigMapName);
        logger.info("Deleted ConfigMap: {}/{}", namespace, recordingConfigMapName);

        // 清理 Envoy 配置相关的 ConfigMap
        String envoyConfigMapName = serviceName + "-envoy-config";
        k8sTapManager.deleteConfigMap(namespace, envoyConfigMapName);
        logger.info("Deleted ConfigMap: {}/{}", namespace, envoyConfigMapName);

        // 清理其他可能的配置
        String tapConfigMapName = serviceName + "-tap-config";
        k8sTapManager.deleteConfigMap(namespace, tapConfigMapName);
        logger.info("Deleted ConfigMap: {}/{}", namespace, tapConfigMapName);

      } catch (Exception e) {
        logger.error(
            "Failed to cleanup ConfigMaps for service {}: {}", serviceName, e.getMessage(), e);
        // 继续清理其他服务的 ConfigMap
      }
    }

    logger.info("ConfigMap cleanup completed for namespace {}", namespace);
  }

  /** 收集录制数据 */
  private List<RecordedEntry> collectRecordedData(String recordingId) {
    try {
      // 获取录制的条目（数据收集由后台任务自动完成）
      return recordingService.getEntries(recordingId, 0, 1000); // 最多获取1000条

    } catch (Exception e) {
      logger.error("Failed to collect recorded data: {}", e.getMessage(), e);
      return new ArrayList<>();
    }
  }

  /** 停止录制 */
  private void stopRecording(String recordingId) {
    try {
      recordingService.stop(recordingId);
      logger.info("Recording {} stopped successfully", recordingId);
    } catch (Exception e) {
      logger.error("Failed to stop recording {}: {}", recordingId, e.getMessage(), e);
    }
  }

  /** 生成任务ID */
  private String generateTaskId() {
    return "task_"
        + System.currentTimeMillis()
        + "_"
        + UUID.randomUUID().toString().substring(0, 8);
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  private Path createRecordingDebugDir() throws IOException {
    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path dir = Paths.get("/tmp", "recording-debug-" + ts);
    Files.createDirectories(dir);
    logger.info("Recording debug directory: {}", dir.toAbsolutePath());
    return dir;
  }

  private void exportRawDataPerService(
      Path debugDir, List<String> services, List<String> recordingIds) throws IOException {
    for (String recordingId : recordingIds) {
      String serviceName = "unknown";
      try {
        RecordingState state = recordingStateService.loadState(recordingId);
        if (state != null && state.getServiceName() != null) {
          serviceName = state.getServiceName();
        }
      } catch (Exception e) {
        logger.warn("Failed to load state for {}: {}", recordingId, e.getMessage());
      }

      try {
        List<RecordedEntry> entries = recordingService.getEntries(recordingId, 0, 1000);
        Map<String, Object> meta = new HashMap<>();
        meta.put("recordingId", recordingId);
        meta.put("serviceName", serviceName);
        meta.put("totalEntries", entries != null ? entries.size() : 0);

        // 将 entries 转换为可序列化的 Map 列表，避免 LocalDateTime 模块依赖
        List<Map<String, Object>> serializableEntries = new ArrayList<>();
        if (entries != null) {
          for (RecordedEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordingId", e.getRecordingId());
            m.put("timestamp", e.getTimestamp() != null ? e.getTimestamp().toString() : null);
            m.put("namespace", e.getNamespace());
            m.put("serviceName", e.getServiceName());
            m.put("pod", e.getPod());
            m.put("path", e.getPath());
            m.put("method", e.getMethod());
            m.put("status", e.getStatus());
            m.put("xRequestId", e.getxRequestId());
            m.put("traceparent", e.getTraceparent());
            m.put("requestHeaders", e.getRequestHeaders());
            m.put("responseHeaders", e.getResponseHeaders());
            m.put("requestBody", e.getRequestBody());
            m.put("responseBody", e.getResponseBody());
            m.put("reqBytes", e.getReqBytes());
            m.put("respBytes", e.getRespBytes());
            m.put("requestTruncated", e.getRequestTruncated());
            m.put("responseTruncated", e.getResponseTruncated());
            serializableEntries.add(m);
          }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("meta", meta);
        payload.put("entries", serializableEntries);

        String fileName = serviceName + "-raw-data.json";
        Path out = debugDir.resolve(fileName);
        String json =
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        Files.write(out, json.getBytes(StandardCharsets.UTF_8));
        logger.info(
            "Exported raw recording data for service {} (recording {}): {}",
            serviceName,
            recordingId,
            out.toAbsolutePath());
      } catch (Exception e) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("recordingId", recordingId);
        meta.put("serviceName", serviceName);
        meta.put("error", e.getMessage());
        String fileName = serviceName + "-raw-data.error.json";
        Path out = debugDir.resolve(fileName);
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(meta);
        Files.write(out, json.getBytes(StandardCharsets.UTF_8));
        logger.warn(
            "Failed to export raw data for service {} (recording {}): {}. Error file: {}",
            serviceName,
            recordingId,
            e.getMessage(),
            out.toAbsolutePath());
      }
    }
  }

  /** 将分析得到的请求模式持久化 */
  @org.springframework.transaction.annotation.Transactional
  protected void persistPatterns(Long executionId, List<ServiceRequestPattern> patterns) {
    if (executionId == null) {
      throw new IllegalArgumentException("execution_id 不能为空");
    }
    if (patterns == null || patterns.isEmpty()) {
      logger.info("No patterns to persist for executionId={}", executionId);
      return;
    }
    // 将每个服务的每个请求模式展开为一行
    for (ServiceRequestPattern sp : patterns) {
      if (sp == null || sp.getRequestMode() == null) continue;
      String serviceName = sp.getServiceName();
      for (ServiceRequestPattern.RequestMode rm : sp.getRequestMode()) {
        try {
          String reqHeadersJson = toJsonOrNull(rm.getRequestHeaders());
          String respHeadersJson = toJsonOrNull(rm.getResponseHeaders());
          String reqBody = normalizeJsonStringOrNull(rm.getRequestBody());
          String respBody = normalizeJsonStringOrNull(rm.getResponseBody());

          com.chaosblade.svc.reqrspproxy.entity.RequestPattern entity =
              new com.chaosblade.svc.reqrspproxy.entity.RequestPattern(
                  executionId,
                  serviceName,
                  rm.getMethod(),
                  rm.getUrl(),
                  reqHeadersJson,
                  reqBody,
                  respHeadersJson,
                  respBody,
                  rm.getResponseStatus() != null ? rm.getResponseStatus() : 0);
          requestPatternRepository.save(entity);
        } catch (Exception ex) {
          logger.warn(
              "Persist single pattern failed for service {}: {}", serviceName, ex.getMessage());
          throw new RuntimeException("Persist request pattern failed", ex);
        }
      }
    }
  }

  private String toJsonOrNull(Map<String, String> map) {
    try {
      return map == null ? null : objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      logger.warn("Serialize map to JSON failed: {}", e.getMessage());
      return null;
    }
  }

  private String normalizeJsonStringOrNull(String s) {
    if (s == null || s.isEmpty()) return null;
    // 如果是看起来像 JSON 的字符串，尽量校验并返回原始值；否则返回 null，避免 MySQL JSON 类型报错
    String trimmed = s.trim();
    if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
        || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
      try {
        objectMapper.readTree(trimmed);
        return trimmed;
      } catch (Exception e) {
        logger.warn("Invalid JSON string, will store as null: {}", e.getMessage());
        return null;
      }
    }
    return null;
  }
}
