package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.dto.*;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisResult;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisCacheService;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisExecutionService;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 命名空间分析API控制器
 */
@RestController
@RequestMapping("/api/ns-analysis")
@Validated
public class NsAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(NsAnalysisController.class);

    @Autowired
    private NsAnalysisPersistenceService persistenceService;

    @Autowired
    private NsAnalysisCacheService cacheService;

    @Autowired
    private NsAnalysisExecutionService executionService;

    /**
     * 创建分析任务
     *
     * 创建任务后会自动异步执行分析流程
     */
    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@Valid @RequestBody NsAnalysisRequest request) {
        logger.info("Creating analysis task for namespace: {}", request.getNamespace());

        NsAnalysisTask task = persistenceService.createTask(request);

        // 缓存任务状态
        Map<String, Object> status = buildTaskStatus(task);
        cacheService.cacheTaskStatus(task.getTaskId(), status);

        // 异步启动分析执行
        try {
            executionService.startExecution(task);
            logger.info("Analysis execution started for task: {}", task.getTaskId());
        } catch (Exception e) {
            logger.error("Failed to start execution for task: {}", task.getTaskId(), e);
            // 任务创建成功但执行启动失败，记录错误但不影响响应
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "分析任务创建成功，正在执行");
        response.put("data", NsAnalysisTaskDTO.fromEntity(task));

        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务状态
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String taskId) {
        // 先查缓存
        Optional<Map<String, Object>> cached = cacheService.getTaskStatus(taskId);
        if (cached.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", cached.get());
            response.put("source", "cache");
            return ResponseEntity.ok(response);
        }
        
        // 查数据库
        Optional<NsAnalysisTask> task = persistenceService.getTask(taskId);
        if (!task.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", NsAnalysisTaskDTO.fromEntity(task.get()));
        response.put("source", "database");
        return ResponseEntity.ok(response);
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        
        PageResponse<NsAnalysisTaskDTO> pageResult = persistenceService.queryTasks(namespace, status, page, size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", pageResult);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务进度
     */
    @GetMapping("/tasks/{taskId}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String taskId) {
        Optional<Map<String, Object>> progress = cacheService.getProgress(taskId);
        
        Map<String, Object> response = new HashMap<>();
        if (progress.isPresent()) {
            response.put("success", true);
            response.put("data", progress.get());
        } else {
            // 从数据库获取
            Optional<NsAnalysisTask> task = persistenceService.getTask(taskId);
            if (task.isPresent()) {
                NsAnalysisTask t = task.get();
                Map<String, Object> data = new HashMap<>();
                data.put("phase", t.getCurrentPhase());
                data.put("percent", t.getProgressPercent());
                data.put("status", t.getStatus().name());
                response.put("success", true);
                response.put("data", data);
            } else {
                response.put("success", false);
                response.put("message", "任务不存在");
                return ResponseEntity.status(404).body(response);
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 获取分析结果
     */
    @GetMapping("/results/{taskId}")
    public ResponseEntity<Map<String, Object>> getResult(@PathVariable String taskId) {
        // 先查缓存
        Optional<Map<String, Object>> cached = cacheService.getResult(taskId);
        if (cached.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", cached.get());
            response.put("source", "cache");
            return ResponseEntity.ok(response);
        }
        
        // 查数据库
        Optional<NsAnalysisResult> result = persistenceService.getResult(taskId);
        if (!result.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "结果不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", NsAnalysisResultDTO.fromEntity(result.get()));
        response.put("source", "database");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取命名空间最新结果
     */
    @GetMapping("/namespaces/{namespace}/latest")
    public ResponseEntity<Map<String, Object>> getLatestResult(@PathVariable String namespace) {
        // 先查缓存获取最新taskId
        Optional<String> latestTaskId = cacheService.getLatestTaskId(namespace);
        if (latestTaskId.isPresent()) {
            Optional<Map<String, Object>> cached = cacheService.getResult(latestTaskId.get());
            if (cached.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", cached.get());
                response.put("source", "cache");
                return ResponseEntity.ok(response);
            }
        }

        // 查数据库
        Optional<NsAnalysisResult> result = persistenceService.getLatestResult(namespace);
        if (!result.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "命名空间无分析结果: " + namespace);
            return ResponseEntity.status(404).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", NsAnalysisResultDTO.fromEntity(result.get()));
        response.put("source", "database");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务的风险分析列表
     *
     * 从ns_analysis_results表的ranked_services_summary字段获取
     */
    @GetMapping("/tasks/{taskId}/risks")
    public ResponseEntity<Map<String, Object>> getRisks(@PathVariable String taskId) {
        Optional<NsAnalysisResult> resultOpt = persistenceService.getResult(taskId);

        Map<String, Object> response = new HashMap<>();
        if (resultOpt.isPresent()) {
            NsAnalysisResult result = resultOpt.get();
            response.put("success", true);
            // 返回从结果中解析的风险摘要
            Object rankedSummary = null;
            try {
                if (result.getRankedServicesSummary() != null) {
                    rankedSummary = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(result.getRankedServicesSummary(), Object.class);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse ranked services summary", e);
            }
            response.put("data", rankedSummary);
        } else {
            response.put("success", true);
            response.put("data", new java.util.ArrayList<>());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务的Top N风险
     *
     * 从ns_analysis_results表获取风险排名
     */
    @GetMapping("/tasks/{taskId}/top-risks")
    public ResponseEntity<Map<String, Object>> getTopRisks(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topN) {

        Optional<NsAnalysisResult> resultOpt = persistenceService.getResult(taskId);

        Map<String, Object> response = new HashMap<>();
        if (resultOpt.isPresent()) {
            NsAnalysisResult result = resultOpt.get();
            response.put("success", true);
            // 返回Top N风险
            try {
                if (result.getRankedServicesSummary() != null) {
                    java.util.List<?> allRisks = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(result.getRankedServicesSummary(), java.util.List.class);
                    java.util.List<?> topRisks = allRisks.size() > topN ? allRisks.subList(0, topN) : allRisks;
                    response.put("data", topRisks);
                    response.put("total", topRisks.size());
                } else {
                    response.put("data", new java.util.ArrayList<>());
                    response.put("total", 0);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse ranked services", e);
                response.put("data", new java.util.ArrayList<>());
                response.put("total", 0);
            }
        } else {
            response.put("success", true);
            response.put("data", new java.util.ArrayList<>());
            response.put("total", 0);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        logger.info("Deleting task: {}", taskId);

        Optional<NsAnalysisTask> task = persistenceService.getTask(taskId);
        if (!task.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        persistenceService.deleteTask(taskId);
        cacheService.deleteTaskCache(taskId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "任务删除成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 取消任务
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        logger.info("Cancelling task: {}", taskId);

        Optional<NsAnalysisTask> task = persistenceService.getTask(taskId);
        if (!task.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        NsAnalysisTask t = task.get();
        if (t.getStatus() != NsAnalysisTask.TaskStatus.PENDING &&
            t.getStatus() != NsAnalysisTask.TaskStatus.RUNNING) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务状态不允许取消: " + t.getStatus());
            return ResponseEntity.status(400).body(response);
        }

        // 尝试取消正在执行的任务
        boolean executionCancelled = executionService.cancelExecution(taskId);
        if (executionCancelled) {
            logger.info("Execution cancellation requested for task: {}", taskId);
        }

        // 更新任务状态
        persistenceService.cancelTask(taskId);
        cacheService.updateProgress(taskId, t.getCurrentPhase() != null ? t.getCurrentPhase() : 0,
            t.getProgressPercent() != null ? t.getProgressPercent() : 0, "任务已取消");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "任务已取消");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildTaskStatus(NsAnalysisTask task) {
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", task.getTaskId());
        status.put("namespace", task.getNamespace());
        status.put("status", task.getStatus().name());
        status.put("progressPercent", task.getProgressPercent());
        status.put("createdAt", task.getCreatedAt());
        return status;
    }
}

