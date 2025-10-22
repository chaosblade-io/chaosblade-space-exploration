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

package com.chaosblade.svc.taskexecutor.web;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskexecutor.entity.TaskExecutionLog;
import com.chaosblade.svc.taskexecutor.service.TaskExecutionLogService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/executions")
public class TaskExecutionLogController {

  private final TaskExecutionLogService logService;

  public TaskExecutionLogController(TaskExecutionLogService logService) {
    this.logService = logService;
  }

  public static class LogView {
    public LocalDateTime ts;
    public String level;
    public String message;
  }

  @GetMapping("/{executionId}/logs")
  public ApiResponse<PageResponse<LogView>> list(
      @PathVariable("executionId") Long executionId,
      @RequestParam(value = "level", required = false) String level,
      @RequestParam(value = "page", defaultValue = "1") Integer page,
      @RequestParam(value = "size", defaultValue = "100") Integer size) {
    Integer minLevel = null;
    if (level != null && !level.isBlank()) {
      try {
        TaskExecutionLog.LogLevel lv =
            TaskExecutionLog.LogLevel.valueOf(level.trim().toUpperCase());
        minLevel = lv.getValue();
      } catch (Exception ignore) {
        // 非法 level 参数时忽略，返回全量
      }
    }
    Page<TaskExecutionLog> p = logService.getLogs(executionId, minLevel, page, size);
    List<LogView> items =
        p.getContent().stream()
            .map(
                l -> {
                  LogView v = new LogView();
                  v.ts = l.getTs();
                  v.level = l.getLogLevel().name();
                  v.message = l.getMessage();
                  return v;
                })
            .collect(Collectors.toList());
    PageResponse<LogView> resp = PageResponse.of(items, p.getTotalElements(), page, size);
    return ApiResponse.ok(resp);
  }
}
