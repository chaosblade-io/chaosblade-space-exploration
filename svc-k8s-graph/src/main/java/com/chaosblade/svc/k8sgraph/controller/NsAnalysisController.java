package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.dto.*;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisResult;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisCacheService;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisExecutionService;
import com.chaosblade.svc.k8sgraph.service.NsAnalysisPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * 获取任务详情
     *
     * 返回完整的任务信息，包括：
     * - 基本信息：taskId, namespace, systemId, topN, createdBy
     * - 状态信息：status, triggerType, currentPhase, progressPercent
     * - 时间信息：createdAt, startedAt, finishedAt, updatedAt, totalTimeMs
     * - 错误信息：errorCode, errorMessage
     *
     * 注意：始终从数据库获取最新数据，确保返回完整的任务信息
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String taskId) {
        // 直接从数据库获取最新数据，确保信息完整性
        Optional<NsAnalysisTask> task = persistenceService.getTask(taskId);
        if (!task.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", buildTaskStatus(task.get()));
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
     *
     * 返回完整的进度信息：
     * - percent: 整体进度百分比 (0-100)
     * - currentPhase: 当前执行阶段 (1-6)
     * - status: 任务状态
     * - totalTimeMs: 总耗时(毫秒)
     * - phases: 各阶段详情数组
     *   - phase: 阶段号
     *   - name: 阶段名称
     *   - status: 阶段状态 (pending/running/completed/failed)
     *   - durationMs: 阶段耗时(毫秒)
     */
    @GetMapping("/tasks/{taskId}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String taskId) {
        Map<String, Object> response = new HashMap<>();

        // 从数据库获取任务信息
        Optional<NsAnalysisTask> taskOpt = persistenceService.getTask(taskId);
        if (!taskOpt.isPresent()) {
            response.put("success", false);
            response.put("message", "任务不存在");
            return ResponseEntity.status(404).body(response);
        }

        NsAnalysisTask task = taskOpt.get();
        Map<String, Object> data = buildProgressData(task);

        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    /**
     * 构建完整的进度数据
     */
    private Map<String, Object> buildProgressData(NsAnalysisTask task) {
        Map<String, Object> data = new HashMap<>();

        // 基础进度信息
        data.put("percent", task.getProgressPercent() != null ? task.getProgressPercent() : 0);
        data.put("currentPhase", task.getCurrentPhase() != null ? task.getCurrentPhase() : 0);
        data.put("status", task.getStatus() != null ? task.getStatus().name() : "PENDING");
        data.put("totalTimeMs", task.getTotalTimeMs());

        // 阶段名称定义
        String[] phaseNames = {
            "规则扫描",
            "拓扑风险分析",
            "风险排名计算",
            "Trace深度分析",
            "综合分析与场景生成",
            "配置生成"
        };

        // 获取各阶段耗时数据
        Map<Integer, Long> phaseTimingsMap = getPhaseTimingsMap(task.getTaskId());

        // 构建阶段详情数组
        List<Map<String, Object>> phases = new ArrayList<>();
        int currentPhase = task.getCurrentPhase() != null ? task.getCurrentPhase() : 0;
        boolean isCompleted = task.getStatus() == NsAnalysisTask.TaskStatus.COMPLETED;
        boolean isFailed = task.getStatus() == NsAnalysisTask.TaskStatus.FAILED;

        for (int i = 1; i <= 6; i++) {
            Map<String, Object> phaseInfo = new HashMap<>();
            phaseInfo.put("phase", i);
            phaseInfo.put("name", phaseNames[i - 1]);

            // 判断阶段状态
            String phaseStatus;
            if (i < currentPhase || (i == currentPhase && isCompleted)) {
                phaseStatus = "completed";
            } else if (i == currentPhase && isFailed) {
                phaseStatus = "failed";
            } else if (i == currentPhase) {
                phaseStatus = "running";
            } else {
                phaseStatus = "pending";
            }
            phaseInfo.put("status", phaseStatus);

            // 阶段耗时
            Long durationMs = phaseTimingsMap.get(i);
            phaseInfo.put("durationMs", durationMs);

            phases.add(phaseInfo);
        }

        data.put("phases", phases);
        return data;
    }

    /**
     * 从结果表获取各阶段耗时数据
     */
    private Map<Integer, Long> getPhaseTimingsMap(String taskId) {
        Map<Integer, Long> timingsMap = new HashMap<>();
        try {
            Optional<NsAnalysisResult> resultOpt = persistenceService.getResult(taskId);
            if (resultOpt.isPresent() && resultOpt.get().getPhaseTimings() != null) {
                String phaseTimingsJson = resultOpt.get().getPhaseTimings();
                // 解析JSON格式的阶段耗时: {"1": 8200, "2": 5100, ...}
                @SuppressWarnings("unchecked")
                Map<String, Object> timings = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(phaseTimingsJson, Map.class);
                for (Map.Entry<String, Object> entry : timings.entrySet()) {
                    try {
                        int phase = Integer.parseInt(entry.getKey());
                        long duration = entry.getValue() instanceof Number
                                ? ((Number) entry.getValue()).longValue()
                                : Long.parseLong(entry.getValue().toString());
                        timingsMap.put(phase, duration);
                    } catch (NumberFormatException e) {
                        // 忽略无效的键
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse phase timings for task: {}", taskId, e);
        }
        return timingsMap;
    }

    /**
     * 获取分析结果（完整结果，包含所有阶段数据）
     *
     * @param taskId 任务ID
     * @param fullData 是否返回完整的压缩数据（默认false，只返回各阶段结果）
     */
    @GetMapping("/results/{taskId}")
    public ResponseEntity<Map<String, Object>> getResult(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "false") boolean fullData) {

        Map<String, Object> response = new HashMap<>();

        // 先查缓存（缓存中有完整的各阶段结果）
        Optional<Map<String, Object>> cached = cacheService.getResult(taskId);
        if (cached.isPresent()) {
            response.put("success", true);
            response.put("data", cached.get());
            response.put("source", "cache");
            return ResponseEntity.ok(response);
        }

        // 查数据库
        Optional<NsAnalysisResult> resultOpt = persistenceService.getResult(taskId);
        if (!resultOpt.isPresent()) {
            response.put("success", false);
            response.put("message", "结果不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        NsAnalysisResult result = resultOpt.get();

        // 构建完整的返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("namespace", result.getNamespace());

        // 基本信息
        data.put("servicesCount", result.getServicesCount());
        data.put("rulesTriggered", result.getRulesTriggered());
        data.put("criticalCount", result.getCriticalCount());
        data.put("maxRiskScore", result.getMaxRiskScore());
        data.put("topRiskService", result.getTopRiskService());
        data.put("riskLevel", result.getRiskLevel() != null ? result.getRiskLevel().name() : null);
        data.put("createdAt", result.getCreatedAt());

        // 各阶段结果（从LONGTEXT字段解析）
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            if (result.getPhase1Result() != null) {
                data.put("phase1Result", objectMapper.readValue(result.getPhase1Result(), Object.class));
            }
            if (result.getPhase2Result() != null) {
                data.put("phase2Result", objectMapper.readValue(result.getPhase2Result(), Object.class));
            }
            if (result.getPhase3Ranking() != null) {
                data.put("phase3Result", objectMapper.readValue(result.getPhase3Ranking(), Object.class));
            }
            if (result.getPhase4Results() != null) {
                data.put("phase4Results", objectMapper.readValue(result.getPhase4Results(), Object.class));
            }
            if (result.getPhase5Result() != null) {
                data.put("phase5Result", objectMapper.readValue(result.getPhase5Result(), Object.class));
            }
            if (result.getPhaseTimings() != null) {
                data.put("phaseTimings", objectMapper.readValue(result.getPhaseTimings(), Object.class));
            }
            if (result.getRankedServicesSummary() != null) {
                data.put("rankedServicesSummary", objectMapper.readValue(result.getRankedServicesSummary(), Object.class));
            }

            // 如果请求完整数据，从压缩的resultData中解压
            if (fullData) {
                Optional<Map<String, Object>> fullResultData = persistenceService.getFullResultData(result);
                if (fullResultData.isPresent()) {
                    data.put("fullResultData", fullResultData.get());
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to parse result data for taskId: {}", taskId, e);
        }

        response.put("success", true);
        response.put("data", data);
        response.put("source", "database");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取命名空间最新结果（完整结果）
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
        Optional<NsAnalysisResult> resultOpt = persistenceService.getLatestResult(namespace);
        if (!resultOpt.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "命名空间无分析结果: " + namespace);
            return ResponseEntity.status(404).body(response);
        }

        // 复用getResult的完整数据返回逻辑
        NsAnalysisResult result = resultOpt.get();
        Optional<NsAnalysisTask> taskOpt = persistenceService.getTaskByDbId(result.getTaskDbId());
        if (taskOpt.isPresent()) {
            return getResult(taskOpt.get().getTaskId(), false);
        }

        // 回退：返回DTO
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", NsAnalysisResultDTO.fromEntity(result));
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

    /**
     * 构建完整的任务状态信息
     * 用于缓存和API响应，确保返回所有详情页需要的字段
     */
    private Map<String, Object> buildTaskStatus(NsAnalysisTask task) {
        Map<String, Object> status = new HashMap<>();
        // 基本信息
        status.put("id", task.getId());
        status.put("taskId", task.getTaskId());
        status.put("namespace", task.getNamespace());
        status.put("systemId", task.getSystemId());
        status.put("topN", task.getTopN());
        status.put("createdBy", task.getCreatedBy());

        // 状态信息
        status.put("status", task.getStatus() != null ? task.getStatus().name() : null);
        status.put("triggerType", task.getTriggerType() != null ? task.getTriggerType().name() : null);
        status.put("currentPhase", task.getCurrentPhase());
        status.put("progressPercent", task.getProgressPercent());

        // 时间信息 - 格式化为字符串
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        status.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().format(formatter) : null);
        status.put("startedAt", task.getStartedAt() != null ? task.getStartedAt().format(formatter) : null);
        status.put("finishedAt", task.getFinishedAt() != null ? task.getFinishedAt().format(formatter) : null);
        status.put("updatedAt", task.getUpdatedAt() != null ? task.getUpdatedAt().format(formatter) : null);
        status.put("totalTimeMs", task.getTotalTimeMs());

        // 错误信息
        status.put("errorCode", task.getErrorCode());
        status.put("errorMessage", task.getErrorMessage());

        return status;
    }

    /**
     * 获取任务执行日志
     *
     * @param taskId 任务ID
     * @param minLevel 最低日志级别: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR (默认0)
     * @param phase 阶段过滤 (可选, 1-6)
     */
    @GetMapping("/tasks/{taskId}/logs")
    public ResponseEntity<Map<String, Object>> getTaskLogs(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(3) int minLevel,
            @RequestParam(required = false) Integer phase) {

        Map<String, Object> response = new HashMap<>();

        // 验证任务存在
        Optional<NsAnalysisTask> taskOpt = persistenceService.getTask(taskId);
        if (!taskOpt.isPresent()) {
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        List<?> logs;
        if (phase != null) {
            logs = persistenceService.getLogsByPhase(taskId, phase);
        } else if (minLevel > 0) {
            logs = persistenceService.getLogs(taskId, minLevel);
        } else {
            logs = persistenceService.getLogs(taskId);
        }

        response.put("success", true);
        response.put("taskId", taskId);
        response.put("logsCount", logs.size());
        response.put("errorCount", persistenceService.countErrors(taskId));
        response.put("logs", logs);
        return ResponseEntity.ok(response);
    }

    /**
     * 分页获取任务执行日志
     */
    @GetMapping("/tasks/{taskId}/logs/page")
    public ResponseEntity<Map<String, Object>> getTaskLogsPaged(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {

        Map<String, Object> response = new HashMap<>();

        Optional<NsAnalysisTask> taskOpt = persistenceService.getTask(taskId);
        if (!taskOpt.isPresent()) {
            response.put("success", false);
            response.put("message", "任务不存在: " + taskId);
            return ResponseEntity.status(404).body(response);
        }

        Page<?> logsPage = persistenceService.getLogs(taskId, page, size);

        response.put("success", true);
        response.put("taskId", taskId);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", logsPage.getTotalElements());
        response.put("totalPages", logsPage.getTotalPages());
        response.put("logs", logsPage.getContent());
        return ResponseEntity.ok(response);
    }
}

