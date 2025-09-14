package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.FaultConfig;
import com.chaosblade.svc.taskresource.service.FaultConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FaultConfigController {

    private static final Logger logger = LoggerFactory.getLogger(FaultConfigController.class);

    @Autowired
    private FaultConfigService service;

    // 使用不与旧控制器冲突的路径前缀
    @GetMapping("/task-fault-configs")
    public ApiResponse<PageResponse<FaultConfig>> list(
            @RequestParam(value = "nodeId", required = false) Long nodeId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("GET /api/task-fault-configs - nodeId: {}, type: {}, taskId: {}, page: {}, size: {}",
                nodeId, type, taskId, page, size);
        return ApiResponse.success(service.pageQuery(nodeId, type, taskId, page, size));
    }

    @GetMapping("/task-fault-configs/{id}")
    public ApiResponse<FaultConfig> get(@PathVariable Long id) {
        logger.info("GET /api/task-fault-configs/{}", id);
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping("/task-fault-configs")
    public ApiResponse<FaultConfig> create(@Valid @RequestBody FaultConfig cfg) {
        logger.info("POST /api/task-fault-configs - nodeId: {}, taskId: {}", cfg.getNodeId(), cfg.getTaskId());
        return ApiResponse.success(service.create(cfg));
    }

    @PutMapping("/task-fault-configs/{id}")
    public ApiResponse<FaultConfig> update(@PathVariable Long id, @Valid @RequestBody FaultConfig cfg) {
        logger.info("PUT /api/task-fault-configs/{}", id);
        return ApiResponse.success(service.update(id, cfg));
    }

    @DeleteMapping("/task-fault-configs/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/task-fault-configs/{}", id);
        service.delete(id);
        return ApiResponse.success();
    }
}

