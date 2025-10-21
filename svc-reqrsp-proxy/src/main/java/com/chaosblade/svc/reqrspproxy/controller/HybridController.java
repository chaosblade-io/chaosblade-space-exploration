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
import com.chaosblade.svc.reqrspproxy.service.HybridService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 混合模式控制器 - 同时支持录制和拦截功能
 * 
 * API 路径：/api/hybrid
 */
@RestController
@RequestMapping("/api/hybrid")
public class HybridController {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridController.class);
    
    @Autowired
    private HybridService hybridService;
    
    /**
     * 启动混合模式（录制 + 拦截）
     * POST /api/hybrid/start
     */
    @PostMapping("/start")
    public ApiResponse<HybridResponse> start(@Valid @RequestBody StartHybridRequest request) {
        logger.info("POST /api/hybrid/start - namespace: {}, serviceName: {}, recordingRules: {}, interceptionRules: {}", 
                   request.getNamespace(), request.getServiceName(), 
                   request.getRecordingRules().size(), request.getInterceptionRules().size());
        
        try {
            HybridResponse response = hybridService.startHybrid(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to start hybrid mode: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to start hybrid mode: " + e.getMessage());
        }
    }
    
    /**
     * 停止混合模式
     * POST /api/hybrid/{hybridId}/stop
     */
    @PostMapping("/{hybridId}/stop")
    public ApiResponse<HybridResponse> stop(@PathVariable String hybridId) {
        logger.info("POST /api/hybrid/{}/stop", hybridId);
        
        try {
            HybridResponse response = hybridService.stopHybrid(hybridId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to stop hybrid mode {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to stop hybrid mode: " + e.getMessage());
        }
    }
    
    /**
     * 获取混合模式状态
     * GET /api/hybrid/{hybridId}
     */
    @GetMapping("/{hybridId}")
    public ApiResponse<HybridStatusResponse> getStatus(@PathVariable String hybridId) {
        logger.info("GET /api/hybrid/{}", hybridId);
        
        try {
            HybridStatusResponse response = hybridService.getHybridStatus(hybridId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to get hybrid status for {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get hybrid status: " + e.getMessage());
        }
    }
    
    /**
     * 添加拦截规则到现有的混合会话
     * POST /api/hybrid/{hybridId}/interceptions
     */
    @PostMapping("/{hybridId}/interceptions")
    public ApiResponse<HybridResponse> addInterceptionRules(@PathVariable String hybridId, 
                                                           @Valid @RequestBody List<InterceptionRule> rules) {
        logger.info("POST /api/hybrid/{}/interceptions - adding {} rules", hybridId, rules.size());
        
        try {
            HybridResponse response = hybridService.addInterceptionRules(hybridId, rules);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to add interception rules to {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to add interception rules: " + e.getMessage());
        }
    }
    
    /**
     * 移除拦截规则
     * DELETE /api/hybrid/{hybridId}/interceptions
     */
    @DeleteMapping("/{hybridId}/interceptions")
    public ApiResponse<HybridResponse> removeInterceptionRules(@PathVariable String hybridId, 
                                                              @RequestBody List<InterceptionRule> rules) {
        logger.info("DELETE /api/hybrid/{}/interceptions - removing {} rules", hybridId, rules.size());
        
        try {
            HybridResponse response = hybridService.removeInterceptionRules(hybridId, rules);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to remove interception rules from {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to remove interception rules: " + e.getMessage());
        }
    }
    
    /**
     * 更新拦截规则
     * PUT /api/hybrid/{hybridId}/interceptions
     */
    @PutMapping("/{hybridId}/interceptions")
    public ApiResponse<HybridResponse> updateInterceptionRules(@PathVariable String hybridId, 
                                                              @Valid @RequestBody List<InterceptionRule> rules) {
        logger.info("PUT /api/hybrid/{}/interceptions - updating to {} rules", hybridId, rules.size());
        
        try {
            HybridResponse response = hybridService.updateInterceptionRules(hybridId, rules);
            return ApiResponse.success(response);
        } catch (Exception e) {
            logger.error("Failed to update interception rules for {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to update interception rules: " + e.getMessage());
        }
    }
    
    /**
     * 获取录制数据（与原有录制 API 兼容）
     * GET /api/hybrid/{hybridId}/data
     */
    @GetMapping("/{hybridId}/data")
    public ApiResponse<List<RecordedEntry>> getRecordedData(@PathVariable String hybridId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        logger.info("GET /api/hybrid/{}/data - page: {}, size: {}", hybridId, page, size);
        
        try {
            List<RecordedEntry> data = hybridService.getRecordedData(hybridId, page, size);
            return ApiResponse.success(data);
        } catch (Exception e) {
            logger.error("Failed to get recorded data for {}: {}", hybridId, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get recorded data: " + e.getMessage());
        }
    }
}
