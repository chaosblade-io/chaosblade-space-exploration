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
import com.chaosblade.svc.taskresource.entity.FaultConfiguration;
import com.chaosblade.svc.taskresource.service.FaultConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 故障配置管理控制器 */
@RestController
@RequestMapping("/api")
public class FaultConfigurationController {

  private static final Logger logger = LoggerFactory.getLogger(FaultConfigurationController.class);

  @Autowired private FaultConfigurationService faultConfigurationService;

  /** 获取故障配置列表 GET /api/fault-configs */
  @GetMapping("/fault-configs")
  public ApiResponse<PageResponse<FaultConfiguration>> getFaultConfigurations(
      @RequestParam(value = "faultTypeId", required = false) Long faultTypeId,
      @RequestParam(value = "configId", required = false) Long configId,
      @RequestParam(value = "nodeId", required = false) Long nodeId,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

    logger.info(
        "GET /api/fault-configs - faultTypeId: {}, configId: {}, nodeId: {}, page: {}, size: {}",
        faultTypeId,
        configId,
        nodeId,
        page,
        size);

    PageResponse<FaultConfiguration> configs =
        faultConfigurationService.getFaultConfigurations(faultTypeId, configId, nodeId, page, size);
    return ApiResponse.success(configs);
  }

  /** 根据ID获取故障配置详情 GET /api/fault-configs/{configId} */
  @GetMapping("/fault-configs/{configId}")
  public ApiResponse<FaultConfiguration> getFaultConfigurationById(@PathVariable Long configId) {
    logger.info("GET /api/fault-configs/{}", configId);

    FaultConfiguration config = faultConfigurationService.getFaultConfigurationById(configId);
    return ApiResponse.success(config);
  }

  /** 创建新的故障配置 POST /api/fault-configs */
  @PostMapping("/fault-configs")
  public ApiResponse<FaultConfiguration> createFaultConfiguration(
      @Valid @RequestBody FaultConfiguration faultConfiguration) {
    logger.info(
        "POST /api/fault-configs - configId: {}, nodeId: {}, faultTypeId: {}",
        faultConfiguration.getConfigId(),
        faultConfiguration.getNodeId(),
        faultConfiguration.getFaultTypeId());

    FaultConfiguration createdConfig =
        faultConfigurationService.createFaultConfiguration(faultConfiguration);
    return ApiResponse.success(createdConfig);
  }

  /** 删除故障配置 DELETE /api/fault-configs/{configId} */
  @DeleteMapping("/fault-configs/{configId}")
  public ApiResponse<Void> deleteFaultConfiguration(@PathVariable Long configId) {
    logger.info("DELETE /api/fault-configs/{}", configId);

    faultConfigurationService.deleteFaultConfiguration(configId);
    return ApiResponse.success(null);
  }
}
