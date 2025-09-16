package com.chaosblade.svc.taskresource.controller;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.svc.taskresource.entity.DetectionTask;
import com.chaosblade.svc.taskresource.service.DetectionTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.chaosblade.svc.taskresource.config.EndpointsProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

/**
 * 检测任务管理控制器
 */
@RestController
@RequestMapping("/api")
public class DetectionTaskController {
    
    private static final Logger logger = LoggerFactory.getLogger(DetectionTaskController.class);
    
    @Autowired
    private DetectionTaskService detectionTaskService;

    @Autowired
    private EndpointsProperties endpointsProperties;

    /**
     * 获取检测任务列表
     * GET /api/detection-tasks
     */
    @GetMapping("/detection-tasks")
    public ApiResponse<PageResponse<DetectionTask>> getDetectionTasks(
            @RequestParam(value = "systemId", required = false) Long systemId,
            @RequestParam(value = "apiId", required = false) Long apiId,
            @RequestParam(value = "faultConfigurationsId", required = false) Long faultConfigurationsId,
            @RequestParam(value = "sloId", required = false) Long sloId,
            @RequestParam(value = "createdBy", required = false) String createdBy,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

        logger.info("GET /api/detection-tasks - systemId: {}, apiId: {}, faultConfigurationsId: {}, " +
                   "sloId: {}, createdBy: {}, name: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                   systemId, apiId, faultConfigurationsId, sloId, createdBy, name, startDate, endDate, page, size);

        PageResponse<DetectionTask> tasks = detectionTaskService.getDetectionTasks(
                systemId, apiId, faultConfigurationsId, sloId, createdBy, name, startDate, endDate, page, size);
        return ApiResponse.success(tasks);
    }
    
    /**
     * 获取检测任务详情（增强版，聚合）
     * GET /api/detection-tasks/{taskId}
     */
    @GetMapping("/detection-tasks/{taskId}")
    public ApiResponse<com.chaosblade.svc.taskresource.dto.DetectionTaskDtos.DetectionTaskDetails> getDetectionTaskDetails(@PathVariable Long taskId) {
        logger.info("GET /api/detection-tasks/{}", taskId);
        var details = detectionTaskService.getDetectionTaskDetails(taskId);
        return ApiResponse.success(details);
    }

    /**
     * 根据名称获取检测任务详情
     * GET /api/detection-tasks/by-name/{name}
     */
    @GetMapping("/detection-tasks/by-name/{name}")
    public ApiResponse<DetectionTask> getDetectionTaskByName(@PathVariable String name) {
        logger.info("GET /api/detection-tasks/by-name/{}", name);
        
        DetectionTask task = detectionTaskService.getDetectionTaskByName(name);
        return ApiResponse.success(task);
    }
    
    /**
     * 创建新检测任务
     * POST /api/detection-tasks
     */
    @PostMapping("/detection-tasks")
    public ApiResponse<DetectionTask> createDetectionTask(@Valid @RequestBody DetectionTask detectionTask) {
        logger.info("POST /api/detection-tasks - name: {}", detectionTask.getName());
        
        DetectionTask createdTask = detectionTaskService.createDetectionTask(detectionTask);
        return ApiResponse.success(createdTask);
    }
    
    /**
     * 更新检测任务信息
     * PUT /api/detection-tasks/{taskId}
     */
    @PutMapping("/detection-tasks/{taskId}")
    public ApiResponse<DetectionTask> updateDetectionTask(@PathVariable Long taskId,
                                                         @Valid @RequestBody DetectionTask detectionTask) {
        logger.info("PUT /api/detection-tasks/{}", taskId);
        
        DetectionTask updatedTask = detectionTaskService.updateDetectionTask(taskId, detectionTask);
        return ApiResponse.success(updatedTask);
    }
    
    /**
     * 部分更新检测任务信息
     * PATCH /api/detection-tasks/{taskId}
     */
    @PatchMapping("/detection-tasks/{taskId}")
    public ApiResponse<DetectionTask> patchDetectionTask(@PathVariable Long taskId,
                                                        @RequestBody DetectionTask detectionTask) {
        logger.info("PATCH /api/detection-tasks/{}", taskId);
        
        DetectionTask updatedTask = detectionTaskService.updateDetectionTask(taskId, detectionTask);
        return ApiResponse.success(updatedTask);
    }
    
    /**
     * 删除检测任务
     * DELETE /api/detection-tasks/{taskId}
     */
    @DeleteMapping("/detection-tasks/{taskId}")
    public ApiResponse<Void> deleteDetectionTask(@PathVariable Long taskId) {
        logger.info("DELETE /api/detection-tasks/{}", taskId);
        
        detectionTaskService.deleteDetectionTask(taskId);
        return ApiResponse.success();
    }
    
    /**
     * 执行检测任务（代理转发）
     * POST /api/detection-tasks/{taskId}/execute
     * 代理到 svc-task-executor: http://localhost:8102/api/tasks/{taskId}/execute
     * 说明：该接口保持请求/响应原样透传，不做业务逻辑处理
     */
    @PostMapping("/detection-tasks/{taskId}/execute")
    public org.springframework.http.ResponseEntity<String> executeDetectionTask(@PathVariable Long taskId,
                                                                               @RequestBody(required = false) String body,
                                                                               @RequestHeader java.util.Map<String, String> headers) {
        String url = endpointsProperties.getExecutorBaseUrl() + "/api/tasks/" + taskId + "/execute";
        logger.info("[PROXY] POST /api/detection-tasks/{}/execute -> {}", taskId, url);
        try {
            // 配置超时时间，避免连接悬挂导致客户端报 Premature EOF
            var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            f.setConnectTimeout(50000);
            f.setReadTimeout(300000);
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate(f);
            // 不让 RestTemplate 在 4xx/5xx 时抛异常，保持上游状态码原样透传
            rt.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response) { return false; }
                @Override public void handleError(org.springframework.http.client.ClientHttpResponse response) { /* no-op */ }
            });

            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            // 透传请求头（剔除 hop-by-hop 头，避免跨服务/代理问题）
            headers.forEach((k,v) -> {
                String key = k == null ? null : k.trim();
                if (key == null || key.isEmpty()) return;
                if (org.springframework.http.HttpHeaders.HOST.equalsIgnoreCase(key)) return;
                if (org.springframework.http.HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) return;
                if (org.springframework.http.HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(key)) return;
                if (org.springframework.http.HttpHeaders.CONNECTION.equalsIgnoreCase(key)) return;
                if ("Keep-Alive".equalsIgnoreCase(key)) return;
                if ("Proxy-Connection".equalsIgnoreCase(key)) return;
                h.add(key, v);
            });
            // 确保 Content-Type 存在
            if (!h.containsKey(org.springframework.http.HttpHeaders.CONTENT_TYPE)) {
                h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            }
            var req = new org.springframework.http.HttpEntity<>(body, h);
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(url, req, String.class);
            logger.info("[PROXY] executor responded: status={} length={}", resp.getStatusCode(), (resp.getBody()==null?0:resp.getBody().length()));
            //
            org.springframework.http.HttpHeaders outH = new org.springframework.http.HttpHeaders();
            resp.getHeaders().forEach((k, vals) -> {
                String key = k == null ? null : k.trim();
                if (key == null || key.isEmpty()) return;
                if (org.springframework.http.HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(key)) return;
                if (org.springframework.http.HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) return;
                if (org.springframework.http.HttpHeaders.CONNECTION.equalsIgnoreCase(key)) return;
                if ("Keep-Alive".equalsIgnoreCase(key)) return;
                if ("Proxy-Connection".equalsIgnoreCase(key)) return;
                outH.put(key, vals);
            });
            return org.springframework.http.ResponseEntity.status(resp.getStatusCode()).headers(outH).body(resp.getBody());
        } catch (Exception ex) {
            logger.error("[PROXY] forward execute failed: taskId={} - {}", taskId, ex.getMessage(), ex);
            // 返回 502 Bad Gateway 更贴近代理失败语义
            return org.springframework.http.ResponseEntity.status(502)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"error\":{\"code\":\"EXECUTOR_PROXY_FAILED\",\"message\":\"" +
                            ex.getClass().getSimpleName() + ": " + (ex.getMessage()==null?"":ex.getMessage().replace("\"","'")) + "\"}}\n");
        }
    }

    /**
     * 取消检测任务
     * POST /api/detection-tasks/{taskId}/cancel
     */
    @PostMapping("/detection-tasks/{taskId}/cancel")
    public ApiResponse<DetectionTask> cancelDetectionTask(@PathVariable Long taskId) {
        logger.info("POST /api/detection-tasks/{}/cancel", taskId);
        
        DetectionTask task = detectionTaskService.cancelDetectionTask(taskId);
        return ApiResponse.success(task);
    }
    
    /**
     * 获取活跃任务列表（未归档）
     * GET /api/detection-tasks/active
     */
    @GetMapping("/detection-tasks/active")
    public ApiResponse<PageResponse<DetectionTask>> getActiveTasks(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("GET /api/detection-tasks/active - page: {}, size: {}", page, size);

        PageResponse<DetectionTask> tasks = detectionTaskService.getActiveTasks(page, size);
        return ApiResponse.success(tasks);
    }

    /**
     * 获取已归档任务列表
     * GET /api/detection-tasks/archived
     */
    @GetMapping("/detection-tasks/archived")
    public ApiResponse<PageResponse<DetectionTask>> getArchivedTasks(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("GET /api/detection-tasks/archived - page: {}, size: {}", page, size);

        PageResponse<DetectionTask> tasks = detectionTaskService.getArchivedTasks(page, size);
        return ApiResponse.success(tasks);
    }

    /**
     * 任务执行历史（分页）
     * GET /api/detection-tasks/{taskId}/executions
     */
    @GetMapping("/detection-tasks/{taskId}/executions")
    public ApiResponse<PageResponse<com.chaosblade.svc.taskresource.entity.TaskExecution>> getExecutions(
            @PathVariable Long taskId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("GET /api/detection-tasks/{}/executions - page: {}, size: {}", taskId, page, size);
        var result = detectionTaskService.getTaskExecutions(taskId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 分页查询所有任务执行记录（支持筛选）
     * GET /api/task-executions 或 /api/executions
     */
    @GetMapping({"/task-executions", "/executions"})
    public ApiResponse<PageResponse<com.chaosblade.svc.taskresource.dto.DetectionTaskDtos.TaskExecutionView>> listExecutions(
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") java.time.LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") java.time.LocalDateTime endDate,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        logger.info("GET /api/task-executions - taskId={}, status={}, namespace={}, startDate={}, endDate={}, page={}, size={}",
                taskId, status, namespace, startDate, endDate, page, size);
        var resp = detectionTaskService.getExecutions(taskId, status, namespace, startDate, endDate, page, size);
        return ApiResponse.success(resp);
    }

    /**
     * 获取任务执行详情（增强版）- 新路径，去除 taskId
     * GET /api/task-executions/{executionId}
     * 同时提供 /api/executions/{executionId} 的别名路径
     */
    @GetMapping({"/task-executions/{executionId}", "/executions/{executionId}"})
    public ApiResponse<com.chaosblade.svc.taskresource.dto.ExecutionDetailsDto> getExecutionDetailsNew(
            @PathVariable Long executionId) {
        logger.info("GET /api/task-executions/{}", executionId);
        var details = detectionTaskService.getExecutionDetailsByExecutionId(executionId);
        // 按需裁剪返回：
        // 1) 不返回 llmSummary
        // 2) 过滤掉指定 CaseType 的测试用例（当前为 BASELINE，后续如需改为 SINGLE/DUAL 可调整）
        try {
            if (details != null) {
                details.llmSummary = null; // 不返回 llmSummary
                if (details.testCases != null) {
                    java.util.List<com.chaosblade.svc.taskresource.dto.ExecutionDetailsDto.TestCaseItem> filtered = new java.util.ArrayList<>();
                    for (var t : details.testCases) {
                        if (t == null) continue;
                        // 过滤规则：去掉 CaseType == BASELINE 的用例
                        if (t.caseType != null && t.caseType.equalsIgnoreCase("BASELINE")) continue;
                        filtered.add(t);
                    }
                    details.testCases = filtered;
                }
            }
        } catch (Exception ignore) { /* no-op */ }
        return ApiResponse.success(details);
    }

    /**
     * 获取任务执行详情（增强版）- 旧路径，保留一段时间用于兼容
     * GET /api/detection-tasks/{taskId}/executions/{executionId}
     */
    @Deprecated
    @GetMapping("/detection-tasks/{taskId}/executions/{executionId}")
    public ApiResponse<com.chaosblade.svc.taskresource.dto.ExecutionDetailsDto> getExecutionDetailsDeprecated(
            @PathVariable Long taskId,
            @PathVariable Long executionId) {
        logger.warn("[DEPRECATED] GET /api/detection-tasks/{}/executions/{}", taskId, executionId);
        var details = detectionTaskService.getExecutionDetails(taskId, executionId);
        return ApiResponse.success(details);
    }
}
