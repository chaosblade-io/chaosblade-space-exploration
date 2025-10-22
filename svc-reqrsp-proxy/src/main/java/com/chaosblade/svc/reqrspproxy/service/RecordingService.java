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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 录制服务
 */
@Component
public class RecordingService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecordingService.class);
    
    @Autowired
    private KubernetesClient k8s;
    
    @Autowired
    private RecordingConfig recordingConfig;
    
    @Autowired
    private TemplateBasedTapConfigRenderer tapRenderer;
    
    @Autowired
    private K8sTapManager tapManager;
    
    @Autowired
    private TapCollector tapCollector;
    
    @Autowired
    private RecordingStateService stateService;
    
    /**
     * 开始录制
     */
    public RecordingResponse start(StartRecordingRequest request) {
        String recordingId = generateRecordingId();
        logger.info("Starting recording {} for service {}/{}", recordingId, request.getNamespace(), request.getServiceName());
        
        try {
            // 1. 创建录制状态
            RecordingState state = new RecordingState(recordingId, request.getNamespace(), request.getServiceName());
            state.setRules(request.getRules());
            state.setDurationSec(request.getDurationSec());
            state.setEnvoyPort(recordingConfig.getEnvoy().getPort());
            
            // 2. 获取服务的原始 targetPort
            Integer appPort = request.getAppPort();
            if (appPort == null) {
                appPort = getServiceOriginalTargetPort(request.getNamespace(), request.getServiceName());
                if (appPort == null) {
                    throw new RuntimeException("Cannot determine application port for service: " + request.getServiceName());
                }
            }
            state.setAppPortOriginal(appPort);
            
            // 3. 生成配置名称
            String configMapName = "envoy-tap-" + recordingId.toLowerCase();
            String deploymentName = request.getServiceName(); // 假设 deployment 名称与 service 名称相同
            
            state.setConfigMapName(configMapName);
            state.setDeploymentName(deploymentName);
            
            // 4. 保存初始状态
            stateService.saveState(state);
            
            // 5. 渲染 Envoy 配置
            String envoyYaml = tapRenderer.render(appPort, request.getRules());
            
            // 6. 创建/更新 ConfigMap（命名包含 recordingId，用于之后精确清理）
            tapManager.applyOrUpdateConfigMap(request.getNamespace(), configMapName, envoyYaml);

            // 7. 注入 sidecar（记录 configMapName 以便 stop 时删除）
            tapManager.injectOrUpdateSidecar(request.getNamespace(), deploymentName, configMapName);
            
            // 8. 重定向 Service
            tapManager.redirectServiceToEnvoy(request.getNamespace(), request.getServiceName(), 
                    recordingConfig.getEnvoy().getPort());
            
            // 9. 等待部署就绪
            tapManager.waitRolloutReady(request.getNamespace(), deploymentName);
            
            // 10. 更新状态为运行中
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.RECORDING);
            
            // 11. 调度自动停止（如果设置了持续时间）
            if (request.getDurationSec() != null && request.getDurationSec() > 0) {
                tapManager.scheduleAutoStop(recordingId, request.getDurationSec(), () -> {
                    try {
                        stop(recordingId);
                    } catch (Exception e) {
                        logger.error("Auto-stop failed for recording {}: {}", recordingId, e.getMessage(), e);
                        stateService.setError(recordingId, "Auto-stop failed: " + e.getMessage());
                    }
                });
            }
            
            logger.info("Recording {} started successfully", recordingId);
            return new RecordingResponse(recordingId, "RUNNING", "Recording started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start recording {}: {}", recordingId, e.getMessage(), e);
            
            // 设置错误状态
            try {
                stateService.setError(recordingId, e.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to set error state: {}", ex.getMessage(), ex);
            }
            
            throw new RuntimeException("Failed to start recording: " + e.getMessage(), e);
        }
    }
    
    /**
     * 停止录制
     */
    public RecordingResponse stop(String recordingId) {
        logger.info("Stopping recording {}", recordingId);
        
        try {
            RecordingState state = stateService.loadState(recordingId);
            
            if (state.getStatus() == RecordingState.RecordingStatus.STOPPED) {
                return new RecordingResponse(recordingId, "STOPPED", "Recording already stopped");
            }

            // 1. 更新状态为停止中
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPING);
            
            // 2. 取消自动停止任务，避免在stop过程中被再次触发
            try { tapManager.cancelAutoStop(recordingId); } catch (Exception ignore) {}

            // 3. 恢复 Service targetPort
            tapManager.redirectServiceToEnvoy(state.getNamespace(), state.getServiceName(),
                    state.getAppPortOriginal());

            // 4. 移除 sidecar
            tapManager.removeSidecarOrDisableTap(state.getNamespace(), state.getDeploymentName());

            // 5. 等待部署就绪
            tapManager.waitRolloutReady(state.getNamespace(), state.getDeploymentName());
            
            // 5. 最终采集一次数据
            try {
                tapCollector.collectOnce(recordingId, state).get();
            } catch (Exception e) {
                logger.warn("Final collection failed for recording {}: {}", recordingId, e.getMessage());
            }
            
            // 6. 删除与本次录制关联的 Envoy ConfigMap（清空规则）
            try {
                if (state.getConfigMapName() != null) {
                    tapManager.deleteConfigMap(state.getNamespace(), state.getConfigMapName());
                    logger.info("Deleted ConfigMap {} in namespace {}", state.getConfigMapName(), state.getNamespace());
                }
            } catch (Exception e) {
                logger.warn("Failed to delete ConfigMap {}: {}", state.getConfigMapName(), e.getMessage());
            }

            // 7. 更新状态为已停止
            stateService.updateStatus(recordingId, RecordingState.RecordingStatus.STOPPED);

            logger.info("Recording {} stopped successfully", recordingId);
            return new RecordingResponse(recordingId, "STOPPED", "Recording stopped successfully");

        } catch (Exception e) {
            logger.error("Failed to stop recording {}: {}", recordingId, e.getMessage(), e);

            // 设置错误状态（如果state已不存在则忽略）
            try {
                stateService.setError(recordingId, e.getMessage());
            } catch (Exception ex) {
                logger.warn("Skip setting error for {} because state missing or already cleaned: {}", recordingId, ex.getMessage());
            }

            throw new RuntimeException("Failed to stop recording: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取录制状态
     */
    public RecordingStatusResponse getStatus(String recordingId) {
        logger.debug("Getting status for recording {}", recordingId);
        
        try {
            RecordingState state = stateService.loadState(recordingId);
            long entryCount = tapCollector.getEntryCount(recordingId);
            
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
            
            // 获取部署和服务状态
            try {
                response.setDeploymentStatus(getDeploymentStatus(state.getNamespace(), state.getDeploymentName()));
                response.setServiceStatus(getServiceStatus(state.getNamespace(), state.getServiceName()));
            } catch (Exception e) {
                logger.warn("Failed to get K8s resource status: {}", e.getMessage());
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to get recording status for {}: {}", recordingId, e.getMessage(), e);
            throw new RuntimeException("Failed to get recording status: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取录制条目
     */
    public List<RecordedEntry> getEntries(String recordingId, int offset, int limit) {
        logger.debug("Getting entries for recording {} (offset={}, limit={})", recordingId, offset, limit);
        
        try {
            // 验证录制存在
            if (!stateService.exists(recordingId)) {
                throw new RuntimeException("Recording not found: " + recordingId);
            }
            
            return tapCollector.readFromRedis(recordingId, offset, limit);
            
        } catch (Exception e) {
            logger.error("Failed to get entries for recording {}: {}", recordingId, e.getMessage(), e);
            throw new RuntimeException("Failed to get recording entries: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成录制 ID
     */
    private String generateRecordingId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "rec-" + timestamp + "-" + uuid;
    }
    
    /**
     * 获取服务的原始 targetPort
     */
    private Integer getServiceOriginalTargetPort(String namespace, String serviceName) {
        try {
            Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
            if (service == null) {
                throw new RuntimeException("Service not found: " + serviceName);
            }
            
            if (service.getSpec().getPorts() != null && !service.getSpec().getPorts().isEmpty()) {
                return service.getSpec().getPorts().get(0).getTargetPort().getIntVal();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to get original target port for service {}: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Failed to get service target port: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取部署状态
     */
    private String getDeploymentStatus(String namespace, String deploymentName) {
        try {
            var deployment = k8s.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) {
                return "NOT_FOUND";
            }
            
            var status = deployment.getStatus();
            if (status == null) {
                return "UNKNOWN";
            }
            
            Integer replicas = status.getReplicas();
            Integer readyReplicas = status.getReadyReplicas();
            
            if (replicas != null && readyReplicas != null && replicas.equals(readyReplicas)) {
                return "READY";
            } else {
                return "NOT_READY";
            }
            
        } catch (Exception e) {
            logger.warn("Failed to get deployment status: {}", e.getMessage());
            return "ERROR";
        }
    }
    
    /**
     * 获取服务状态
     */
    private String getServiceStatus(String namespace, String serviceName) {
        try {
            Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
            return service != null ? "ACTIVE" : "NOT_FOUND";
        } catch (Exception e) {
            logger.warn("Failed to get service status: {}", e.getMessage());
            return "ERROR";
        }
    }
}
