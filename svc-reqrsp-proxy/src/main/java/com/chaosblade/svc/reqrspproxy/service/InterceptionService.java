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
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 拦截服务 - 智能检测现有录制状态并添加拦截功能
 *
 * <p>核心逻辑： 1. 检查目标服务是否已在录制模式 2. 如果已在录制，基于现有配置添加拦截规则 3. 如果未在录制，启动新的混合模式
 */
@Service
public class InterceptionService {

  private static final Logger logger = LoggerFactory.getLogger(InterceptionService.class);

  @Autowired private RecordingStateService stateService;
  @Autowired private HybridConfigRenderer hybridRenderer;
  @Autowired private PureInterceptionConfigRenderer pureInterceptionRenderer;
  @Autowired private K8sTapManager tapManager;
  @Autowired private RecordingConfig recordingConfig;

  /**
   * 智能添加拦截规则
   *
   * @param request 拦截请求
   * @return 拦截响应
   */
  public InterceptionResponse addInterception(AddInterceptionRequest request) {
    logger.info(
        "Adding interception for service {}/{}", request.getNamespace(), request.getServiceName());

    try {
      // 1. 检查目标服务是否已在录制模式
      Optional<RecordingState> existingRecording =
          findActiveRecordingForService(request.getNamespace(), request.getServiceName());

      if (existingRecording.isPresent()) {
        // 2a. 基于现有录制添加拦截规则
        return addInterceptionToExistingRecording(
            existingRecording.get(), request.getInterceptionRules());
      } else {
        // 2b. 启动新的混合模式（仅拦截，无录制）
        return startInterceptionOnlyMode(request);
      }

    } catch (Exception e) {
      logger.error("Failed to add interception: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to add interception: " + e.getMessage(), e);
    }
  }

  /** 查找服务的活跃录制状态 */
  private Optional<RecordingState> findActiveRecordingForService(
      String namespace, String serviceName) {
    logger.debug("Searching for active recording for service {}/{}", namespace, serviceName);

    try {
      List<RecordingState> activeRecordings = stateService.getAllActiveRecordingStates();

      return activeRecordings.stream()
          .filter(
              state ->
                  namespace.equals(state.getNamespace())
                      && serviceName.equals(state.getServiceName()))
          .findFirst();

    } catch (Exception e) {
      logger.error("Failed to search for active recordings: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }

  /** 基于现有录制添加拦截规则 */
  private InterceptionResponse addInterceptionToExistingRecording(
      RecordingState existingState, List<InterceptionRule> newRules) {
    logger.info(
        "Adding interception rules to existing recording: {}", existingState.getRecordingId());

    try {
      // 1. 合并拦截规则
      List<InterceptionRule> allInterceptionRules =
          new ArrayList<>(existingState.getInterceptionRules());
      allInterceptionRules.addAll(newRules);

      // 2. 确保新的拦截路径也被录制
      List<RecordingRule> allRecordingRules =
          mergeRecordingRules(existingState.getRules(), allInterceptionRules);

      // 3. 重新生成混合配置
      String envoyYaml =
          hybridRenderer.renderHybridConfig(
              existingState.getAppPortOriginal(), allRecordingRules, allInterceptionRules);

      // 4. 更新 ConfigMap
      tapManager.applyOrUpdateConfigMap(
          existingState.getNamespace(), existingState.getConfigMapName(), envoyYaml);

      // 5. 触发滚动更新以应用新的拦截配置
      tapManager.triggerRollingUpdateForConfigChange(
          existingState.getNamespace(), existingState.getDeploymentName());

      // 6. 更新状态
      existingState.setInterceptionRules(allInterceptionRules);
      existingState.setRules(allRecordingRules);
      stateService.saveState(existingState);

      logger.info(
          "Successfully added {} interception rules to recording {}",
          newRules.size(),
          existingState.getRecordingId());

      return new InterceptionResponse(
          existingState.getRecordingId(),
          "ACTIVE",
          "Added " + newRules.size() + " interception rules to existing recording",
          allRecordingRules.size(),
          allInterceptionRules.size());

    } catch (Exception e) {
      logger.error("Failed to add interception to existing recording: {}", e.getMessage(), e);
      throw new RuntimeException(
          "Failed to add interception to existing recording: " + e.getMessage(), e);
    }
  }

  /** 启动仅拦截模式 */
  private InterceptionResponse startInterceptionOnlyMode(AddInterceptionRequest request) {
    String mode = request.isEnableRecording() ? "interception-with-recording" : "pure-interception";
    logger.info(
        "Starting {} mode for service {}/{}",
        mode,
        request.getNamespace(),
        request.getServiceName());

    try {
      // 1. 生成会话ID
      String sessionId = generateInterceptionId();

      // 2. 获取应用端口
      int appPort = getApplicationPort(request.getNamespace(), request.getServiceName());

      // 3. 生成配置
      String envoyYaml;
      List<RecordingRule> recordingRules;

      if (request.isEnableRecording()) {
        // 拦截 + 录制模式：为拦截规则创建对应的录制规则
        recordingRules = createRecordingRulesForInterception(request.getInterceptionRules());
        envoyYaml =
            hybridRenderer.renderHybridConfig(
                appPort, recordingRules, request.getInterceptionRules());
      } else {
        // 纯拦截模式：不包含录制功能
        recordingRules = List.of(); // 空的录制规则
        envoyYaml =
            pureInterceptionRenderer.renderPureInterceptionConfig(
                appPort, request.getInterceptionRules());
      }

      // 5. 创建 ConfigMap
      String configMapName = "envoy-intercept-" + sessionId.toLowerCase();
      tapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyYaml);

      // 6. 注入 Envoy sidecar
      String deploymentName = request.getServiceName();
      tapManager.injectOrUpdateSidecar(request.getNamespace(), deploymentName, configMapName);

      // 7. 重定向 Service
      tapManager.redirectServiceToEnvoy(
          request.getNamespace(), request.getServiceName(), recordingConfig.getEnvoy().getPort());

      // 8. 保存状态
      RecordingState state =
          createInterceptionState(
              sessionId, request, configMapName, deploymentName, appPort, recordingRules);
      stateService.saveState(state);

      logger.info("{} mode started successfully: sessionId={}", mode, sessionId);

      String responseMode =
          request.isEnableRecording() ? "INTERCEPTION_WITH_RECORDING" : "PURE_INTERCEPTION";
      String message =
          request.isEnableRecording()
              ? "Started interception with recording mode with "
                  + request.getInterceptionRules().size()
                  + " rules"
              : "Started pure interception mode with "
                  + request.getInterceptionRules().size()
                  + " rules";

      return new InterceptionResponse(
          sessionId,
          "ACTIVE",
          message,
          recordingRules.size(),
          request.getInterceptionRules().size(),
          responseMode);

    } catch (Exception e) {
      logger.error("Failed to start interception-only mode: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to start interception-only mode: " + e.getMessage(), e);
    }
  }

  /** 移除拦截规则 */
  public InterceptionResponse removeInterception(
      String sessionId, List<InterceptionRule> rulesToRemove) {
    logger.info("Removing interception rules from session: {}", sessionId);

    try {
      RecordingState state = stateService.loadState(sessionId);

      // 移除指定的拦截规则
      List<InterceptionRule> currentRules = new ArrayList<>(state.getInterceptionRules());
      currentRules.removeAll(rulesToRemove);

      if (currentRules.isEmpty()) {
        // 如果没有拦截规则了，检查是否还有录制规则
        if (state.getRules().isEmpty()
            || state.getRules().stream()
                .allMatch(rule -> isRuleForInterception(rule, state.getInterceptionRules()))) {
          // 如果没有独立的录制规则，停止整个会话
          return stopInterception(sessionId);
        }
      }

      // 重新生成配置
      String envoyYaml =
          hybridRenderer.renderHybridConfig(
              state.getAppPortOriginal(), state.getRules(), currentRules);

      // 更新 ConfigMap
      tapManager.applyOrUpdateConfigMap(state.getNamespace(), state.getConfigMapName(), envoyYaml);

      // 触发滚动更新以应用移除拦截规则后的配置
      tapManager.triggerRollingUpdateForConfigChange(
          state.getNamespace(), state.getDeploymentName());

      // 更新状态
      state.setInterceptionRules(currentRules);
      stateService.saveState(state);

      return new InterceptionResponse(
          sessionId,
          state.getStatus().name(),
          "Removed " + rulesToRemove.size() + " interception rules",
          state.getRules().size(),
          currentRules.size());

    } catch (Exception e) {
      logger.error("Failed to remove interception rules: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to remove interception rules: " + e.getMessage(), e);
    }
  }

  /** 停止拦截 */
  public InterceptionResponse stopInterception(String sessionId) {
    logger.info("Stopping interception session: {}", sessionId);

    try {
      RecordingState state = stateService.loadState(sessionId);

      // 恢复原始配置
      tapManager.restoreServiceToOriginal(
          state.getNamespace(), state.getServiceName(), state.getAppPortOriginal());

      // 移除 Envoy sidecar
      tapManager.removeSidecar(state.getNamespace(), state.getDeploymentName());

      // 删除 ConfigMap
      tapManager.deleteConfigMap(state.getNamespace(), state.getConfigMapName());

      // 更新状态
      state.setStatus(RecordingState.RecordingStatus.STOPPED);
      state.setStoppedAt(LocalDateTime.now());
      stateService.saveState(state);

      return new InterceptionResponse(
          sessionId, "STOPPED", "Interception session stopped successfully", 0, 0);

    } catch (Exception e) {
      logger.error("Failed to stop interception: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to stop interception: " + e.getMessage(), e);
    }
  }

  // === 辅助方法 ===

  private List<RecordingRule> mergeRecordingRules(
      List<RecordingRule> existingRules, List<InterceptionRule> interceptionRules) {
    List<RecordingRule> merged = new ArrayList<>(existingRules);

    for (InterceptionRule interceptionRule : interceptionRules) {
      RecordingRule recordingRule =
          new RecordingRule(interceptionRule.getPath(), interceptionRule.getMethod());

      boolean exists =
          merged.stream()
              .anyMatch(
                  rule ->
                      rule.getPath().equals(recordingRule.getPath())
                          && rule.getMethod().equals(recordingRule.getMethod()));

      if (!exists) {
        merged.add(recordingRule);
      }
    }

    return merged;
  }

  private List<RecordingRule> createRecordingRulesForInterception(
      List<InterceptionRule> interceptionRules) {
    return interceptionRules.stream()
        .map(rule -> new RecordingRule(rule.getPath(), rule.getMethod()))
        .collect(
            ArrayList::new,
            (list, rule) -> {
              if (list.stream()
                  .noneMatch(
                      existing ->
                          existing.getPath().equals(rule.getPath())
                              && existing.getMethod().equals(rule.getMethod()))) {
                list.add(rule);
              }
            },
            ArrayList::addAll);
  }

  private boolean isRuleForInterception(
      RecordingRule recordingRule, List<InterceptionRule> interceptionRules) {
    return interceptionRules.stream()
        .anyMatch(
            interceptionRule ->
                interceptionRule.getPath().equals(recordingRule.getPath())
                    && interceptionRule.getMethod().equals(recordingRule.getMethod()));
  }

  private RecordingState createInterceptionState(
      String sessionId,
      AddInterceptionRequest request,
      String configMapName,
      String deploymentName,
      int appPort,
      List<RecordingRule> recordingRules) {
    RecordingState state = new RecordingState();
    state.setRecordingId(sessionId);
    state.setNamespace(request.getNamespace());
    state.setServiceName(request.getServiceName());
    state.setRules(recordingRules); // 使用传入的录制规则（可能为空）
    state.setInterceptionRules(request.getInterceptionRules());
    state.setStatus(RecordingState.RecordingStatus.RECORDING);
    state.setStartedAt(LocalDateTime.now());
    state.setConfigMapName(configMapName);
    state.setDeploymentName(deploymentName);
    state.setAppPortOriginal(appPort);

    return state;
  }

  private int getApplicationPort(String namespace, String serviceName) {
    try {
      return tapManager.getServicePort(namespace, serviceName);
    } catch (Exception e) {
      logger.warn("Failed to get service port, using default: {}", e.getMessage());
      return 8080;
    }
  }

  private String generateInterceptionId() {
    return "intercept-"
        + System.currentTimeMillis()
        + "-"
        + Integer.toHexString((int) (Math.random() * 0x10000));
  }
}
