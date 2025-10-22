/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.config.RecordingSettings;
import com.chaosblade.svc.reqrspproxy.dto.RecordedEntry;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Tap 数据采集器 */
@Component
public class TapCollector {

  private static final Logger logger = LoggerFactory.getLogger(TapCollector.class);

  private final ExecutorService tapExecutor;
  private final RecordingSettings settings;
  private static final int MAX_BODY_SIZE = 10240; // 10KB

  public TapCollector(RecordingSettings settings) {
    this.settings = settings;
    this.tapExecutor = Executors.newFixedThreadPool(Math.max(1, settings.getTapConcurrent()));
    logger.info(
        "TapCollector executor initialized with concurrency {}", settings.getTapConcurrent());
  }

  @Autowired private KubernetesClient k8s;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private StringRedisTemplate redis;

  @Autowired private RecordingConfig recordingConfig;

  @Autowired private RecordingStateService stateService;

  private boolean isRecordingActive(String recordingId) {
    try {
      RecordingState st = stateService.loadState(recordingId);
      return st != null && st.getStatus() == RecordingState.RecordingStatus.RECORDING;
    } catch (Exception e) {
      logger.debug("Recording {} considered inactive: {}", recordingId, e.getMessage());
      return false;
    }
  }

  /** 从指定录制中收集数据 */
  public CompletableFuture<Integer> collectOnce(String recordingId, RecordingState state) {

    return CompletableFuture.supplyAsync(
        () -> {
          try {
            if (!isRecordingActive(recordingId)) {
              logger.debug("Recording {} is not active, skip collectOnce", recordingId);
              return 0;
            }

            List<Pod> pods = getPodsByService(state.getNamespace(), state.getServiceName());
            int totalCollected = 0;

            for (Pod pod : pods) {
              if (!isRecordingActive(recordingId)) {
                logger.debug("Recording {} became inactive, abort remaining pods", recordingId);
                break;
              }
              int collected = collectFromPod(recordingId, state, pod);
              totalCollected += collected;
            }

            return totalCollected;

          } catch (Exception e) {
            logger.error(
                "Failed to collect data for recording {}: {}", recordingId, e.getMessage(), e);
            throw new RuntimeException("Collection failed: " + e.getMessage(), e);
          }
        });
  }

  /** 从单个 Pod 收集数据 */
  private int collectFromPod(String recordingId, RecordingState state, Pod pod) {
    String podName = pod.getMetadata().getName();
    logger.debug("Collecting from pod {} for recording {}", podName, recordingId);

    try {
      if (!isRecordingActive(recordingId)) {
        logger.debug("Recording {} inactive, skip pod {}", recordingId, podName);
        return 0;
      }

      if (!isEnvoyContainerReady(pod)) {
        logger.debug("Envoy container not ready in pod {}, skipping collection", podName);
        return 0;
      }

      List<String> tapFiles = listTapFiles(state.getNamespace(), podName);
      logger.debug("Found {} tap files in pod {}", tapFiles.size(), podName);

      java.util.concurrent.atomic.AtomicInteger collected =
          new java.util.concurrent.atomic.AtomicInteger(0);
      tapFiles.parallelStream()
          .forEach(
              fileName -> {
                try {
                  if (!isRecordingActive(recordingId)) {
                    return; // 已停止，取消处理
                  }
                  if (isFileProcessed(recordingId, fileName)) {
                    logger.debug("File {} already processed, skipping", fileName);
                    return;
                  }
                  String content =
                      readTapFileWithRetry(
                          recordingId,
                          state.getNamespace(),
                          podName,
                          fileName,
                          settings.getTapReadRetryMaxRetries(),
                          settings.getTapReadRetrySleepMillis());
                  if (content != null && !content.trim().isEmpty()) {
                    RecordedEntry entry = parseTapContent(recordingId, state, pod, content);
                    if (entry != null) {
                      storeEntry(recordingId, entry);
                      markFileProcessed(recordingId, fileName);
                      collected.incrementAndGet();
                    }
                  }
                } catch (Exception ex) {
                  logger.warn(
                      "Failed to process tap file {} in pod {}: {}",
                      fileName,
                      podName,
                      ex.getMessage());
                }
              });

      logger.debug("Collected {} entries from pod {}", collected.get(), podName);
      return collected.get();

    } catch (Exception e) {
      logger.error("Failed to collect from pod {}: {}", podName, e.getMessage(), e);
      return 0;
    }
  }

  /** 以重试方式读取 tap 文件，避免读取到空文件 */
  private String readTapFileWithRetry(
      String recordingId,
      String namespace,
      String podName,
      String fileName,
      int maxRetries,
      long sleepMillis) {
    for (int i = 0; i < maxRetries; i++) {
      if (!isRecordingActive(recordingId)) {
        logger.debug("Recording {} inactive during read, cancel {}", recordingId, fileName);
        return null;
      }
      String content = readTapFile(namespace, podName, fileName);
      if (content != null && !content.trim().isEmpty()) {
        return content;
      }
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (!isRecordingActive(recordingId)) {
      return null;
    }
    return readTapFile(namespace, podName, fileName);
  }

  /** 导出原始 tap JSON 文件到本地目录（调试用） */
  public int exportRawTapFiles(String recordingId, RecordingState state, Path outDir) {
    java.util.concurrent.atomic.AtomicInteger total =
        new java.util.concurrent.atomic.AtomicInteger(0);
    try {
      Files.createDirectories(outDir);
      List<Pod> pods = getPodsByService(state.getNamespace(), state.getServiceName());
      pods.parallelStream()
          .forEach(
              pod -> {
                String podName = pod.getMetadata().getName();
                if (!isEnvoyContainerReady(pod)) {
                  logger.debug("Envoy container not ready in pod {}, skip raw export", podName);
                  return;
                }
                List<String> tapFiles = listTapFiles(state.getNamespace(), podName);
                tapFiles.parallelStream()
                    .forEach(
                        filePath -> {
                          try {
                            if (!isRecordingActive(recordingId)) return;
                            String content =
                                readTapFileWithRetry(
                                    recordingId, state.getNamespace(), podName, filePath, 5, 300);
                            if (content == null || content.trim().isEmpty()) return;
                            String base = filePath;
                            int idx = base.lastIndexOf('/');
                            if (idx >= 0) base = base.substring(idx + 1);
                            String fileName =
                                String.format("%s__%s__%s", state.getServiceName(), podName, base);
                            Path out = outDir.resolve(fileName);
                            Files.write(out, content.getBytes(StandardCharsets.UTF_8));
                            logger.info("Exported raw tap file: {}", out.toAbsolutePath());
                            total.incrementAndGet();
                          } catch (Exception ex) {
                            logger.warn(
                                "Failed exporting raw tap from pod {} file {}: {}",
                                podName,
                                filePath,
                                ex.getMessage());
                          }
                        });
              });
    } catch (Exception e) {
      logger.warn("Raw tap export error for recording {}: {}", recordingId, e.getMessage());
    }
    return total.get();
  }

  /** 列出 tap 文件 */
  private List<String> listTapFiles(String namespace, String podName) {
    try {
      String command =
          String.format(
              "ls -1 %s/*.json 2>/dev/null || true", recordingConfig.getEnvoy().getTapDir());
      String output =
          execCommandWithRetry(
              namespace,
              podName,
              "envoy",
              command,
              settings.getTapReadRetryMaxRetries(),
              settings.getTapReadRetrySleepMillis());

      if (output == null || output.trim().isEmpty()) {
        return Collections.emptyList();
      }

      return Arrays.asList(output.trim().split("\n"));

    } catch (Exception e) {
      // 如果是容器不存在的错误，记录为 debug 级别
      if (e.getMessage() != null && e.getMessage().contains("container envoy not found")) {
        logger.debug("Envoy container not found in pod {}, skipping tap file listing", podName);
      } else {
        logger.error("Failed to list tap files in pod {}: {}", podName, e.getMessage(), e);
      }
      return Collections.emptyList();
    }
  }

  /** 读取 tap 文件内容（带重试） */
  private String readTapFile(String namespace, String podName, String fileName) {
    try {
      String command = String.format("cat %s", fileName);
      return execCommandWithRetry(
          namespace,
          podName,
          "envoy",
          command,
          settings.getTapReadRetryMaxRetries(),
          settings.getTapReadRetrySleepMillis());

    } catch (Exception e) {
      logger.error(
          "Failed to read tap file {} from pod {}: {}", fileName, podName, e.getMessage(), e);
      return null;
    }
  }

  /** 执行命令（带重试，处理 WebSocket 握手失败等瞬时错误） */
  private String execCommand(
      String namespace, String podName, String containerName, String command) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    try (ExecWatch watch =
        k8s.pods()
            .inNamespace(namespace)
            .withName(podName)
            .inContainer(containerName)
            .writingOutput(out)
            .writingError(err)
            .exec("sh", "-c", command)) {

      // 等待命令完成，使用简单的睡眠等待
      Thread.sleep(5000); // 等待5秒让命令执行完成

      String output = out.toString();
      String error = err.toString();

      if (!error.isEmpty()) {
        logger.warn("Command stderr: {}", error);
      }

      return output;

    } catch (Exception e) {
      // 特殊处理容器不存在的情况
      if (e.getMessage() != null
          && e.getMessage().contains("container " + containerName + " not found")) {
        logger.debug(
            "Container {} not found in pod {}, this is expected during startup/shutdown",
            containerName,
            podName);
        return "";
      }

      logger.error("Failed to execute command in pod {}: {}", podName, e.getMessage(), e);
      throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
    }
  }

  private String execCommandWithRetry(
      String namespace,
      String podName,
      String containerName,
      String command,
      int maxRetries,
      long sleepMillis)
      throws InterruptedException {
    for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
      try {
        return execCommand(namespace, podName, containerName, command);
      } catch (RuntimeException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        boolean handshake =
            msg.contains("WebSocketHandshakeException") || msg.contains("Exec Failure");
        boolean notFound = msg.contains("container " + containerName + " not found");
        if (attempt >= maxRetries || (!handshake && !notFound)) {
          throw ex;
        }
        long backoff = sleepMillis * attempt;
        logger.warn(
            "Exec failed in pod {} (attempt {}/{}), will retry after {}ms: {}",
            podName,
            attempt,
            maxRetries,
            backoff,
            msg);
        Thread.sleep(backoff);
      }
    }
    return "";
  }

  /** 解析 tap 内容 */
  private RecordedEntry parseTapContent(
      String recordingId, RecordingState state, Pod pod, String content) {
    try {
      JsonNode root = objectMapper.readTree(content);

      // 尝试获取 http_buffered_trace 或 http_streamed_trace
      JsonNode trace = root.path("http_buffered_trace");
      if (trace.isMissingNode()) {
        trace = root.path("http_streamed_trace");
      }

      if (trace.isMissingNode()) {
        logger.warn("No HTTP trace found in tap content");
        return null;
      }

      JsonNode request = trace.path("request");
      JsonNode response = trace.path("response");

      RecordedEntry entry = new RecordedEntry();
      entry.setRecordingId(recordingId);
      entry.setTimestamp(LocalDateTime.now());
      entry.setNamespace(state.getNamespace());
      entry.setServiceName(state.getServiceName());
      entry.setPod(pod.getMetadata().getName());

      // 解析请求信息
      parseRequestInfo(entry, request);

      // 解析响应信息
      parseResponseInfo(entry, response);

      return entry;

    } catch (Exception e) {
      logger.error("Failed to parse tap content: {}", e.getMessage(), e);
      return null;
    }
  }

  /** 解析请求信息 */
  private void parseRequestInfo(RecordedEntry entry, JsonNode request) {
    // 解析 headers
    Map<String, String> headers = parseHeaders(request.path("headers"));
    entry.setRequestHeaders(headers);

    // 提取关键信息
    entry.setPath(headers.get(":path"));
    entry.setMethod(headers.get(":method"));
    entry.setxRequestId(headers.get("x-request-id"));
    entry.setTraceparent(headers.get("traceparent"));

    // 解析 body
    JsonNode body = request.path("body");
    if (!body.isMissingNode()) {
      String bodyStr = body.path("as_string").asText();
      if (bodyStr.length() > MAX_BODY_SIZE) {
        entry.setRequestBody(bodyStr.substring(0, MAX_BODY_SIZE));
        entry.setRequestTruncated(true);
      } else {
        entry.setRequestBody(bodyStr);
        entry.setRequestTruncated(false);
      }
      entry.setReqBytes((long) bodyStr.length());
    }
  }

  /** 解析响应信息 */
  private void parseResponseInfo(RecordedEntry entry, JsonNode response) {
    // 解析 headers
    Map<String, String> headers = parseHeaders(response.path("headers"));
    entry.setResponseHeaders(headers);

    // 提取状态码
    String status = headers.get(":status");
    if (status != null) {
      try {
        entry.setStatus(Integer.parseInt(status));
      } catch (NumberFormatException e) {
        logger.warn("Invalid status code: {}", status);
      }
    }

    // 解析 body
    JsonNode body = response.path("body");
    if (!body.isMissingNode()) {
      String bodyStr = body.path("as_string").asText();
      if (bodyStr.length() > MAX_BODY_SIZE) {
        entry.setResponseBody(bodyStr.substring(0, MAX_BODY_SIZE));
        entry.setResponseTruncated(true);
      } else {
        entry.setResponseBody(bodyStr);
        entry.setResponseTruncated(false);
      }
      entry.setRespBytes((long) bodyStr.length());
    }
  }

  /** 解析 headers */
  private Map<String, String> parseHeaders(JsonNode headersNode) {
    Map<String, String> headers = new HashMap<>();

    if (headersNode.isMissingNode()) {
      return headers;
    }

    // 尝试两种格式：对象格式和数组格式
    JsonNode headersArray = headersNode.path("headers");
    if (!headersArray.isMissingNode() && headersArray.isArray()) {
      // 对象格式：headers.headers[]
      for (JsonNode header : headersArray) {
        String key = header.path("key").asText();
        String value = header.path("value").asText();
        if (!key.isEmpty()) {
          headers.put(key, value);
        }
      }
    } else if (headersNode.isArray()) {
      // 数组格式：headers[]
      for (JsonNode header : headersNode) {
        String key = header.path("key").asText();
        String value = header.path("value").asText();
        if (!key.isEmpty()) {
          headers.put(key, value);
        }
      }
    }

    return headers;
  }

  /** 存储条目到 Redis */
  private void storeEntry(String recordingId, RecordedEntry entry) {
    try {
      String json = objectMapper.writeValueAsString(entry);
      String key = "rec:" + recordingId + ":entries";
      redis.opsForList().rightPush(key, json);

      // 设置过期时间（7天）
      redis.expire(key, Duration.ofDays(7));

    } catch (Exception e) {
      logger.error("Failed to store entry: {}", e.getMessage(), e);
    }
  }

  /** 检查文件是否已处理 */
  private boolean isFileProcessed(String recordingId, String fileName) {
    String key = "rec:" + recordingId + ":processed";
    return redis.opsForSet().isMember(key, fileName);
  }

  /** 标记文件已处理 */
  private void markFileProcessed(String recordingId, String fileName) {
    String key = "rec:" + recordingId + ":processed";
    redis.opsForSet().add(key, fileName);
    redis.expire(key, Duration.ofDays(7));
  }

  /** 从 Redis 读取条目 */
  public List<RecordedEntry> readFromRedis(String recordingId, int offset, int limit) {
    try {
      String key = "rec:" + recordingId + ":entries";
      List<String> jsonList = redis.opsForList().range(key, offset, offset + limit - 1);

      if (jsonList == null) {
        return Collections.emptyList();
      }

      List<RecordedEntry> entries = new ArrayList<>();
      for (String json : jsonList) {
        try {
          RecordedEntry entry = objectMapper.readValue(json, RecordedEntry.class);
          entries.add(entry);
        } catch (Exception e) {
          logger.error("Failed to parse entry JSON: {}", e.getMessage(), e);
        }
      }

      return entries;

    } catch (Exception e) {
      logger.error("Failed to read entries from Redis: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /** 获取条目总数 */
  public long getEntryCount(String recordingId) {
    try {
      String key = "rec:" + recordingId + ":entries";
      Long count = redis.opsForList().size(key);
      return count != null ? count : 0;
    } catch (Exception e) {
      logger.error("Failed to get entry count: {}", e.getMessage(), e);
      return 0;
    }
  }

  /** 检查 Pod 中的 Envoy 容器是否就绪 */
  private boolean isEnvoyContainerReady(Pod pod) {
    if (pod == null || pod.getStatus() == null) {
      return false;
    }

    // 检查 Pod 是否处于 Running 状态
    if (!"Running".equals(pod.getStatus().getPhase())) {
      logger.debug(
          "Pod {} is not in Running phase: {}",
          pod.getMetadata().getName(),
          pod.getStatus().getPhase());
      return false;
    }

    // 检查是否有 Envoy 容器
    boolean hasEnvoyContainer =
        pod.getSpec().getContainers().stream()
            .anyMatch(container -> "envoy".equals(container.getName()));

    if (!hasEnvoyContainer) {
      logger.debug("Pod {} does not have envoy container", pod.getMetadata().getName());
      return false;
    }

    // 检查 Envoy 容器是否就绪
    if (pod.getStatus().getContainerStatuses() != null) {
      return pod.getStatus().getContainerStatuses().stream()
          .filter(status -> "envoy".equals(status.getName()))
          .findFirst()
          .map(
              status ->
                  Boolean.TRUE.equals(status.getReady())
                      && Boolean.TRUE.equals(status.getStarted()))
          .orElse(false);
    }

    return false;
  }

  /** 根据服务名获取 Pod 列表 */
  private List<Pod> getPodsByService(String namespace, String serviceName) {
    try {
      return k8s.pods().inNamespace(namespace).withLabel("app", serviceName).list().getItems();
    } catch (Exception e) {
      logger.error("Failed to get pods for service {}: {}", serviceName, e.getMessage(), e);
      return Collections.emptyList();
    }
  }
}
