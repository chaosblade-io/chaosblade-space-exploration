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
import com.chaosblade.svc.taskresource.entity.FaultExecution;
import com.chaosblade.svc.taskresource.service.FaultExecutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 故障执行管理控制器 - 基于fault_injection_results表 */
@RestController
@RequestMapping("/api")
public class FaultExecutionController {

  private static final Logger logger = LoggerFactory.getLogger(FaultExecutionController.class);

  @Autowired private FaultExecutionService faultExecutionService;

  /** 获取故障执行列表 GET /api/fault-executions */
  @GetMapping("/fault-executions")
  public ApiResponse<PageResponse<FaultExecution>> getFaultExecutions(
      @RequestParam(value = "executionId", required = false) Long executionId,
      @RequestParam(value = "topologyNodeId", required = false) Long topologyNodeId,
      @RequestParam(value = "templateType", required = false) String templateType,
      @RequestParam(value = "resultStatus", required = false)
          FaultExecution.ResultStatus resultStatus,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

    logger.info(
        "GET /api/fault-executions - executionId: {}, topologyNodeId: {}, templateType: {},"
            + " resultStatus: {}, page: {}, size: {}",
        executionId,
        topologyNodeId,
        templateType,
        resultStatus,
        page,
        size);

    PageResponse<FaultExecution> executions =
        faultExecutionService.getFaultExecutions(
            executionId, topologyNodeId, templateType, resultStatus, page, size);
    return ApiResponse.success(executions);
  }

  /** 根据ID获取故障执行详情 GET /api/fault-executions/{executionId} */
  @GetMapping("/fault-executions/{executionId}")
  public ApiResponse<FaultExecution> getFaultExecutionById(@PathVariable Long executionId) {
    logger.info("GET /api/fault-executions/{}", executionId);

    FaultExecution execution = faultExecutionService.getFaultExecutionById(executionId);
    return ApiResponse.success(execution);
  }

  /** 创建新的故障执行记录 POST /api/fault-executions */
  @PostMapping("/fault-executions")
  public ApiResponse<FaultExecution> createFaultExecution(
      @Valid @RequestBody FaultExecution faultExecution) {
    logger.info(
        "POST /api/fault-executions - executionId: {}, topologyNodeId: {}, templateType: {}",
        faultExecution.getExecutionId(),
        faultExecution.getTopologyNodeId(),
        faultExecution.getTemplateType());

    FaultExecution createdExecution = faultExecutionService.createFaultExecution(faultExecution);
    return ApiResponse.success(createdExecution);
  }

  /** 删除故障执行记录 DELETE /api/fault-executions/{executionId} */
  @DeleteMapping("/fault-executions/{executionId}")
  public ApiResponse<Void> deleteFaultExecution(@PathVariable Long executionId) {
    logger.info("DELETE /api/fault-executions/{}", executionId);

    faultExecutionService.deleteFaultExecution(executionId);
    return ApiResponse.success(null);
  }
}
