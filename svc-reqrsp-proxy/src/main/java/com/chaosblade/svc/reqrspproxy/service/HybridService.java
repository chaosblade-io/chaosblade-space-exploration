package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.ProxyInstance;
import com.chaosblade.svc.reqrspproxy.entity.ProxySnapshot;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.repository.ProxyInstanceRepository;
import com.chaosblade.svc.reqrspproxy.repository.ProxySnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 混合服务 — 通过 feature flag 切换新旧实现。
 *
 * proxy.engine=new  → proxy-agent（Selector Swap + Control API）
 * proxy.engine=legacy → Envoy sidecar（原有逻辑）
 *
 * 核心功能：
 * 1. 管理录制和拦截的混合配置
 * 2. 确保拦截的请求也能被正确录制
 * 3. 支持动态添加/移除拦截规则
 */
@Service
public class HybridService {

    private static final Logger logger = LoggerFactory.getLogger(HybridService.class);

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

    @Autowired
    private ObjectMapper objectMapper;

    // ── 旧引擎（Envoy sidecar）依赖 ──
    @Autowired private K8sTapManager tapManager;
    @Autowired private HybridConfigRenderer hybridRenderer;
    @Autowired private RecordingStateService stateService;
    @Autowired private RecordingConfig recordingConfig;

    private boolean isNewEngine() {
        return "new".equalsIgnoreCase(proxyEngine);
    }

    /**
     * 启动混合模式（录制 + 拦截）
     */
    public HybridResponse startHybrid(StartHybridRequest request) {
        if (isNewEngine()) {
            return startHybridWithProxyAgent(request);
        } else {
            return startHybridWithEnvoy(request);
        }
    }

    /**
     * 停止混合模式
     */
    public HybridResponse stopHybrid(String hybridId) {
        if (isNewEngine()) {
            return stopHybridWithProxyAgent(hybridId);
        } else {
            return stopHybridWithEnvoy(hybridId);
        }
    }

    /**
     * 添加拦截规则到现有的混合会话
     */
    public HybridResponse addInterceptionRules(String hybridId, List<InterceptionRule> newRules) {
        if (isNewEngine()) {
            return addInterceptionRulesWithProxyAgent(hybridId, newRules);
        } else {
            return addInterceptionRulesWithEnvoy(hybridId, newRules);
        }
    }

    /**
     * 移除拦截规则
     */
    public HybridResponse removeInterceptionRules(String hybridId, List<InterceptionRule> rulesToRemove) {
        if (isNewEngine()) {
            return removeInterceptionRulesWithProxyAgent(hybridId, rulesToRemove);
        } else {
            return removeInterceptionRulesWithEnvoy(hybridId, rulesToRemove);
        }
    }

    /**
     * 更新拦截规则（替换所有现有规则）
     */
    public HybridResponse updateInterceptionRules(String hybridId, List<InterceptionRule> newRules) {
        if (isNewEngine()) {
            return updateInterceptionRulesWithProxyAgent(hybridId, newRules);
        } else {
            return updateInterceptionRulesWithEnvoy(hybridId, newRules);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  新引擎: proxy-agent
    // ═══════════════════════════════════════════════════════════════════════

    private HybridResponse startHybridWithProxyAgent(StartHybridRequest request) {
        String hybridId = generateHybridId();
        logger.info("[proxy-agent] Starting hybrid mode: hybridId={}, namespace={}, serviceName={}",
                hybridId, request.getNamespace(), request.getServiceName());

        try {
            String namespace = request.getNamespace();
            String serviceName = request.getServiceName();

            // 1. 获取目标服务信息
            int servicePort = proxyManager.getServicePort(namespace, serviceName);

            // 2. 创建影子 Service + 部署 proxy-agent
            proxyManager.createShadowService(namespace, serviceName);
            ProxyInstance instance = proxyManager.deployProxy(hybridId, namespace, serviceName, servicePort);

            // 3. Selector Swap 劫持
            proxyManager.hijackService(hybridId, namespace, serviceName);

            // 4. 等待 kube-proxy 传播
            Thread.sleep(5000);

            // 5. 设置 record 模式（混合模式先启动录制）
            proxyControl.setMode(instance.getProxyPodIp(), instance.getControlPort(), "record");

            // 6. 添加拦截规则
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

            // 7. 保存状态
            RecordingState state = createHybridState(hybridId, request, null, serviceName, servicePort);
            stateService.saveState(state);

            logger.info("[proxy-agent] Hybrid mode started: hybridId={}, proxy={}", hybridId, instance.getDeploymentName());
            return new HybridResponse(hybridId, "ACTIVE",
                    request.getRecordingRules().size(), request.getInterceptionRules().size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to start hybrid mode: {}", e.getMessage(), e);
            // 尽力恢复
            try { proxyManager.restoreService(hybridId); } catch (Exception ex) { /* ignore */ }
            try { proxyManager.destroyProxy(hybridId); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to start hybrid mode: " + e.getMessage(), e);
        }
    }

    private HybridResponse stopHybridWithProxyAgent(String hybridId) {
        logger.info("[proxy-agent] Stopping hybrid mode: hybridId={}", hybridId);

        try {
            // 1. 从 proxy-agent 拉取快照并持久化到 MySQL
            pullAndSaveSnapshots(hybridId);

            // 2. 恢复 Service selector
            proxyManager.restoreService(hybridId);

            // 3. 等待 EndpointSlice 恢复
            Thread.sleep(5000);

            // 4. 删除 proxy-agent Deployment
            proxyManager.destroyProxy(hybridId);

            // 5. 更新状态
            try {
                RecordingState state = stateService.loadState(hybridId);
                state.setStatus(RecordingState.RecordingStatus.STOPPED);
                state.setStoppedAt(LocalDateTime.now());
                stateService.saveState(state);
            } catch (Exception e) {
                logger.warn("[proxy-agent] Failed to update state for {}: {}", hybridId, e.getMessage());
            }

            logger.info("[proxy-agent] Hybrid mode stopped: hybridId={}", hybridId);
            return new HybridResponse(hybridId, "STOPPED", 0, 0);

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to stop hybrid mode: {}", e.getMessage(), e);
            // 尽力恢复
            try { proxyManager.restoreService(hybridId); } catch (Exception ex) { /* ignore */ }
            try { proxyManager.destroyProxy(hybridId); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to stop hybrid mode: " + e.getMessage(), e);
        }
    }

    private HybridResponse addInterceptionRulesWithProxyAgent(String hybridId, List<InterceptionRule> newRules) {
        logger.info("[proxy-agent] Adding interception rules to hybrid session: hybridId={}, newRules={}", hybridId, newRules.size());

        try {
            ProxyInstance instance = proxyManager.getProxyInstance(hybridId);
            if (instance == null || instance.getProxyPodIp() == null) {
                throw new RuntimeException("No running proxy instance for hybrid session: " + hybridId);
            }

            // 添加规则到 proxy-agent
            for (InterceptionRule rule : newRules) {
                MockResponse mock = rule.getMockResponse();
                proxyControl.addRule(
                        instance.getProxyPodIp(),
                        instance.getControlPort(),
                        rule.getPath(),
                        rule.getMethod(),
                        mock.getStatusCode(),
                        mock.getBody());
            }

            // 更新状态
            RecordingState state = stateService.loadState(hybridId);
            List<InterceptionRule> allInterceptionRules = new ArrayList<>(state.getInterceptionRules());
            allInterceptionRules.addAll(newRules);
            state.setInterceptionRules(allInterceptionRules);

            List<RecordingRule> allRecordingRules = mergeRecordingRules(state.getRules(), allInterceptionRules);
            state.setRules(allRecordingRules);
            stateService.saveState(state);

            logger.info("[proxy-agent] Interception rules added: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    allRecordingRules.size(), allInterceptionRules.size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to add interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add interception rules: " + e.getMessage(), e);
        }
    }

    private HybridResponse removeInterceptionRulesWithProxyAgent(String hybridId, List<InterceptionRule> rulesToRemove) {
        logger.info("[proxy-agent] Removing interception rules from hybrid session: hybridId={}, rulesToRemove={}", hybridId, rulesToRemove.size());

        try {
            ProxyInstance instance = proxyManager.getProxyInstance(hybridId);
            if (instance == null || instance.getProxyPodIp() == null) {
                throw new RuntimeException("No running proxy instance for hybrid session: " + hybridId);
            }

            // 从 proxy-agent 移除规则
            for (InterceptionRule rule : rulesToRemove) {
                String ruleId = rule.getPath() + ":" + rule.getMethod();
                proxyControl.removeRule(instance.getProxyPodIp(), instance.getControlPort(), ruleId);
            }

            // 更新状态
            RecordingState state = stateService.loadState(hybridId);
            List<InterceptionRule> currentRules = new ArrayList<>(state.getInterceptionRules());
            currentRules.removeAll(rulesToRemove);
            state.setInterceptionRules(currentRules);
            stateService.saveState(state);

            logger.info("[proxy-agent] Interception rules removed: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    state.getRules().size(), currentRules.size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to remove interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove interception rules: " + e.getMessage(), e);
        }
    }

    private HybridResponse updateInterceptionRulesWithProxyAgent(String hybridId, List<InterceptionRule> newRules) {
        logger.info("[proxy-agent] Updating interception rules for hybrid session: hybridId={}, newRules={}", hybridId, newRules.size());

        try {
            ProxyInstance instance = proxyManager.getProxyInstance(hybridId);
            if (instance == null || instance.getProxyPodIp() == null) {
                throw new RuntimeException("No running proxy instance for hybrid session: " + hybridId);
            }

            RecordingState state = stateService.loadState(hybridId);

            // 先移除旧规则
            for (InterceptionRule oldRule : state.getInterceptionRules()) {
                String ruleId = oldRule.getPath() + ":" + oldRule.getMethod();
                try {
                    proxyControl.removeRule(instance.getProxyPodIp(), instance.getControlPort(), ruleId);
                } catch (Exception e) {
                    logger.warn("[proxy-agent] Failed to remove old rule {}: {}", ruleId, e.getMessage());
                }
            }

            // 添加新规则
            for (InterceptionRule rule : newRules) {
                MockResponse mock = rule.getMockResponse();
                proxyControl.addRule(
                        instance.getProxyPodIp(),
                        instance.getControlPort(),
                        rule.getPath(),
                        rule.getMethod(),
                        mock.getStatusCode(),
                        mock.getBody());
            }

            // 更新状态
            List<RecordingRule> allRecordingRules = mergeRecordingRules(state.getRules(), newRules);
            state.setInterceptionRules(newRules);
            state.setRules(allRecordingRules);
            stateService.saveState(state);

            logger.info("[proxy-agent] Interception rules updated: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    allRecordingRules.size(), newRules.size());

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to update interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update interception rules: " + e.getMessage(), e);
        }
    }

    /**
     * 从 proxy-agent 拉取快照并存入 proxy_snapshot 表
     */
    @SuppressWarnings("unchecked")
    private void pullAndSaveSnapshots(String hybridId) {
        ProxyInstance instance = proxyManager.getProxyInstance(hybridId);
        if (instance == null || instance.getProxyPodIp() == null) {
            logger.warn("No proxy instance or pod IP for hybrid {}, skip snapshot pull", hybridId);
            return;
        }

        List<Map<String, Object>> snapshots = proxyControl.getSnapshots(
                instance.getProxyPodIp(), instance.getControlPort());
        logger.info("Pulled {} snapshots from proxy-agent for hybrid {}", snapshots.size(), hybridId);

        for (Map<String, Object> snap : snapshots) {
            try {
                ProxySnapshot entity = new ProxySnapshot();
                entity.setRecordingId(hybridId);

                Map<String, Object> sig = (Map<String, Object>) snap.get("signature");
                if (sig != null) {
                    entity.setSignatureHash((String) sig.get("hash"));
                    entity.setMethod((String) sig.get("method"));
                    entity.setPath((String) sig.get("path"));
                }

                String method = entity.getMethod();
                entity.setProtocol("GRPC".equals(method) ? "grpc" : "http");

                Map<String, Object> req = (Map<String, Object>) snap.get("request");
                if (req != null) {
                    entity.setRequestHeaders(objectMapper.writeValueAsString(req.get("headers")));
                    Object bodyObj = req.get("body");
                    if (bodyObj instanceof String) {
                        entity.setRequestBody(Base64.getDecoder().decode((String) bodyObj));
                    }
                }

                Map<String, Object> resp = (Map<String, Object>) snap.get("response");
                if (resp != null) {
                    entity.setResponseStatus((Integer) resp.get("status_code"));
                    entity.setResponseHeaders(objectMapper.writeValueAsString(resp.get("headers")));
                    Object bodyObj = resp.get("body");
                    if (bodyObj instanceof String) {
                        entity.setResponseBody(Base64.getDecoder().decode((String) bodyObj));
                    }
                    Object latency = resp.get("latency_ms");
                    if (latency instanceof Number) {
                        entity.setLatencyMs(((Number) latency).intValue());
                    }
                }

                snapshotRepo.save(entity);
            } catch (Exception e) {
                logger.warn("Failed to save snapshot for hybrid {}: {}", hybridId, e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  旧引擎: Envoy sidecar（原有逻辑不变）
    // ═══════════════════════════════════════════════════════════════════════

    private HybridResponse startHybridWithEnvoy(StartHybridRequest request) {
        logger.info("Starting hybrid mode: namespace={}, serviceName={}, recordingRules={}, interceptionRules={}",
                   request.getNamespace(), request.getServiceName(),
                   request.getRecordingRules().size(), request.getInterceptionRules().size());

        try {
            // 1. 生成混合会话ID
            String hybridId = generateHybridId();

            // 2. 获取应用端口
            int appPort = getApplicationPort(request.getNamespace(), request.getServiceName());

            // 3. 合并录制规则（确保拦截的路径也被录制）
            List<RecordingRule> allRecordingRules = mergeRecordingRules(
                request.getRecordingRules(),
                request.getInterceptionRules());

            // 4. 生成混合 Envoy 配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                appPort,
                allRecordingRules,
                request.getInterceptionRules());

            // 5. 创建/更新 ConfigMap
            String configMapName = "envoy-hybrid-" + hybridId.toLowerCase();
            tapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyYaml);

            // 6. 注入/更新 Envoy sidecar
            String deploymentName = request.getServiceName();
            tapManager.injectOrUpdateSidecar(request.getNamespace(), deploymentName, configMapName);

            // 7. 重定向 Service 流量
            tapManager.redirectServiceToEnvoy(request.getNamespace(), request.getServiceName(),
                    recordingConfig.getEnvoy().getPort());

            // 8. 保存混合状态
            RecordingState state = createHybridState(hybridId, request, configMapName, deploymentName, appPort);
            stateService.saveState(state);

            logger.info("Hybrid mode started successfully: hybridId={}", hybridId);
            return new HybridResponse(hybridId, "ACTIVE",
                    allRecordingRules.size(), request.getInterceptionRules().size());

        } catch (Exception e) {
            logger.error("Failed to start hybrid mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start hybrid mode: " + e.getMessage(), e);
        }
    }

    private HybridResponse addInterceptionRulesWithEnvoy(String hybridId, List<InterceptionRule> newRules) {
        logger.info("Adding interception rules to hybrid session: hybridId={}, newRules={}",
                   hybridId, newRules.size());

        try {
            // 1. 获取当前状态
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            // 2. 合并拦截规则
            List<InterceptionRule> allInterceptionRules = new ArrayList<>(state.getInterceptionRules());
            allInterceptionRules.addAll(newRules);

            // 3. 更新录制规则（确保新的拦截路径也被录制）
            List<RecordingRule> allRecordingRules = mergeRecordingRules(
                state.getRules(), allInterceptionRules);

            // 4. 重新生成配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                state.getAppPortOriginal(),
                allRecordingRules,
                allInterceptionRules);

            // 5. 更新 ConfigMap
            tapManager.applyOrUpdateConfigMap(state.getNamespace(), state.getConfigMapName(), envoyYaml);

            // 6. 更新状态
            state.setInterceptionRules(allInterceptionRules);
            state.setRules(allRecordingRules);
            stateService.saveState(state);

            logger.info("Interception rules added successfully: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    allRecordingRules.size(), allInterceptionRules.size());

        } catch (Exception e) {
            logger.error("Failed to add interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add interception rules: " + e.getMessage(), e);
        }
    }

    private HybridResponse stopHybridWithEnvoy(String hybridId) {
        logger.info("Stopping hybrid mode: hybridId={}", hybridId);

        try {
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            // 1. 恢复原始 Service 配置
            tapManager.restoreServiceToOriginal(state.getNamespace(), state.getServiceName(),
                    state.getAppPortOriginal());

            // 2. 移除 Envoy sidecar
            tapManager.removeSidecar(state.getNamespace(), state.getDeploymentName());

            // 3. 删除 ConfigMap
            tapManager.deleteConfigMap(state.getNamespace(), state.getConfigMapName());

            // 4. 更新状态
            state.setStatus(RecordingState.RecordingStatus.STOPPED);
            state.setStoppedAt(LocalDateTime.now());
            stateService.saveState(state);

            logger.info("Hybrid mode stopped successfully: hybridId={}", hybridId);
            return new HybridResponse(hybridId, "STOPPED", 0, 0);

        } catch (Exception e) {
            logger.error("Failed to stop hybrid mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop hybrid mode: " + e.getMessage(), e);
        }
    }

    private HybridResponse removeInterceptionRulesWithEnvoy(String hybridId, List<InterceptionRule> rulesToRemove) {
        logger.info("Removing interception rules from hybrid session: hybridId={}, rulesToRemove={}",
                   hybridId, rulesToRemove.size());

        try {
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            // 移除指定的拦截规则
            List<InterceptionRule> currentRules = new ArrayList<>(state.getInterceptionRules());
            currentRules.removeAll(rulesToRemove);

            // 重新生成配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                state.getAppPortOriginal(),
                state.getRules(),
                currentRules);

            // 更新 ConfigMap
            tapManager.applyOrUpdateConfigMap(state.getNamespace(), state.getConfigMapName(), envoyYaml);

            // 更新状态
            state.setInterceptionRules(currentRules);
            stateService.saveState(state);

            logger.info("Interception rules removed successfully: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    state.getRules().size(), currentRules.size());

        } catch (Exception e) {
            logger.error("Failed to remove interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove interception rules: " + e.getMessage(), e);
        }
    }

    private HybridResponse updateInterceptionRulesWithEnvoy(String hybridId, List<InterceptionRule> newRules) {
        logger.info("Updating interception rules for hybrid session: hybridId={}, newRules={}",
                   hybridId, newRules.size());

        try {
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            // 更新录制规则（确保新的拦截路径也被录制）
            List<RecordingRule> allRecordingRules = mergeRecordingRules(
                state.getRules(), newRules);

            // 重新生成配置
            String envoyYaml = hybridRenderer.renderHybridConfig(
                state.getAppPortOriginal(),
                allRecordingRules,
                newRules);

            // 更新 ConfigMap
            tapManager.applyOrUpdateConfigMap(state.getNamespace(), state.getConfigMapName(), envoyYaml);

            // 更新状态
            state.setInterceptionRules(newRules);
            state.setRules(allRecordingRules);
            stateService.saveState(state);

            logger.info("Interception rules updated successfully: hybridId={}", hybridId);
            return new HybridResponse(hybridId, state.getStatus().name(),
                    allRecordingRules.size(), newRules.size());

        } catch (Exception e) {
            logger.error("Failed to update interception rules: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update interception rules: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  公共方法（不受 feature flag 影响）
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 获取混合模式状态
     */
    public HybridStatusResponse getHybridStatus(String hybridId) {
        logger.info("Getting hybrid status: hybridId={}", hybridId);

        try {
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            HybridStatusResponse response = new HybridStatusResponse();
            response.setHybridId(hybridId);
            response.setNamespace(state.getNamespace());
            response.setServiceName(state.getServiceName());
            response.setStatus(state.getStatus().name());
            response.setRecordingRules(state.getRules());
            response.setInterceptionRules(state.getInterceptionRules());
            response.setDurationSec(state.getDurationSec());
            response.setStartedAt(state.getStartedAt());
            response.setStoppedAt(state.getStoppedAt());
            response.setExpiresAt(state.getExpiresAt());
            response.setErrorMessage(state.getErrorMessage());

            // 新引擎使用 snapshot 表计数，旧引擎使用默认值
            if (isNewEngine()) {
                long snapshotCount = snapshotRepo.findByRecordingId(hybridId).size();
                response.setRecordedCount((int) snapshotCount);
            } else {
                response.setRecordedCount(0);
            }
            response.setInterceptedCount(0);

            return response;

        } catch (Exception e) {
            logger.error("Failed to get hybrid status: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get hybrid status: " + e.getMessage(), e);
        }
    }

    /**
     * 获取录制数据
     */
    public List<RecordedEntry> getRecordedData(String hybridId, int page, int size) {
        logger.info("Getting recorded data: hybridId={}, page={}, size={}", hybridId, page, size);

        try {
            RecordingState state = stateService.loadState(hybridId);
            if (state == null) {
                throw new IllegalArgumentException("Hybrid session not found: " + hybridId);
            }

            // TODO: 实现从 Redis 或其他存储获取录制数据的逻辑
            // 这里应该复用现有的 TapCollector 或类似的数据获取逻辑
            return new ArrayList<>();

        } catch (Exception e) {
            logger.error("Failed to get recorded data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get recorded data: " + e.getMessage(), e);
        }
    }

    // ─── 辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 合并录制规则，确保拦截的路径也被录制
     */
    private List<RecordingRule> mergeRecordingRules(List<RecordingRule> recordingRules,
                                                   List<InterceptionRule> interceptionRules) {
        List<RecordingRule> merged = new ArrayList<>(recordingRules);

        // 为每个拦截规则添加对应的录制规则（如果不存在）
        for (InterceptionRule interceptionRule : interceptionRules) {
            RecordingRule recordingRule = new RecordingRule(
                interceptionRule.getPath(),
                interceptionRule.getMethod());

            // 检查是否已存在相同的录制规则
            boolean exists = merged.stream().anyMatch(rule ->
                rule.getPath().equals(recordingRule.getPath()) &&
                rule.getMethod().equals(recordingRule.getMethod()));

            if (!exists) {
                merged.add(recordingRule);
                logger.debug("Added recording rule for interception: {}", recordingRule);
            }
        }

        return merged;
    }

    /**
     * 创建混合状态对象
     */
    private RecordingState createHybridState(String hybridId, StartHybridRequest request,
                                           String configMapName, String deploymentName, int appPort) {
        RecordingState state = new RecordingState();
        state.setRecordingId(hybridId);
        state.setNamespace(request.getNamespace());
        state.setServiceName(request.getServiceName());
        state.setRules(request.getRecordingRules());
        state.setInterceptionRules(request.getInterceptionRules());
        state.setStatus(RecordingState.RecordingStatus.RECORDING);
        state.setStartedAt(LocalDateTime.now());
        state.setConfigMapName(configMapName);
        state.setDeploymentName(deploymentName);
        state.setAppPortOriginal(appPort);

        if (request.getDurationSec() != null && request.getDurationSec() > 0) {
            state.setDurationSec(request.getDurationSec());
            state.setExpiresAt(LocalDateTime.now().plusSeconds(request.getDurationSec()));
        }

        return state;
    }

    /**
     * 获取应用端口
     */
    private int getApplicationPort(String namespace, String serviceName) {
        // 复用现有的端口获取逻辑
        try {
            return tapManager.getServicePort(namespace, serviceName);
        } catch (Exception e) {
            logger.warn("Failed to get service port, using default: {}", e.getMessage());
            return 8080; // 默认端口
        }
    }

    /**
     * 生成混合会话ID
     */
    private String generateHybridId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "hybrid_" + timestamp + "_" + uuid;
    }
}
