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
import com.chaosblade.svc.reqrspproxy.dto.RecordedEntry;
import com.chaosblade.svc.reqrspproxy.entity.RecordingState;
import com.chaosblade.svc.reqrspproxy.service.DirectTapReader;
import com.chaosblade.svc.reqrspproxy.service.RecordingStateService;

import jakarta.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 直接从 Pod 文件系统读取 Tap 数据的控制器
 * 用于调试和临时访问 tap 数据
 */
@RestController
@RequestMapping("/api/direct-tap")
public class DirectTapController {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectTapController.class);
    
    @Autowired
    private DirectTapReader directTapReader;

    @Autowired
    private RecordingStateService stateService;
    
    /**
     * 直接从 Pod 读取 tap 数据
     * GET /api/direct-tap/{namespace}/{serviceName}/entries
     */
    @GetMapping("/{namespace}/{serviceName}/entries")
    public ApiResponse<List<RecordedEntry>> getEntriesDirectly(
            @PathVariable String namespace,
            @PathVariable String serviceName,
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit) {
        
        logger.info("GET /api/direct-tap/{}/{}/entries - offset: {}, limit: {}", 
                   namespace, serviceName, offset, limit);
        
        try {
            List<RecordedEntry> entries = directTapReader.readTapDataDirectly(
                namespace, serviceName, offset, limit);
            
            logger.info("Successfully read {} entries from {}/{}", 
                       entries.size(), namespace, serviceName);
            return ApiResponse.success(entries);
            
        } catch (Exception e) {
            logger.error("Failed to read tap data directly from {}/{}: {}", 
                        namespace, serviceName, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to read tap data: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定服务的 Pod 列表和 tap 文件统计
     * GET /api/direct-tap/{namespace}/{serviceName}/info
     */
    @GetMapping("/{namespace}/{serviceName}/info")
    public ApiResponse<Object> getTapInfo(
            @PathVariable String namespace,
            @PathVariable String serviceName) {

        logger.info("GET /api/direct-tap/{}/{}/info", namespace, serviceName);

        try {
            Object info = directTapReader.getTapInfo(namespace, serviceName);
            return ApiResponse.success(info);

        } catch (Exception e) {
            logger.error("Failed to get tap info for {}/{}: {}",
                        namespace, serviceName, e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get tap info: " + e.getMessage());
        }
    }

    /**
     * 获取所有运行中的录制 ID
     * GET /api/direct-tap/recordings/active
     */
    @GetMapping("/recordings/active")
    public ApiResponse<List<String>> getActiveRecordings() {

        logger.info("GET /api/direct-tap/recordings/active");

        try {
            List<String> activeRecordings = stateService.getAllActiveRecordings();

            logger.info("Found {} active recordings", activeRecordings.size());

            return ApiResponse.success(activeRecordings);

        } catch (Exception e) {
            logger.error("Failed to get active recordings: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get active recordings: " + e.getMessage());
        }
    }

    /**
     * 获取所有运行中的录制详细信息
     * GET /api/direct-tap/recordings/active/details
     */
    @GetMapping("/recordings/active/details")
    public ApiResponse<List<RecordingState>> getActiveRecordingDetails() {

        logger.info("GET /api/direct-tap/recordings/active/details");

        try {
            List<RecordingState> activeRecordings = stateService.getAllActiveRecordingStates();

            logger.info("Found {} active recordings with details", activeRecordings.size());

            return ApiResponse.success(activeRecordings);

        } catch (Exception e) {
            logger.error("Failed to get active recording details: {}", e.getMessage(), e);
            return ApiResponse.error("500", "Failed to get active recording details: " + e.getMessage());
        }
    }
}
