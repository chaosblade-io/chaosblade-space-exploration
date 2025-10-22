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

package com.chaosblade.svc.reqrspproxy.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.reqrspproxy.dto.*;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.service.InterceptionService;
import com.chaosblade.svc.reqrspproxy.service.RecordingStateService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能拦截控制器
 *
 * 核心功能：
 * 1. 智能检测目标服务的录制状态
 * 2. 基于现有配置添加拦截功能
 * 3. 支持独立的拦截模式
 *
 * API 路径：/api/interceptions
 */
@RestController
@RequestMapping("/api/interceptions")
public class InterceptionController {
    
    private static final Logger logger = LoggerFactory.getLogger(InterceptionController.class);
    
    @Autowired
    private InterceptionService interceptionService;
    
    @Autowired
    private RecordingStateService stateService;
    
    /**
     * 智能添加拦截规则
     * POST /api/interceptions/start
     *
     * 自动检测目标服务状态：
     * - 如果已在录制，基于现有配置添加拦截
     * - 如果未在录制，启动仅拦截模式
     */
    @PostMapping("/start")
    public ApiResponse<InterceptionResponse> addInterception(@Valid @RequestBody AddInterceptionRequest request) {
        logger.info("POST /api/interceptions/start - namespace: {}, serviceName: {}, rules: {}",
                   request.getNamespace(), request.getServiceName(), request.getInterceptionRules().size());
        
        try {
            InterceptionResponse response = interceptionService.addInterception(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to add interception: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to add interception: " + e.getMessage());
        }
    }
    
    /**
     * 移除拦截规则
     * DELETE /api/interceptions/session/{sessionId}/rules
     */
    @DeleteMapping("/session/{sessionId}/rules")
    public ApiResponse<InterceptionResponse> removeInterceptionRules(@PathVariable String sessionId,
                                                                    @RequestBody List<InterceptionRule> rules) {
        logger.info("DELETE /api/interceptions/session/{}/rules - removing {} rules", sessionId, rules.size());
        
        try {
            InterceptionResponse response = interceptionService.removeInterception(sessionId, rules);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to remove interception rules from {}: {}", sessionId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to remove interception rules: " + e.getMessage());
        }
    }
    
    /**
     * 停止拦截
     * POST /api/interceptions/session/{sessionId}/stop
     */
    @PostMapping("/session/{sessionId}/stop")
    public ApiResponse<InterceptionResponse> stopInterception(@PathVariable String sessionId) {
        logger.info("POST /api/interceptions/session/{}/stop", sessionId);
        
        try {
            InterceptionResponse response = interceptionService.stopInterception(sessionId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to stop interception {}: {}", sessionId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to stop interception: " + e.getMessage());
        }
    }
    
    /**
     * 获取拦截状态
     * GET /api/interceptions/session/{sessionId}/status
     */
    @GetMapping("/session/{sessionId}/status")
    public ApiResponse<InterceptionStatusResponse> getInterceptionStatus(@PathVariable String sessionId) {
        logger.info("GET /api/interceptions/session/{}/status", sessionId);
        
        try {
            RecordingState state = stateService.loadState(sessionId);
            
            InterceptionStatusResponse response = new InterceptionStatusResponse();
            response.setSessionId(sessionId);
            response.setNamespace(state.getNamespace());
            response.setServiceName(state.getServiceName());
            response.setStatus(state.getStatus().name());
            response.setRecordingRules(state.getRules());
            response.setInterceptionRules(state.getInterceptionRules());
            response.setStartedAt(state.getStartedAt());
            response.setStoppedAt(state.getStoppedAt());
            
            // 判断模式
            if (state.getRules().size() > state.getInterceptionRules().size()) {
                response.setMode("RECORDING_WITH_INTERCEPTION");
            } else {
                response.setMode("INTERCEPTION_ONLY");
            }
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get interception status for {}: {}", sessionId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get interception status: " + e.getMessage());
        }
    }
    
    /**
     * 检查服务的录制状态
     * GET /api/interceptions/check/{namespace}/{serviceName}
     */
    @GetMapping("/check/{namespace}/{serviceName}")
    public ApiResponse<ServiceRecordingStatusResponse> checkServiceRecordingStatus(
            @PathVariable String namespace, 
            @PathVariable String serviceName) {
        
        logger.info("GET /api/interceptions/check/{}/{}", namespace, serviceName);
        
        try {
            List<RecordingState> activeRecordings = stateService.getAllActiveRecordingStates();
            
            RecordingState existingRecording = activeRecordings.stream()
                    .filter(state -> namespace.equals(state.getNamespace()) && 
                                   serviceName.equals(state.getServiceName()))
                    .findFirst()
                    .orElse(null);
            
            ServiceRecordingStatusResponse response = new ServiceRecordingStatusResponse();
            response.setNamespace(namespace);
            response.setServiceName(serviceName);
            
            if (existingRecording != null) {
                response.setIsRecording(true);
                response.setRecordingId(existingRecording.getRecordingId());
                response.setRecordingRules(existingRecording.getRules());
                response.setInterceptionRules(existingRecording.getInterceptionRules());
                response.setStartedAt(existingRecording.getStartedAt());
                response.setMessage("Service is currently in recording mode");
            } else {
                response.setIsRecording(false);
                response.setMessage("Service is not in recording mode");
            }
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to check service recording status for {}/{}: {}", 
                        namespace, serviceName, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to check service recording status: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有活跃的拦截会话
     * GET /api/interceptions/active
     */
    @GetMapping("/active")
    public ApiResponse<List<InterceptionSummary>> getActiveInterceptions() {
        logger.info("GET /api/interceptions/active");
        
        try {
            List<RecordingState> activeStates = stateService.getAllActiveRecordingStates();
            
            List<InterceptionSummary> summaries = activeStates.stream()
                    .filter(state -> !state.getInterceptionRules().isEmpty())
                    .map(state -> {
                        InterceptionSummary summary = new InterceptionSummary();
                        summary.setSessionId(state.getRecordingId());
                        summary.setNamespace(state.getNamespace());
                        summary.setServiceName(state.getServiceName());
                        summary.setInterceptionRulesCount(state.getInterceptionRules().size());
                        summary.setRecordingRulesCount(state.getRules().size());
                        summary.setStartedAt(state.getStartedAt());
                        
                        if (state.getRules().size() > state.getInterceptionRules().size()) {
                            summary.setMode("RECORDING_WITH_INTERCEPTION");
                        } else {
                            summary.setMode("INTERCEPTION_ONLY");
                        }
                        
                        return summary;
                    })
                    .toList();
            
            return ApiResponse.success(summaries);
            
        } catch (Exception e) {
            logger.error("Failed to get active interceptions: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get active interceptions: " + e.getMessage());
        }
    }

    /**
     * 诊断拦截问题
     * GET /api/interceptions/session/{sessionId}/diagnose
     */
    @GetMapping("/session/{sessionId}/diagnose")
    public ApiResponse<Map<String, Object>> diagnoseInterception(@PathVariable String sessionId) {
        logger.info("GET /api/interceptions/session/{}/diagnose", sessionId);

        try {
            Map<String, Object> diagnosis = new HashMap<>();

            // 1. 检查拦截状态
            RecordingState state = stateService.loadState(sessionId);
            diagnosis.put("interceptionState", Map.of(
                "sessionId", sessionId,
                "status", state.getStatus().name(),
                "namespace", state.getNamespace(),
                "serviceName", state.getServiceName(),
                "interceptionRulesCount", state.getInterceptionRules().size(),
                "configMapName", state.getConfigMapName(),
                "deploymentName", state.getDeploymentName()
            ));

            // 2. 生成诊断建议
            List<String> suggestions = new ArrayList<>();

            if (state.getInterceptionRules().isEmpty()) {
                suggestions.add("没有拦截规则，请先添加拦截规则");
            }

            if (state.getConfigMapName() == null) {
                suggestions.add("ConfigMap 名称为空，可能创建失败");
            }

            suggestions.add("请检查 Pod 是否包含 Envoy sidecar");
            suggestions.add("请检查 Service targetPort 是否重定向到 15006");
            suggestions.add("请查看 Envoy 日志确认配置是否正确加载");

            diagnosis.put("suggestions", suggestions);

            // 3. 提供测试命令
            Map<String, String> testCommands = new HashMap<>();
            testCommands.put("checkConfigMap",
                String.format("kubectl get configmap %s -n %s", state.getConfigMapName(), state.getNamespace()));
            testCommands.put("checkService",
                String.format("kubectl get service %s -n %s -o yaml", state.getServiceName(), state.getNamespace()));
            testCommands.put("checkPods",
                String.format("kubectl get pods -n %s | grep %s", state.getNamespace(), state.getServiceName()));

            if (!state.getInterceptionRules().isEmpty()) {
                testCommands.put("testInterception",
                    String.format("curl -X %s http://%s:12031%s -H 'Content-Type: application/json' -d '{\"test\":\"data\"}' -v",
                        state.getInterceptionRules().get(0).getMethod(),
                        state.getServiceName(),
                        state.getInterceptionRules().get(0).getPath()));
            }

            diagnosis.put("testCommands", testCommands);

            return ApiResponse.success(diagnosis);

        } catch (Exception e) {
            logger.error("Failed to diagnose interception for {}: {}", sessionId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to diagnose interception: " + e.getMessage());
        }
    }
}
