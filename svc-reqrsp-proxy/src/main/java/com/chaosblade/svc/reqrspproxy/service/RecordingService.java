package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.ProxyInstance;
import com.chaosblade.svc.reqrspproxy.entity.ProxySnapshot;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.repository.ProxySnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 录制服务 — 通过 feature flag 切换新旧实现。
 *
 * proxy.engine=new  → proxy-agent（Selector Swap + Control API）
 * proxy.engine=legacy → Envoy sidecar（原有逻辑）
 */
@Component
public class RecordingService {

    private static final Logger logger = LoggerFactory.getLogger(RecordingService.class);

    @Value("${proxy.engine:new}")
    private String proxyEngine;

    @Autowired
    private KubernetesClient k8s;

    @Autowired
    private RecordingConfig recordingConfig;

    @Autowired
    private RecordingStateService stateService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── 新引擎（proxy-agent）依赖 ──
    @Autowired
    private K8sProxyManager proxyManager;

    @Autowired
    private ProxyControlClient proxyControl;

    @Autowired
    private ProxySnapshotRepository snapshotRepo;

    // ── 旧引擎（Envoy sidecar）依赖 ──
    @Autowired
    private TemplateBasedTapConfigRenderer tapRenderer;

    @Autowired
    private K8sTapManager tapManager;

    @Autowired
    private TapCollector tapCollector;

    private boolean isNewEngine() {
        return "new".equalsIgnoreCase(proxyEngine);
    }

    /**
     * 开始录制
     */
    public RecordingResponse start(StartRecordingRequest request) {
        if (isNewEngine()) {
            return startWithProxyAgent(request);
        } else {
            return startWithEnvoy(request);
        }
    }

    /**
     * 停止录制
     */
    public RecordingResponse stop(String recordingId) {
        if (isNewEngine()) {
            return stopWithProxyAgent(recordingId);
        } else {
            return stopWithEnvoy(recordingId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  新引擎: proxy-agent
    // ═══════════════════════════════════════════════════════════════════════

    private RecordingResponse startWithProxyAgent(StartRecordingRequest request) {
        String recordingId = generateRecordingId();
        logger.info("[proxy-agent] Starting recording {} for {}/{}",
                recordingId, request.getNamespace(), request.getServiceName());

        try {
            // 1. 创建录制状态
            RecordingState state = new RecordingState(recordingId, request.getNamespace(), request.getServiceName());
            state.setRules(request.getRules());
            state.setDurationSec(request.getDurationSec());

            // 2. 获取目标服务的端口
            int servicePort = proxyManager.getServicePort(request.getNamespace(), request.getServiceName());
            state.setAppPortOriginal(servicePort);
            state.setDeploymentName(request.getServiceName());
            stateService.saveState(state);

            // 3. 创建影子 Service（{svc}-original），保留原始 selector 用于 proxy-agent 转发
            proxyManager.createShadowService(request.getNamespace(), request.getServiceName());

            // 4. 部署 proxy-agent（转发目标为 {svc}-original DNS）
            ProxyInstance instance = proxyManager.deployProxy(
                    recordingId, request.getNamespace(), request.getServiceName(), servicePort);

            // 5. Selector Swap 劫持
            proxyManager.hijackService(recordingId, request.getNamespace(), request.getServiceName());

            // 5. 等待 kube-proxy 传播（EndpointSlice Controller 更新）
            Thread.sleep(5000);

            // 6. 设置 proxy-agent 为 record 模式
            proxyControl.setMode(instance.getProxyPodIp(), instance.getControlPort(), "record");

            // 7. 更新状态
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.RECORDING);

            logger.info("[proxy-agent] Recording {} started, proxy={}, podIP={}",
                    recordingId, instance.getDeploymentName(), instance.getProxyPodIp());
            return new RecordingResponse(recordingId, "RUNNING", "Recording started (proxy-agent)");

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to start recording {}", recordingId, e);
            try { stateService.setError(recordingId, e.getMessage()); } catch (Exception ex) { /* ignore */ }
            // 尽力恢复
            try { proxyManager.restoreService(recordingId); } catch (Exception ex) { /* ignore */ }
            try { proxyManager.destroyProxy(recordingId); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to start recording: " + e.getMessage(), e);
        }
    }

    private RecordingResponse stopWithProxyAgent(String recordingId) {
        logger.info("[proxy-agent] Stopping recording {}", recordingId);

        try {
            RecordingState state = stateService.loadState(recordingId);
            if (state.getStatus() == RecordingState.RecordingStatus.STOPPED) {
                return new RecordingResponse(recordingId, "STOPPED", "Recording already stopped");
            }

            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPING);

            // 1. 从 proxy-agent 拉取快照并持久化到 MySQL
            pullAndSaveSnapshots(recordingId);

            // 2. 恢复 Service selector
            proxyManager.restoreService(recordingId);

            // 3. 等待 EndpointSlice 恢复
            Thread.sleep(5000);

            // 4. 删除 proxy-agent Deployment
            proxyManager.destroyProxy(recordingId);

            // 5. 更新状态
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPED);

            logger.info("[proxy-agent] Recording {} stopped successfully", recordingId);
            return new RecordingResponse(recordingId, "STOPPED", "Recording stopped (proxy-agent)");

        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to stop recording {}", recordingId, e);
            try { stateService.setError(recordingId, e.getMessage()); } catch (Exception ex) { /* ignore */ }
            // 尽力恢复（最重要：恢复 selector）
            try { proxyManager.restoreService(recordingId); } catch (Exception ex) { /* ignore */ }
            try { proxyManager.destroyProxy(recordingId); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to stop recording: " + e.getMessage(), e);
        }
    }

    /**
     * 停止录制但保持 proxy-agent 运行（不恢复 selector、不删除 Deployment）。
     * 用于 Stage 2→4 过渡：停止请求模式分析的录制，但 proxy-agent 继续为 Stage 4 服务。
     */
    public RecordingResponse stopRecordingOnly(String recordingId) {
        logger.info("[proxy-agent] Stop recording only (keep proxy alive): {}", recordingId);
        try {
            pullAndSaveSnapshots(recordingId);
            // 设为 passthrough 模式（暂停录制，但保持 proxy-agent 运行和 Selector Swap 生效）
            ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
            if (instance != null) {
                proxyControl.setMode(instance.getProxyPodIp(), instance.getControlPort(), "passthrough");
            }
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPED);
            return new RecordingResponse(recordingId, "STOPPED", "Recording stopped, proxy-agent kept alive");
        } catch (Exception e) {
            logger.error("[proxy-agent] Failed to stop recording only {}", recordingId, e);
            throw new RuntimeException("Failed to stop recording: " + e.getMessage(), e);
        }
    }

    /**
     * 重新启动 proxy-agent 的录制模式（Stage 4 使用）。
     */
    public void resumeRecording(String recordingId) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance != null) {
            proxyControl.setMode(instance.getProxyPodIp(), instance.getControlPort(), "record");
            logger.info("[proxy-agent] Resumed recording for {}", recordingId);
        }
    }

    /**
     * 清空 proxy-agent 的 snapshot 数据（Stage 4 每个服务注入前调用，隔离故障快照）。
     */
    public void clearProxySnapshots(String recordingId) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance != null) {
            proxyControl.clearSnapshots(instance.getProxyPodIp(), instance.getControlPort());
            logger.info("[proxy-agent] Cleared snapshots for {}", recordingId);
        }
    }

    /**
     * 获取 recordingId 对应的服务名称。
     */
    public String getServiceName(String recordingId) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        return instance != null ? instance.getTargetService() : null;
    }

    /**
     * 切换 proxy-agent 为 intercept 模式并加载拦截规则（Stage 5 使用）。
     * 规则格式: [{path_match, method, baggage_match, status_code, body}]
     */
    public void switchToIntercept(String recordingId, java.util.List<java.util.Map<String, Object>> rules) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance == null) {
            throw new RuntimeException("ProxyInstance not found: " + recordingId);
        }
        String podIp = instance.getProxyPodIp();
        int controlPort = instance.getControlPort();

        // 先推送规则（区分 HTTP 和 gRPC）
        for (java.util.Map<String, Object> rule : rules) {
            String bodyBase64 = (String) rule.get("body_base64");
            if (bodyBase64 != null && !bodyBase64.isEmpty()) {
                // gRPC 规则：使用 Base64 编码的 protobuf body
                proxyControl.addRuleWithBase64Body(podIp, controlPort,
                        (String) rule.get("path_match"),
                        (String) rule.get("method"),
                        rule.get("status_code") != null ? ((Number) rule.get("status_code")).intValue() : 0,
                        bodyBase64,
                        (String) rule.get("baggage_match"));
            } else {
                // HTTP 规则：使用明文 body
                proxyControl.addRuleWithBaggage(podIp, controlPort,
                        (String) rule.get("path_match"),
                        (String) rule.get("method"),
                        rule.get("status_code") != null ? ((Number) rule.get("status_code")).intValue() : 200,
                        (String) rule.get("body"),
                        (String) rule.get("baggage_match"));
            }
        }
        // 切换模式
        proxyControl.setMode(podIp, controlPort, "intercept");
        logger.info("[proxy-agent] Switched to intercept mode: recordingId={}, rules={}", recordingId, rules.size());
    }

    /**
     * 获取 proxy-agent 的快照数据（用于 Stage 5 构建拦截规则）。
     */
    public java.util.List<java.util.Map<String, Object>> getProxySnapshots(String recordingId) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance == null) return java.util.Collections.emptyList();
        return proxyControl.getSnapshots(instance.getProxyPodIp(), instance.getControlPort());
    }

    /**
     * 完整清理：恢复 selector → 删除 proxy-agent → 删除影子 Service。
     * Stage 6 完成后调用。
     */
    public void fullCleanup(String recordingId) {
        logger.info("[proxy-agent] Full cleanup for recording {}", recordingId);
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance == null) {
            logger.warn("ProxyInstance not found for recording {}, skip cleanup", recordingId);
            return;
        }
        String namespace = instance.getNamespace();
        String targetService = instance.getTargetService();

        // 1. 恢复 selector
        try { proxyManager.restoreService(recordingId); } catch (Exception e) {
            logger.warn("[Cleanup] Failed to restore service {}: {}", targetService, e.getMessage());
        }
        // 2. 等待 EndpointSlice 传播
        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        // 3. 删除 proxy-agent Deployment
        try { proxyManager.destroyProxy(recordingId); } catch (Exception e) {
            logger.warn("[Cleanup] Failed to destroy proxy {}: {}", recordingId, e.getMessage());
        }
        // 4. 删除影子 Service
        try { proxyManager.deleteShadowService(namespace, targetService); } catch (Exception e) {
            logger.warn("[Cleanup] Failed to delete shadow service {}: {}", targetService, e.getMessage());
        }
        logger.info("[proxy-agent] Full cleanup completed for recording {}", recordingId);
    }

    /**
     * 重启目标服务的 Deployment，强制重建 Pod 以清除缓存的 gRPC 长连接。
     * Selector Swap 后调用，确保所有新建连接经过 proxy-agent。
     */
    public void restartOriginalDeployment(String namespace, String serviceName) {
        proxyManager.restartDeployment(namespace, serviceName);
    }

    /**
     * 从 proxy-agent 拉取快照并存入 proxy_snapshot 表
     */
    /**
     * 从 proxy-agent 拉取 snapshot 数据并保存到 MySQL proxy_snapshot 表。
     * 供外部在 collectAllRecordedData() 之前调用，确保数据已持久化。
     */
    public void pullSnapshots(String recordingId) {
        pullAndSaveSnapshots(recordingId);
    }

    @SuppressWarnings("unchecked")
    private void pullAndSaveSnapshots(String recordingId) {
        ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
        if (instance == null || instance.getProxyPodIp() == null) {
            logger.warn("No proxy instance or pod IP for recording {}, skip snapshot pull", recordingId);
            return;
        }

        List<Map<String, Object>> snapshots = proxyControl.getSnapshots(
                instance.getProxyPodIp(), instance.getControlPort());
        logger.info("Pulled {} snapshots from proxy-agent for recording {}", snapshots.size(), recordingId);

        for (Map<String, Object> snap : snapshots) {
            try {
                ProxySnapshot entity = new ProxySnapshot();
                entity.setRecordingId(recordingId);

                Map<String, Object> sig = (Map<String, Object>) snap.get("signature");
                if (sig != null) {
                    entity.setSignatureHash((String) sig.get("hash"));
                    entity.setMethod((String) sig.get("method"));
                    entity.setPath((String) sig.get("path"));
                }

                // 协议判断
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
                logger.warn("Failed to save snapshot for recording {}: {}", recordingId, e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  旧引擎: Envoy sidecar（原有逻辑不变）
    // ═══════════════════════════════════════════════════════════════════════

    private RecordingResponse startWithEnvoy(StartRecordingRequest request) {
        String recordingId = generateRecordingId();
        logger.info("[envoy] Starting recording {} for service {}/{}", recordingId, request.getNamespace(), request.getServiceName());

        try {
            RecordingState state = new RecordingState(recordingId, request.getNamespace(), request.getServiceName());
            state.setRules(request.getRules());
            state.setDurationSec(request.getDurationSec());
            state.setEnvoyPort(recordingConfig.getEnvoy().getPort());

            Integer appPort = request.getAppPort();
            if (appPort == null) {
                appPort = getServiceOriginalTargetPort(request.getNamespace(), request.getServiceName());
                if (appPort == null) {
                    throw new RuntimeException("Cannot determine application port for service: " + request.getServiceName());
                }
            }
            state.setAppPortOriginal(appPort);

            String configMapName = "envoy-tap-" + recordingId.toLowerCase();
            String deploymentName = request.getServiceName();
            state.setConfigMapName(configMapName);
            state.setDeploymentName(deploymentName);
            stateService.saveState(state);

            String envoyYaml = tapRenderer.render(appPort, request.getRules());
            tapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyYaml);
            tapManager.injectOrUpdateSidecar(request.getNamespace(), deploymentName, configMapName);
            tapManager.redirectServiceToEnvoy(request.getNamespace(), request.getServiceName(),
                    recordingConfig.getEnvoy().getPort());
            tapManager.waitRolloutReady(request.getNamespace(), deploymentName);

            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.RECORDING);

            if (request.getDurationSec() != null && request.getDurationSec() > 0) {
                tapManager.scheduleAutoStop(recordingId, request.getDurationSec(), () -> {
                    try { stop(recordingId); }
                    catch (Exception e) { stateService.setError(recordingId, "Auto-stop failed: " + e.getMessage()); }
                });
            }

            return new RecordingResponse(recordingId, "RUNNING", "Recording started successfully");

        } catch (Exception e) {
            logger.error("[envoy] Failed to start recording {}: {}", recordingId, e.getMessage(), e);
            try { stateService.setError(recordingId, e.getMessage()); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to start recording: " + e.getMessage(), e);
        }
    }

    private RecordingResponse stopWithEnvoy(String recordingId) {
        logger.info("[envoy] Stopping recording {}", recordingId);

        try {
            RecordingState state = stateService.loadState(recordingId);
            if (state.getStatus() == RecordingState.RecordingStatus.STOPPED) {
                return new RecordingResponse(recordingId, "STOPPED", "Recording already stopped");
            }

            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPING);
            try { tapManager.cancelAutoStop(recordingId); } catch (Exception ignore) {}

            tapManager.redirectServiceToEnvoy(state.getNamespace(), state.getServiceName(), state.getAppPortOriginal());
            tapManager.removeSidecarOrDisableTap(state.getNamespace(), state.getDeploymentName());
            tapManager.waitRolloutReady(state.getNamespace(), state.getDeploymentName());

            try { tapCollector.collectOnce(recordingId, state).get(); }
            catch (Exception e) { logger.warn("Final collection failed: {}", e.getMessage()); }

            try {
                if (state.getConfigMapName() != null) {
                    tapManager.deleteConfigMap(state.getNamespace(), state.getConfigMapName());
                }
            } catch (Exception e) { logger.warn("Failed to delete ConfigMap: {}", e.getMessage()); }

            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPED);
            return new RecordingResponse(recordingId, "STOPPED", "Recording stopped successfully");

        } catch (Exception e) {
            logger.error("[envoy] Failed to stop recording {}: {}", recordingId, e.getMessage(), e);
            try { stateService.setError(recordingId, e.getMessage()); } catch (Exception ex) { /* ignore */ }
            throw new RuntimeException("Failed to stop recording: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  公共方法（不受 feature flag 影响）
    // ═══════════════════════════════════════════════════════════════════════

    public RecordingStatusResponse getStatus(String recordingId) {
        try {
            RecordingState state = stateService.loadState(recordingId);
            long entryCount = isNewEngine()
                    ? snapshotRepo.findByRecordingId(recordingId).size()
                    : tapCollector.getEntryCount(recordingId);

            RecordingStatusResponse response = new RecordingStatusResponse(recordingId, state.getStatus().name());
            response.setNamespace(state.getNamespace());
            response.setServiceName(state.getServiceName());
            response.setAppPortOriginal(state.getAppPortOriginal());
            response.setEnvoyPort(state.getEnvoyPort());
            response.setRules(state.getRules());
            response.setStartAt(state.getStartedAt());
            response.setEndAt(state.getStoppedAt());
            response.setDurationSec(state.getDurationSec());
            response.setEntryCount(entryCount);
            response.setMessage(state.getErrorMessage());

            try {
                response.setDeploymentStatus(getDeploymentStatus(state.getNamespace(), state.getDeploymentName()));
                response.setServiceStatus(getServiceStatus(state.getNamespace(), state.getServiceName()));
            } catch (Exception e) {
                logger.warn("Failed to get K8s resource status: {}", e.getMessage());
            }
            return response;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get recording status: " + e.getMessage(), e);
        }
    }

    public List<RecordedEntry> getEntries(String recordingId, int offset, int limit) {
        try {
            // 新引擎（proxy-agent）：从 MySQL proxy_snapshot 表读取
            if ("new".equalsIgnoreCase(proxyEngine)) {
                List<ProxySnapshot> snapshots = snapshotRepo.findByRecordingId(recordingId);
                // 查找对应的 proxy_instance 以获取服务名和 namespace
                ProxyInstance instance = proxyManager.getProxyInstance(recordingId);
                String svcName = (instance != null) ? instance.getTargetService() : "";
                String ns = (instance != null) ? instance.getNamespace() : "";

                List<RecordedEntry> entries = new java.util.ArrayList<>();
                for (int i = offset; i < Math.min(snapshots.size(), offset + limit); i++) {
                    ProxySnapshot snap = snapshots.get(i);
                    RecordedEntry entry = new RecordedEntry();
                    entry.setRecordingId(recordingId);
                    entry.setServiceName(svcName);
                    entry.setNamespace(ns);
                    entry.setMethod(snap.getMethod());
                    entry.setPath(snap.getPath());
                    entry.setStatus(snap.getResponseStatus());
                    entry.setTimestamp(snap.getRecordedAt());
                    // headers
                    try {
                        if (snap.getRequestHeaders() != null) {
                            entry.setRequestHeaders(new ObjectMapper().readValue(snap.getRequestHeaders(),
                                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {}));
                        }
                        if (snap.getResponseHeaders() != null) {
                            entry.setResponseHeaders(new ObjectMapper().readValue(snap.getResponseHeaders(),
                                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {}));
                        }
                    } catch (Exception ignore) { }
                    // body
                    if (snap.getRequestBody() != null) {
                        entry.setRequestBody(new String(snap.getRequestBody(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (snap.getResponseBody() != null) {
                        entry.setResponseBody(new String(snap.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                    entries.add(entry);
                }
                logger.info("getEntries(proxy-agent): recordingId={}, service={}, total={}, returned={}",
                        recordingId, svcName, snapshots.size(), entries.size());
                return entries;
            }
            // 旧引擎（Envoy）：从 Redis 读取
            if (!stateService.exists(recordingId)) {
                throw new RuntimeException("Recording not found: " + recordingId);
            }
            return tapCollector.readFromRedis(recordingId, offset, limit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get recording entries: " + e.getMessage(), e);
        }
    }

    // ─── Internal helpers ──────────────────────────────────────────────────

    private String generateRecordingId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "rec-" + timestamp + "-" + uuid;
    }

    private Integer getServiceOriginalTargetPort(String namespace, String serviceName) {
        try {
            Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
            if (service == null) throw new RuntimeException("Service not found: " + serviceName);
            if (service.getSpec().getPorts() != null && !service.getSpec().getPorts().isEmpty()) {
                return service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get service target port: " + e.getMessage(), e);
        }
    }

    private String getDeploymentStatus(String namespace, String deploymentName) {
        try {
            io.fabric8.kubernetes.api.model.apps.Deployment deployment =
                    k8s.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) return "NOT_FOUND";
            io.fabric8.kubernetes.api.model.apps.DeploymentStatus status = deployment.getStatus();
            if (status == null) return "UNKNOWN";
            Integer replicas = status.getReplicas();
            Integer readyReplicas = status.getReadyReplicas();
            return (replicas != null && replicas.equals(readyReplicas)) ? "READY" : "NOT_READY";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private String getServiceStatus(String namespace, String serviceName) {
        try {
            Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
            return service != null ? "ACTIVE" : "NOT_FOUND";
        } catch (Exception e) {
            return "ERROR";
        }
    }
}
