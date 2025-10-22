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
import com.chaosblade.svc.taskresource.dto.ProbeTaskDtos;
import com.chaosblade.svc.taskresource.service.ProbeTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProbeTaskController {

  private static final Logger logger = LoggerFactory.getLogger(ProbeTaskController.class);
  private final ProbeTaskService service;

  public ProbeTaskController(ProbeTaskService service) {
    this.service = service;
  }

  @PostMapping({"/probe-tasks", "/detection-tasks"})
  public ApiResponse<ProbeTaskDtos.ProbeTaskCreateResponse> create(
      @RequestBody ProbeTaskDtos.ProbeTaskCreateRequest req) {
    logger.info(
        "POST /api/probe-tasks name={}, systemId={}, apiId={}, requestNum={}",
        req.name,
        req.systemId,
        req.apiId,
        req.requestNum);
    var resp = service.createProbeTask(req);
    return ApiResponse.success(resp);
  }
}
