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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 混合服务 - 同时支持录制和拦截功能
 * 
 * 核心功能：
 * 1. 管理录制和拦截的混合配置
 * 2. 确保拦截的请求也能被正确录制
 * 3. 支持动态添加/移除拦截规则
 */
@Service
public class HybridService {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridService.class);
    
    @Autowired private K8sTapManager tapManager;
    @Autowired private HybridConfigRenderer hybridRenderer;
    @Autowired private RecordingStateService stateService;
    @Autowired private RecordingConfig recordingConfig;
    
    /**
     * 启动混合模式（录制 + 拦截）
     */
    public HybridResponse startHybrid(StartHybridRequest request) {
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
    
    /**
     * 添加拦截规则到现有的混合会话
     */
    public HybridResponse addInterceptionRules(String hybridId, List<InterceptionRule> newRules) {
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
    
    /**
     * 停止混合模式
     */
    public HybridResponse stopHybrid(String hybridId) {
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

            // TODO: 获取实际的录制和拦截计数
            response.setRecordedCount(0);
            response.setInterceptedCount(0);

            return response;

        } catch (Exception e) {
            logger.error("Failed to get hybrid status: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get hybrid status: " + e.getMessage(), e);
        }
    }

    /**
     * 移除拦截规则
     */
    public HybridResponse removeInterceptionRules(String hybridId, List<InterceptionRule> rulesToRemove) {
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

    /**
     * 更新拦截规则（替换所有现有规则）
     */
    public HybridResponse updateInterceptionRules(String hybridId, List<InterceptionRule> newRules) {
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

    /**
     * 生成混合会话ID
     */
    private String generateHybridId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "hybrid_" + timestamp + "_" + uuid;
    }
}
