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
import com.chaosblade.svc.reqrspproxy.dto.FixtureUpsertRequest;
import com.chaosblade.svc.reqrspproxy.dto.FixtureUpsertResponse;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 将 FixtureUpsertRequest 转换为 Envoy 过滤拦截并应用到 K8s 的服务 */
@Service
public class FixtureInterceptionService {
  private static final Logger logger = LoggerFactory.getLogger(FixtureInterceptionService.class);

  @Autowired private K8sTapManager tapManager;
  @Autowired private FilterInterceptionConfigRenderer filterRenderer;
  @Autowired private RecordingStateService stateService;
  @Autowired private RecordingConfig recordingConfig;

  /** 处理 upsert，请生成并下发 Envoy 配置，不再写数据库 */
  public FixtureUpsertResponse upsert(FixtureUpsertRequest req, HttpServletRequest httpRequest) {
    String namespace = inferNamespace(req.getNamespace(), httpRequest);
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("必须提供namespace");
    }

    // 统一 serviceName：items 中允许多个服务，但目前按每个 serviceName 分开应用
    Map<String, List<FixtureUpsertRequest.FixtureItem>> byService = groupByService(req.getItems());
    LocalDateTime expiresAt =
        LocalDateTime.now().plusSeconds(Optional.ofNullable(req.getTtlSec()).orElse(600));

    for (Map.Entry<String, List<FixtureUpsertRequest.FixtureItem>> entry : byService.entrySet()) {
      String serviceName = entry.getKey();
      List<FixtureUpsertRequest.FixtureItem> items = entry.getValue();

      // 1. 读取应用端口
      int appPort = getApplicationPort(namespace, serviceName);

      // 2. 渲染 Envoy 过滤器配置（包含 direct_response + baggage 条件）
      String tapPrefix =
          String.format(
              "%s/fi-%s-%s-",
              recordingConfig.getEnvoy().getTapDir(), serviceName, System.currentTimeMillis());
      String envoyYaml = filterRenderer.render(appPort, items, true, tapPrefix);

      // 3. 应用到 K8s：ConfigMap + 注入/更新 sidecar + redirect service
      String configMapName = String.format("envoy-fi-%s", serviceName);
      tapManager.applyOrUpdateConfigMap(namespace, configMapName, envoyYaml);
      tapManager.injectOrUpdateSidecar(namespace, serviceName, configMapName);
      tapManager.redirectServiceToEnvoy(
          namespace, serviceName, recordingConfig.getEnvoy().getPort());

      // 4. 保存/更新状态（Redis），便于 recordId 查询与 TTL 清理
      String sessionId = buildSessionId(req.getRecordId(), serviceName);
      RecordingState state = new RecordingState();
      state.setRecordingId(sessionId);
      state.setNamespace(namespace);
      state.setServiceName(serviceName);
      state.setRules(List.of());
      state.setInterceptionRules(List.of());
      state.setStatus(RecordingState.RecordingStatus.RECORDING);
      state.setStartedAt(LocalDateTime.now());
      state.setConfigMapName(configMapName);
      state.setDeploymentName(serviceName);
      state.setAppPortOriginal(appPort);
      stateService.saveState(state);

      // 5. TTL 自动清理：到期后恢复服务并删除配置
      Integer ttlSec = Optional.ofNullable(req.getTtlSec()).orElse(600);
      tapManager.scheduleAutoStop(
          sessionId,
          ttlSec,
          () -> {
            try {
              if (stateService.exists(sessionId)) {
                logger.info("Auto TTL cleanup for session {}", sessionId);
                deleteByRecordId(sessionId);
              }
            } catch (Exception e) {
              logger.warn("Auto cleanup failed for {}: {}", sessionId, e.getMessage());
            }
          });
    }

    return new FixtureUpsertResponse(req.getItems().size(), expiresAt);
  }

  public Map<String, Object> getStatusByRecordId(String recordId) {
    Map<String, Object> resp = new HashMap<>();
    resp.put("recordId", recordId);

    if (stateService.exists(recordId)) {
      RecordingState st = stateService.loadState(recordId);
      resp.put("exists", true);
      resp.put("namespace", st.getNamespace());
      resp.put("serviceName", st.getServiceName());
      resp.put("status", st.getStatus().name());
      resp.put("startedAt", st.getStartedAt());
      resp.put("configMap", st.getConfigMapName());
      return resp;
    }

    // 聚合：查找所有活跃会话，匹配以 recordId 为前缀的分组
    List<RecordingState> all = stateService.getAllActiveRecordingStates();
    List<Map<String, Object>> matches = new ArrayList<>();
    for (RecordingState st : all) {
      if (st.getRecordingId() != null && st.getRecordingId().startsWith(recordId)) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", st.getRecordingId());
        m.put("namespace", st.getNamespace());
        m.put("serviceName", st.getServiceName());
        m.put("status", st.getStatus().name());
        matches.add(m);
      }
    }
    resp.put("exists", !matches.isEmpty());
    resp.put("matches", matches);
    return resp;
  }

  public void deleteByRecordId(String recordId) {
    // 支持两种用法：
    // 1) 传入精确的 sessionId（如 1-ts-user-service）
    // 2) 传入组ID前缀（如 1），自动匹配所有以该前缀开头的 sessionId 并批量清理
    List<String> targets = new ArrayList<>();
    if (stateService.exists(recordId)) {
      targets.add(recordId);
    } else {
      List<RecordingState> all = stateService.getAllActiveRecordingStates();
      for (RecordingState st : all) {
        String rid = st.getRecordingId();
        if (rid != null && rid.startsWith(recordId)) {
          targets.add(rid);
        }
      }
      if (targets.isEmpty()) {
        logger.warn("No matching recording/session found for recordId prefix: {}", recordId);
        return;
      }
    }

    int success = 0;
    for (String id : targets) {
      try {
        if (!stateService.exists(id)) continue;
        RecordingState st = stateService.loadState(id);
        // 先取消可能的自动停止任务，避免在清理过程中被触发
        try {
          tapManager.cancelAutoStop(id);
        } catch (Exception ignore) {
        }

        tapManager.restoreServiceToOriginal(
            st.getNamespace(), st.getServiceName(), st.getAppPortOriginal());
        tapManager.removeSidecar(st.getNamespace(), st.getDeploymentName());
        tapManager.deleteConfigMap(st.getNamespace(), st.getConfigMapName());
        success++;
      } catch (Exception e) {
        logger.warn("Failed to cleanup session {}: {}", id, e.getMessage());
      } finally {
        try {
          stateService.deleteState(id);
        } catch (Exception ignore) {
        }
      }
    }
    logger.info(
        "Fixture deleteByRecordId completed for prefix={}, cleaned {} session(s)",
        recordId,
        success);
  }

  private String buildSessionId(String recordId, String serviceName) {
    String base = (recordId != null && !recordId.isBlank()) ? recordId.trim() : generateSessionId();
    // 保证同一组内不同服务不冲突
    return base + "-" + serviceName;
  }

  private String inferNamespace(String explicit, HttpServletRequest httpRequest) {
    return (explicit != null && !explicit.isBlank()) ? explicit.trim() : null;
  }

  private Map<String, List<FixtureUpsertRequest.FixtureItem>> groupByService(
      List<FixtureUpsertRequest.FixtureItem> items) {
    Map<String, List<FixtureUpsertRequest.FixtureItem>> m = new HashMap<>();
    for (FixtureUpsertRequest.FixtureItem it : items) {
      m.computeIfAbsent(it.getServiceName(), k -> new ArrayList<>()).add(it);
    }
    return m;
  }

  private int getApplicationPort(String namespace, String serviceName) {
    try {
      return tapManager.getServicePort(namespace, serviceName);
    } catch (Exception e) {
      logger.warn("Get service port failed, fallback 8080: {}", e.getMessage());
      return 8080;
    }
  }

  private String generateSessionId() {
    return "fi-"
        + System.currentTimeMillis()
        + "-"
        + Integer.toHexString((int) (Math.random() * 0x10000));
  }
}
