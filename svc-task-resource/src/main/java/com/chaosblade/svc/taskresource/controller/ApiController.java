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

package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.Api;
import com.chaosblade.svc.taskresource.service.ApiService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * API管理控制器
 */
@RestController
@RequestMapping("/api")
public class ApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    
    @Autowired
    private ApiService apiService;
    
    /**
     * 获取系统的API列表
     * GET /api/systems/{systemId}/apis
     */
    @GetMapping("/systems/{systemId}/apis")
    public ApiResponse<PageResponse<Api>> getSystemApis(
            @PathVariable Long systemId,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "50") @Min(1) int size) {

        logger.info("GET /api/systems/{}/apis - method: {}, path: {}, tags: {}, version: {}, page: {}, size: {}",
                   systemId, method, path, tags, version, page, size);

        PageResponse<Api> apis = apiService.getApisBySystemId(systemId, method, path, tags, version, page, size);
        return ApiResponse.success(apis);
    }
    
    /**
     * 获取系统的特定API详情
     * GET /api/systems/{systemId}/apis/{operationId}
     */
    @GetMapping("/systems/{systemId}/apis/{operationId}")
    public ApiResponse<Api> getSystemApiByOperationId(@PathVariable Long systemId, 
                                                     @PathVariable String operationId) {
        logger.info("GET /api/systems/{}/apis/{}", systemId, operationId);
        
        Api api = apiService.getApiBySystemIdAndOperationId(systemId, operationId);
        return ApiResponse.success(api);
    }
    
    /**
     * 创建系统API
     * POST /api/systems/{systemId}/apis
     */
    @PostMapping("/systems/{systemId}/apis")
    public ApiResponse<Api> createSystemApi(@PathVariable Long systemId,
                                           @Valid @RequestBody Api api) {
        logger.info("POST /api/systems/{}/apis - operationId: {}", systemId, api.getOperationId());
        
        api.setSystemId(systemId);
        Api createdApi = apiService.createApi(api);
        return ApiResponse.success(createdApi);
    }
    
    /**
     * 获取API详情
     * GET /api/apis/{apiId}
     */
    @GetMapping("/apis/{apiId}")
    public ApiResponse<Api> getApiById(@PathVariable Long apiId) {
        logger.info("GET /api/apis/{}", apiId);
        
        Api api = apiService.getApiById(apiId);
        return ApiResponse.success(api);
    }
    
    /**
     * 更新API信息
     * PUT /api/apis/{apiId}
     */
    @PutMapping("/apis/{apiId}")
    public ApiResponse<Api> updateApi(@PathVariable Long apiId,
                                     @Valid @RequestBody Api api) {
        logger.info("PUT /api/apis/{}", apiId);
        
        Api updatedApi = apiService.updateApi(apiId, api);
        return ApiResponse.success(updatedApi);
    }
    
    /**
     * 删除API
     * DELETE /api/apis/{apiId}
     */
    @DeleteMapping("/apis/{apiId}")
    public ApiResponse<Void> deleteApi(@PathVariable Long apiId) {
        logger.info("DELETE /api/apis/{}", apiId);
        
        apiService.deleteApi(apiId);
        return ApiResponse.success();
    }
}
