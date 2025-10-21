package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.TaskSlo;
import com.chaosblade.svc.taskresource.service.TaskSloService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TaskSloController {

  private static final Logger logger = LoggerFactory.getLogger(TaskSloController.class);

  @Autowired private TaskSloService service;

  @GetMapping("/task-slos")
  public ApiResponse<PageResponse<TaskSlo>> list(
      @RequestParam(value = "p95", required = false) Integer p95,
      @RequestParam(value = "p99", required = false) Integer p99,
      @RequestParam(value = "errRate", required = false) Integer errRate,
      @RequestParam(value = "taskId", required = false) Long taskId,
      @RequestParam(value = "nodeId", required = false) Long nodeId,
      @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
    logger.info(
        "GET /api/task-slos - p95: {}, p99: {}, errRate: {}, taskId: {}, nodeId: {}, page: {},"
            + " size: {}",
        p95,
        p99,
        errRate,
        taskId,
        nodeId,
        page,
        size);
    return ApiResponse.success(service.pageQuery(p95, p99, errRate, taskId, nodeId, page, size));
  }

  @GetMapping("/task-slos/{id}")
  public ApiResponse<TaskSlo> get(@PathVariable Long id) {
    logger.info("GET /api/task-slos/{}", id);
    return ApiResponse.success(service.getById(id));
  }

  @PostMapping("/task-slos")
  public ApiResponse<TaskSlo> create(@Valid @RequestBody TaskSlo slo) {
    logger.info(
        "POST /api/task-slos - p95: {}, p99: {}, errRate: {}, taskId: {}, nodeId: {}",
        slo.getP95(),
        slo.getP99(),
        slo.getErrRate(),
        slo.getTaskId(),
        slo.getNodeId());
    return ApiResponse.success(service.create(slo));
  }

  @PutMapping("/task-slos/{id}")
  public ApiResponse<TaskSlo> update(@PathVariable Long id, @Valid @RequestBody TaskSlo slo) {
    logger.info("PUT /api/task-slos/{}", id);
    return ApiResponse.success(service.update(id, slo));
  }

  @DeleteMapping("/task-slos/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    logger.info("DELETE /api/task-slos/{}", id);
    service.delete(id);
    return ApiResponse.success();
  }
}
