package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.ProxyInstance;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.repository.ProxyInstanceRepository;
import com.chaosblade.svc.reqrspproxy.repository.ProxySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 拦截服务 — 通过 feature flag 切换新旧实现。
 *
 * proxy.engine=new  → proxy-agent（Selector Swap + Control API）
 * proxy.engine=legacy → Envoy sidecar（原有逻辑）
 *
 * 核心逻辑：
 * 1. 检查目标服务是否已在录制模式
 * 2. 如果已在录制，基于现有配置添加拦截规则
 * 3. 如果未在录制，启动新的混合模式
 */
@Service
public class InterceptionService {

    private static final Logger logger = LoggerFactory.getLogger(InterceptionService.class);

    @Value("${proxy.engine:new}")
    private String proxyEngine;

    // ── 新引擎（proxy-agent）依赖 ──
    @Autowired
    private K8sProxyManager proxyManager;

    @Autowired
    private ProxyControlClient proxyControl;

    @Autowired
    private ProxySnapshotRepository snapshotRepo;

    @Autowired
    private ProxyInstanceRepository proxyInstanceRepo;

    // ── 旧引擎（Envoy sidecar）依赖 ──
    @Autowired private RecordingStateService stateService;
    @Autowired private HybridConfigRenderer hybridRenderer;
    @Autowired private PureInterceptionConfigRenderer pureInterceptionRenderer;
    @Autowired private K8sTapManager tapManager;
    @Autowired private RecordingConfig recordingConfig;

    private boolean isNewEngine() {
        return "new".equalsIgnoreCase(proxyEngine);
    }

    /**
     * 智能添加拦截规则
     *
     * @param request 拦截请求
     * @return 拦截响应
     */
    public InterceptionResponse addInterception(AddInterceptionRequest request) {
        if (isNewEngine()) {
            return addInterceptionWithProxyAgent(request);
        } else {
            return addInterceptionWithEnvoy(request);
        }
    }

    /**
     * 移除拦截规则
     */
    public InterceptionResponse removeInterception(String sessionId, List<InterceptionRule> rulesToRemove) {
        if (isNewEngine()) {
            return removeInterceptionWithProxyAgent(sessionId, rulesToRemove);
        } else {
            return removeInterceptionWithEnvoy(sessionId, rulesToRemove);
        }
    }

    /**
     * 停止拦截
     */
    public InterceptionResponse stopInterception(String sessionId) {
        if (isNewEngine()) {
            return stopInterceptionWithProxyAgent(sessionId);
        } else {
            return stopInterceptionWithEnvoy(sessionId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  新引擎: proxy-agent
    // ═══════════════════════════════════════════════════════════════════════

    private InterceptionResponse addInterceptionWithProxyAgent(AddInterceptionRequest request) {
        String namespace = request.getNamespace();
        String serviceName = request.getServiceName();
        logger.info("[proxy-agent] Adding interception for service {}/{}", namespace, serviceName);

        try {
            // 1. 检查是否已有运行中的 proxy-agent 实例
            List<ProxyInstance> existing = proxyInstanceRepo.findByNamespaceAndTargetService(namespace, serviceName);
            ProxyInstance runningInstance = null;
            for (ProxyInstance inst : existing) {
                if (inst.getStatus() == ProxyInstance.Status.RUNNING) {
                    runningInstance = inst;
                    break;
                }
            }

            String sessionId;

            if (runningInstance != null) {
                // 2a. proxy 已经在运行：切换到 intercept 模式并添加规则
                sessionId = runningInstance.getRecordingId();
                logger.info("[proxy-agent] Proxy already running for {}/{}, sessionId={}", namespace, serviceName, sessionId);

                proxyControl.setMode(runningInstance.getProxyPodIp(), runningInstance.getControlPort(), "intercept");

                for (InterceptionRule rule : request.getInterceptionRules()) {
                    MockResponse mock = rule.getMockResponse();
                    proxyControl.addRule(
                            runningInstance.getProxyPodIp(),
                            runningInstance.getControlPort(),
                            rule.getPath(),
                            rule.getMethod(),
                            mock.getStatusCode(),
                            mock.getBody());
                }
            } else {
                // 2b. 没有运行中的 proxy：部署新的 proxy-agent
                sessionId = generateInterceptionId();
                logger.info("[proxy-agent] No running proxy for {}/{}, deploying new proxy, sessionId={}", namespace, serviceName, sessionId);

                int servicePort = proxyManager.getServicePort(namespace, serviceName);

                // 创建影子 Service + 部署 proxy-agent
                proxyManager.createShadowService(namespace, serviceName);
                ProxyInstance instance = proxyManager.deployProxy(sessionId, namespace, serviceName, servicePort);

                // Selector Swap 劫持
                proxyManager.hijackService(sessionId, namespace, serviceName);

                // 等待 kube-proxy 传播
                Thread.sleep(5000);

                // 设置 intercept 模式并添加规则
                proxyControl.setMode(instance.getProxyPodIp(), instance.getControlPort(), "intercept");

                for (InterceptionRule rule : request.getInterceptionRules()) {
                    MockResponse mock = rule.getMockResponse();
                    proxyControl.addRule(
                            instance.getProxyPodIp(),
                            instance.getControlPort(),
                            rule.getPath(),
                            rule.getMethod(),
                            mock.getStatusCode(),
                            mock.getBody());
                }
            }

            // 3. 保存状态（复用 RecordingState 存储拦截信息）
            RecordingState state;
            try {
                state = stateService.loadState(sessionId);
            } catch (Exception e) {
                state = new RecordingState();
                state.setRecordingId(sessionId);
                state.setNamespace(namespace);
                state.setServiceName(serviceName);
            }
            List<InterceptionRule> allRules = new ArrayList<>(state.getInterceptionRules());
            allRules.addAll(request.getInterceptionRules());
            state.setInterceptionRules(allRules);
            state.setStatus(RecordingState.RecordingStatus.RECORDING);
            state.setStartedAt(LocalDateTime.now());
            stateService.saveState(state);

            logger.info("[proxy-agent] Interception added: sessionId={}, totalRules={}", sessionId, allRules.size());
            return new InterceptionResponse(
                    sessionId, "ACTIVE",
                    "Added " + request.getInterceptionRules().size() + " interception rules (proxy-agent)",
                    0, allRules.size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to add interception: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add interception: " + e.getMessage(), e);
        }
    }

    private InterceptionResponse removeInterceptionWithProxyAgent(String sessionId, List<InterceptionRule> rulesToRemove) {
        logger.info("[proxy-agent] Removing interception rules from session: {}", sessionId);

        try {
            ProxyInstance instance = proxyManager.getProxyInstance(sessionId);
            if (instance == null || instance.getProxyPodIp() == null) {
                throw new RuntimeException("No running proxy instance for session: " + sessionId);
            }

            // 对每个要移除的规则，调用 proxyControl.removeRule
            // 注意：proxy-agent 使用 path+method 作为 ruleId
            for (InterceptionRule rule : rulesToRemove) {
                String ruleId = rule.getPath() + ":" + rule.getMethod();
                proxyControl.removeRule(instance.getProxyPodIp(), instance.getControlPort(), ruleId);
            }

            // 更新状态
            RecordingState state = stateService.loadState(sessionId);
            List<InterceptionRule> currentRules = new ArrayList<>(state.getInterceptionRules());
            currentRules.removeAll(rulesToRemove);
            state.setInterceptionRules(currentRules);
            stateService.saveState(state);

            if (currentRules.isEmpty()) {
                return stopInterceptionWithProxyAgent(sessionId);
            }

            return new InterceptionResponse(
                    sessionId, state.getStatus().name(),
                    "Removed " + rulesToRemove.size() + " interception rules (proxy-agent)",
                    0, currentRules.size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to remove interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove interception rules: " + e.getMessage(), e);
        }
    }

    private InterceptionResponse stopInterceptionWithProxyAgent(String sessionId) {
        logger.info("[proxy-agent] Stopping interception session: {}", sessionId);

        try {
            // 1. 恢复 Service selector
            try {
                proxyManager.restoreService(sessionId);
            } catch (Exception e) {
                logger.warn("[proxy-agent] Failed to restore service for {}: {}", sessionId, e.getMessage());
            }

            // 2. 等待 EndpointSlice 恢复
            Thread.sleep(5000);

            // 3. 删除 proxy-agent Deployment
            try {
                proxyManager.destroyProxy(sessionId);
            } catch (Exception e) {
                logger.warn("[proxy-agent] Failed to destroy proxy for {}: {}", sessionId, e.getMessage());
            }

            // 4. 更新状态
            try {
                RecordingState state = stateService.loadState(sessionId);
                state.setStatus(RecordingState.RecordingStatus.STOPPED);
                state.setStoppedAt(LocalDateTime.now());
                stateService.saveState(state);
            } catch (Exception e) {
                logger.warn("[proxy-agent] Failed to update state for {}: {}", sessionId, e.getMessage());
            }

            return new InterceptionResponse(
                    sessionId, "STOPPED",
                    "Interception session stopped successfully (proxy-agent)",
                    0, 0);

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to stop interception: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop interception: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  旧引擎: Envoy sidecar（原有逻辑不变）
    // ═══════════════════════════════════════════════════════════════════════

    private InterceptionResponse addInterceptionWithEnvoy(AddInterceptionRequest request) {
        logger.info("Adding interception for service {}/{}", request.getNamespace(), request.getServiceName());

        try {
            // 1. 检查目标服务是否已在录制模式
            Optional<RecordingState> existingRecording = findActiveRecordingForService(
                request.getNamespace(), request.getServiceName());

            if (existingRecording.isPresent()) {
                // 2a. 基于现有录制添加拦截规则
                return addInterceptionToExistingRecording(existingRecording.get(), request.getInterceptionRules());
            } else {
                // 2b. 启动新的混合模式（仅拦截，无录制）
                return startInterceptionOnlyMode(request);
            }

        } catch (Exception e) {
            logger.error("Failed to add interception: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add interception: " + e.getMessage(), e);
        }
    }

    /**
     * 查找服务的活跃录制状态
     */
    private Optional<RecordingState> findActiveRecordingForService(String namespace, String serviceName) {
        logger.debug("Searching for active recording for service {}/{}", namespace, serviceName);

        try {
            List<RecordingState> activeRecordings = stateService.getAllActiveRecordingStates();

            return activeRecordings.stream()
                    .filter(state -> namespace.equals(state.getNamespace()) &&
                                   serviceName.equals(state.getServiceName()))
                    .findFirst();

        } catch (Exception e) {
            logger.error("Failed to search for active recordings: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 基于现有录制添加拦截规则
     */
    private InterceptionResponse addInterceptionToExistingRecording(RecordingState existingState,
                                                                   List<InterceptionRule> newRules) {
        logger.info("Adding interception rules to existing recording: {}", existingState.getRecordingId());

        try {
            // 1. 合并拦截规则
            List<InterceptionRule> allInterceptionRules = new ArrayList<>(existingState.getInterceptionRules());
            allInterceptionRules.addAll(newRules);

            // 2. 确保新的拦截路径也被录制
            List<RecordingRule> allRecordingRules = mergeRecordingRules(
                existingState.getRules(), allInterceptionRules);

            // 3. 重新生成混合配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                existingState.getAppPortOriginal(),
                allRecordingRules,
                allInterceptionRules);

            // 4. 更新 ConfigMap
            tapManager.applyOrUpdateConfigMap(existingState.getNamespace(),
                    existingState.getConfigMapName(), envoyYaml);

            // 5. 触发滚动更新以应用新的拦截配置
            tapManager.triggerRollingUpdateForConfigChange(existingState.getNamespace(),
                    existingState.getDeploymentName());

            // 6. 更新状态
            existingState.setInterceptionRules(allInterceptionRules);
            existingState.setRules(allRecordingRules);
            stateService.saveState(existingState);

            logger.info("Successfully added {} interception rules to recording {}",
                       newRules.size(), existingState.getRecordingId());

            return new InterceptionResponse(
                existingState.getRecordingId(),
                "ACTIVE",
                "Added " + newRules.size() + " interception rules to existing recording",
                allRecordingRules.size(),
                allInterceptionRules.size()
            );

        } catch (Exception e) {
            logger.error("Failed to add interception to existing recording: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add interception to existing recording: " + e.getMessage(), e);
        }
    }

    /**
     * 启动仅拦截模式
     */
    private InterceptionResponse startInterceptionOnlyMode(AddInterceptionRequest request) {
        String mode = request.isEnableRecording() ? "interception-with-recording" : "pure-interception";
        logger.info("Starting {} mode for service {}/{}",
                   mode, request.getNamespace(), request.getServiceName());

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
                envoyYaml = hybridRenderer.renderHybridConfig(appPort, recordingRules, request.getInterceptionRules());
            } else {
                // 纯拦截模式：不包含录制功能
                recordingRules = java.util.Collections.emptyList(); // 空的录制规则
                envoyYaml = pureInterceptionRenderer.renderPureInterceptionConfig(appPort, request.getInterceptionRules());
            }

            // 5. 创建 ConfigMap
            String configMapName = "envoy-intercept-" + sessionId.toLowerCase();
            tapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyYaml);

            // 6. 注入 Envoy sidecar
            String deploymentName = request.getServiceName();
            tapManager.injectOrUpdateSidecar(request.getNamespace(), deploymentName, configMapName);

            // 7. 重定向 Service
            tapManager.redirectServiceToEnvoy(request.getNamespace(), request.getServiceName(),
                    recordingConfig.getEnvoy().getPort());

            // 8. 保存状态
            RecordingState state = createInterceptionState(sessionId, request, configMapName, deploymentName, appPort, recordingRules);
            stateService.saveState(state);

            logger.info("{} mode started successfully: sessionId={}", mode, sessionId);

            String responseMode = request.isEnableRecording() ? "INTERCEPTION_WITH_RECORDING" : "PURE_INTERCEPTION";
            String message = request.isEnableRecording() ?
                "Started interception with recording mode with " + request.getInterceptionRules().size() + " rules" :
                "Started pure interception mode with " + request.getInterceptionRules().size() + " rules";

            return new InterceptionResponse(
                sessionId,
                "ACTIVE",
                message,
                recordingRules.size(),
                request.getInterceptionRules().size(),
                responseMode
            );

        } catch (Exception e) {
            logger.error("Failed to start interception-only mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start interception-only mode: " + e.getMessage(), e);
        }
    }

    private InterceptionResponse removeInterceptionWithEnvoy(String sessionId, List<InterceptionRule> rulesToRemove) {
        logger.info("Removing interception rules from session: {}", sessionId);

        try {
            RecordingState state = stateService.loadState(sessionId);

            // 移除指定的拦截规则
            List<InterceptionRule> currentRules = new ArrayList<>(state.getInterceptionRules());
            currentRules.removeAll(rulesToRemove);

            if (currentRules.isEmpty()) {
                // 如果没有拦截规则了，检查是否还有录制规则
                if (state.getRules().isEmpty() ||
                    state.getRules().stream().allMatch(rule -> isRuleForInterception(rule, state.getInterceptionRules()))) {
                    // 如果没有独立的录制规则，停止整个会话
                    return stopInterceptionWithEnvoy(sessionId);
                }
            }

            // 重新生成配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                state.getAppPortOriginal(), state.getRules(), currentRules);

            // 更新 ConfigMap
            tapManager.applyOrUpdateConfigMap(state.getNamespace(), state.getConfigMapName(), envoyYaml);

            // 触发滚动更新以应用移除拦截规则后的配置
            tapManager.triggerRollingUpdateForConfigChange(state.getNamespace(), state.getDeploymentName());

            // 更新状态
            state.setInterceptionRules(currentRules);
            stateService.saveState(state);

            return new InterceptionResponse(
                sessionId,
                state.getStatus().name(),
                "Removed " + rulesToRemove.size() + " interception rules",
                state.getRules().size(),
                currentRules.size()
            );

        } catch (Exception e) {
            logger.error("Failed to remove interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove interception rules: " + e.getMessage(), e);
        }
    }

    private InterceptionResponse stopInterceptionWithEnvoy(String sessionId) {
        logger.info("Stopping interception session: {}", sessionId);

        try {
            RecordingState state = stateService.loadState(sessionId);

            // 恢复原始配置
            tapManager.restoreServiceToOriginal(state.getNamespace(), state.getServiceName(),
                    state.getAppPortOriginal());

            // 移除 Envoy sidecar
            tapManager.removeSidecar(state.getNamespace(), state.getDeploymentName());

            // 删除 ConfigMap
            tapManager.deleteConfigMap(state.getNamespace(), state.getConfigMapName());

            // 更新状态
            state.setStatus(RecordingState.RecordingStatus.STOPPED);
            state.setStoppedAt(LocalDateTime.now());
            stateService.saveState(state);

            return new InterceptionResponse(
                sessionId,
                "STOPPED",
                "Interception session stopped successfully",
                0, 0
            );

        } catch (Exception e) {
            logger.error("Failed to stop interception: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop interception: " + e.getMessage(), e);
        }
    }

    // === 辅助方法 ===

    private List<RecordingRule> mergeRecordingRules(List<RecordingRule> existingRules,
                                                   List<InterceptionRule> interceptionRules) {
        List<RecordingRule> merged = new ArrayList<>(existingRules);

        for (InterceptionRule interceptionRule : interceptionRules) {
            RecordingRule recordingRule = new RecordingRule(
                interceptionRule.getPath(), interceptionRule.getMethod());

            boolean exists = merged.stream().anyMatch(rule ->
                rule.getPath().equals(recordingRule.getPath()) &&
                rule.getMethod().equals(recordingRule.getMethod()));

            if (!exists) {
                merged.add(recordingRule);
            }
        }

        return merged;
    }

    private List<RecordingRule> createRecordingRulesForInterception(List<InterceptionRule> interceptionRules) {
        return interceptionRules.stream()
                .map(rule -> new RecordingRule(rule.getPath(), rule.getMethod()))
                .collect(ArrayList::new, (list, rule) -> {
                    if (list.stream().noneMatch(existing ->
                        existing.getPath().equals(rule.getPath()) &&
                        existing.getMethod().equals(rule.getMethod()))) {
                        list.add(rule);
                    }
                }, ArrayList::addAll);
    }

    private boolean isRuleForInterception(RecordingRule recordingRule, List<InterceptionRule> interceptionRules) {
        return interceptionRules.stream().anyMatch(interceptionRule ->
            interceptionRule.getPath().equals(recordingRule.getPath()) &&
            interceptionRule.getMethod().equals(recordingRule.getMethod()));
    }

    private RecordingState createInterceptionState(String sessionId, AddInterceptionRequest request,
                                                  String configMapName, String deploymentName, int appPort,
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
        return "intercept-" + System.currentTimeMillis() + "-" +
               Integer.toHexString((int)(Math.random() * 0x10000));
    }
}
