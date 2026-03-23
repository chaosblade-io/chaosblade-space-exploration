package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.dto.*;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisLog;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisResult;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import com.chaosblade.svc.k8sgraph.repository.NsAnalysisLogRepository;
import com.chaosblade.svc.k8sgraph.repository.NsAnalysisResultRepository;
import com.chaosblade.svc.k8sgraph.repository.NsAnalysisTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 命名空间分析持久化服务
 *
 * 对应实际数据库表:
 * - ns_analysis_tasks
 * - ns_analysis_results
 * - ns_analysis_phases
 * - ns_analysis_service_snapshots
 */
@Service
@Transactional
public class NsAnalysisPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(NsAnalysisPersistenceService.class);

    @Autowired
    private NsAnalysisTaskRepository taskRepository;

    @Autowired
    private NsAnalysisResultRepository resultRepository;

    @Autowired
    private NsAnalysisLogRepository logRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 创建分析任务
     */
    public NsAnalysisTask createTask(NsAnalysisRequest request) {
        NsAnalysisTask task = new NsAnalysisTask();
        task.setTaskId(generateTaskId());
        task.setNamespace(request.getNamespace());
        task.setSystemId(request.getSystemId());
        task.setTopN(request.getTopN() != null ? request.getTopN() : 5);
        task.setStatus(NsAnalysisTask.TaskStatus.PENDING);
        task.setTriggerType(NsAnalysisTask.TriggerType.API);
        task.setCreatedBy(request.getCreatedBy());
        task.setProgressPercent(0);

        if (request.getConfig() != null) {
            try {
                task.setConfig(objectMapper.writeValueAsString(request.getConfig()));
            } catch (Exception e) {
                logger.warn("Failed to serialize config", e);
            }
        }

        NsAnalysisTask saved = taskRepository.save(task);
        logger.info("Created analysis task: taskId={}, namespace={}", saved.getTaskId(), saved.getNamespace());
        return saved;
    }

    /**
     * 更新任务状态为运行中
     */
    public void startTask(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(NsAnalysisTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);
            logger.info("Task started: taskId={}", taskId);
        });
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int phase, int percent) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setCurrentPhase(phase);
            task.setProgressPercent(percent);
            taskRepository.save(task);
        });
    }

    /**
     * 完成任务
     */
    public void completeTask(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(NsAnalysisTask.TaskStatus.COMPLETED);
            task.setFinishedAt(LocalDateTime.now());
            task.setProgressPercent(100);
            if (task.getStartedAt() != null) {
                long duration = java.time.Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis();
                task.setTotalTimeMs(duration);
            }
            taskRepository.save(task);
            logger.info("Task completed: taskId={}, duration={}ms", taskId, task.getTotalTimeMs());
        });
    }

    /**
     * 任务失败
     */
    public void failTask(String taskId, String errorCode, String errorMessage) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(NsAnalysisTask.TaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorCode(errorCode);
            task.setErrorMessage(errorMessage);
            if (task.getStartedAt() != null) {
                long duration = java.time.Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis();
                task.setTotalTimeMs(duration);
            }
            taskRepository.save(task);
            logger.error("Task failed: taskId={}, error={}", taskId, errorMessage);
        });
    }

    /**
     * 取消任务
     */
    public void cancelTask(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(NsAnalysisTask.TaskStatus.CANCELLED);
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorCode("CANCELLED");
            task.setErrorMessage("任务被用户取消");
            if (task.getStartedAt() != null) {
                long duration = java.time.Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis();
                task.setTotalTimeMs(duration);
            }
            taskRepository.save(task);
            logger.info("Task cancelled: taskId={}", taskId);
        });
    }

    /**
     * 获取任务（通过taskId字符串）
     */
    @Transactional(readOnly = true)
    public Optional<NsAnalysisTask> getTask(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 获取任务（通过数据库主键ID）
     */
    @Transactional(readOnly = true)
    public Optional<NsAnalysisTask> getTaskByDbId(Long dbId) {
        return taskRepository.findById(dbId);
    }

    /**
     * 分页查询任务
     */
    @Transactional(readOnly = true)
    public PageResponse<NsAnalysisTaskDTO> queryTasks(String namespace, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        NsAnalysisTask.TaskStatus taskStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                taskStatus = NsAnalysisTask.TaskStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid task status: {}, ignoring status filter", status);
                // 无效的status参数，忽略该筛选条件
            }
        }
        Page<NsAnalysisTask> p = taskRepository.findByConditions(namespace, taskStatus, null, null, pageable);
        List<NsAnalysisTaskDTO> dtos = p.getContent().stream()
                .map(NsAnalysisTaskDTO::fromEntity)
                .collect(Collectors.toList());
        return PageResponse.of(dtos, p.getTotalElements(), page, size);
    }

    /**
     * 保存分析结果
     *
     * 对应 ns_analysis_results 表结构
     */
    public NsAnalysisResult saveResult(String taskId, Map<String, Object> resultData) {
        NsAnalysisTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        NsAnalysisResult result = new NsAnalysisResult();
        result.setTaskDbId(task.getId());  // 使用任务数据库主键ID
        result.setNamespace(task.getNamespace());
        result.setResultVersion("v1");

        // 设置摘要数据
        result.setServicesCount(getIntValue(resultData, "servicesCount", 0));
        result.setRulesTriggered(getIntValue(resultData, "rulesTriggered", 0));
        result.setCriticalCount(getIntValue(resultData, "criticalCount", 0));
        result.setMaxRiskScore(parseBigDecimal(resultData.get("maxRiskScore")));
        result.setTopRiskService((String) resultData.get("topRiskService"));
        result.setRiskLevel(parseRiskLevel((String) resultData.get("riskLevel")));

        // 序列化并存储完整结果数据
        try {
            String fullDataJson = objectMapper.writeValueAsString(resultData);
            byte[] jsonBytes = fullDataJson.getBytes(StandardCharsets.UTF_8);
            int originalSize = jsonBytes.length;
            result.setDataSizeBytes(originalSize);

            // 如果数据较大，压缩存储
            if (originalSize > 100 * 1024) { // > 100KB 时压缩
                byte[] compressed = gzipCompress(jsonBytes);
                result.setResultData(compressed);
                result.setCompressedSizeBytes(compressed.length);
                result.setIsCompressed(true);
                result.setCompressionAlgo("GZIP");
            } else {
                result.setResultData(jsonBytes);
                result.setIsCompressed(false);
            }
            result.setStorageType(NsAnalysisResult.StorageType.INLINE);

            // 存储服务排名摘要 (Top10)
            if (resultData.get("rankedServices") != null) {
                Object rankedServices = resultData.get("rankedServices");
                if (rankedServices instanceof List) {
                    List<?> list = (List<?>) rankedServices;
                    List<?> top10 = list.size() > 10 ? list.subList(0, 10) : list;
                    result.setRankedServicesSummary(objectMapper.writeValueAsString(top10));
                }
            }

            // 存储各阶段结果
            if (resultData.get("phase1Result") != null) {
                result.setPhase1Result(objectMapper.writeValueAsString(resultData.get("phase1Result")));
            }
            if (resultData.get("phase2Result") != null) {
                result.setPhase2Result(objectMapper.writeValueAsString(resultData.get("phase2Result")));
            }
            if (resultData.get("phase3Ranking") != null) {
                result.setPhase3Ranking(objectMapper.writeValueAsString(resultData.get("phase3Ranking")));
            }
            if (resultData.get("phase4Results") != null) {
                result.setPhase4Results(objectMapper.writeValueAsString(resultData.get("phase4Results")));
            }
            if (resultData.get("phase5Result") != null) {
                result.setPhase5Result(objectMapper.writeValueAsString(resultData.get("phase5Result")));
            }
            if (resultData.get("phaseTimings") != null) {
                result.setPhaseTimings(objectMapper.writeValueAsString(resultData.get("phaseTimings")));
            }
        } catch (Exception e) {
            logger.warn("Failed to serialize result data", e);
            result.setDataSizeBytes(0);
        }

        NsAnalysisResult saved = resultRepository.save(result);
        logger.info("Saved analysis result: taskId={}, dbId={}, resultId={}", taskId, task.getId(), saved.getId());
        return saved;
    }

    /**
     * 获取分析结果
     */
    @Transactional(readOnly = true)
    public Optional<NsAnalysisResult> getResult(String taskId) {
        Optional<NsAnalysisTask> task = taskRepository.findByTaskId(taskId);
        if (!task.isPresent()) {
            return Optional.empty();
        }
        return resultRepository.findByTaskDbId(task.get().getId());
    }

    /**
     * 获取命名空间最新结果
     */
    @Transactional(readOnly = true)
    public Optional<NsAnalysisResult> getLatestResult(String namespace) {
        return resultRepository.findFirstByNamespaceOrderByCreatedAtDesc(namespace);
    }

    /**
     * 删除任务及相关数据
     */
    public void deleteTask(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            resultRepository.deleteByTaskDbId(task.getId());
            taskRepository.delete(task);
        });
        logger.info("Deleted task and related data: taskId={}", taskId);
    }

    private String generateTaskId() {
        return "ns-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Integer getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private NsAnalysisResult.RiskLevel parseRiskLevel(String level) {
        if (level == null) return null;
        try {
            return NsAnalysisResult.RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析BigDecimal并四舍五入到两位小数
     */
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        BigDecimal result;
        if (value instanceof BigDecimal) {
            result = (BigDecimal) value;
        } else if (value instanceof Number) {
            result = BigDecimal.valueOf(((Number) value).doubleValue());
        } else {
            try {
                result = new BigDecimal(value.toString());
            } catch (Exception e) {
                return null;
            }
        }
        // 四舍五入到两位小数
        return result.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private byte[] gzipCompress(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * GZIP解压缩
     */
    private byte[] gzipDecompress(byte[] compressed) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }

    /**
     * 获取完整的结果数据（自动处理解压缩）
     *
     * @param result 分析结果实体
     * @return 解析后的完整结果Map，如果解析失败返回空Optional
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getFullResultData(NsAnalysisResult result) {
        if (result == null || result.getResultData() == null) {
            return Optional.empty();
        }

        try {
            byte[] data = result.getResultData();

            // 如果数据被压缩，先解压
            if (Boolean.TRUE.equals(result.getIsCompressed())) {
                logger.debug("Decompressing result data: originalSize={}, compressedSize={}",
                        result.getDataSizeBytes(), result.getCompressedSizeBytes());
                data = gzipDecompress(data);
            }

            // 转换为JSON字符串并解析
            String json = new String(data, StandardCharsets.UTF_8);
            Map<String, Object> resultMap = objectMapper.readValue(json, Map.class);

            logger.debug("Successfully parsed full result data: keys={}", resultMap.keySet());
            return Optional.of(resultMap);

        } catch (Exception e) {
            logger.error("Failed to get full result data: taskDbId={}, error={}",
                    result.getTaskDbId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 根据taskId获取完整的结果数据
     *
     * @param taskId 任务ID
     * @return 解析后的完整结果Map
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getFullResultData(String taskId) {
        Optional<NsAnalysisResult> result = getResult(taskId);
        if (!result.isPresent()) {
            return Optional.empty();
        }
        return getFullResultData(result.get());
    }

    /**
     * 获取指定阶段的结果数据
     *
     * @param taskId 任务ID
     * @param phase 阶段号 (1-5)
     * @return 阶段结果数据
     */
    @Transactional(readOnly = true)
    public Optional<Object> getPhaseResult(String taskId, int phase) {
        Optional<NsAnalysisResult> resultOpt = getResult(taskId);
        if (!resultOpt.isPresent()) {
            return Optional.empty();
        }

        NsAnalysisResult result = resultOpt.get();
        String jsonData = null;

        switch (phase) {
            case 1:
                jsonData = result.getPhase1Result();
                break;
            case 2:
                jsonData = result.getPhase2Result();
                break;
            case 3:
                jsonData = result.getPhase3Ranking();
                break;
            case 4:
                jsonData = result.getPhase4Results();
                break;
            case 5:
                jsonData = result.getPhase5Result();
                break;
            default:
                logger.warn("Invalid phase number: {}", phase);
                return Optional.empty();
        }

        if (jsonData == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(jsonData, Object.class));
        } catch (Exception e) {
            logger.error("Failed to parse phase {} result: taskId={}", phase, taskId, e);
            return Optional.empty();
        }
    }

    // ==================== 日志相关方法 ====================

    /**
     * 添加日志
     */
    public NsAnalysisLog addLog(String taskId, int level, String message) {
        NsAnalysisLog log = new NsAnalysisLog(taskId, level, message);
        return logRepository.save(log);
    }

    /**
     * 添加带阶段的日志
     */
    public NsAnalysisLog addLog(String taskId, int level, int phase, String message) {
        NsAnalysisLog log = new NsAnalysisLog(taskId, level, phase, message);
        return logRepository.save(log);
    }

    /**
     * 添加带详情的日志
     */
    public NsAnalysisLog addLog(String taskId, int level, int phase, String message, String details, Long durationMs) {
        NsAnalysisLog log = new NsAnalysisLog(taskId, level, phase, message);
        log.setDetails(details);
        log.setDurationMs(durationMs);
        return logRepository.save(log);
    }

    /**
     * 获取任务的所有日志
     */
    @Transactional(readOnly = true)
    public List<NsAnalysisLog> getLogs(String taskId) {
        return logRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    /**
     * 分页获取任务日志
     */
    @Transactional(readOnly = true)
    public Page<NsAnalysisLog> getLogs(String taskId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return logRepository.findByTaskIdOrderByCreatedAtAsc(taskId, pageable);
    }

    /**
     * 获取任务的日志（按最低级别过滤）
     */
    @Transactional(readOnly = true)
    public List<NsAnalysisLog> getLogs(String taskId, int minLevel) {
        return logRepository.findByTaskIdAndMinLevel(taskId, minLevel);
    }

    /**
     * 获取任务指定阶段的日志
     */
    @Transactional(readOnly = true)
    public List<NsAnalysisLog> getLogsByPhase(String taskId, int phase) {
        return logRepository.findByTaskIdAndPhaseOrderByCreatedAtAsc(taskId, phase);
    }

    /**
     * 获取任务的错误日志数量
     */
    @Transactional(readOnly = true)
    public long countErrors(String taskId) {
        return logRepository.countErrorsByTaskId(taskId);
    }

    /**
     * 删除任务的所有日志
     */
    public void deleteLogs(String taskId) {
        logRepository.deleteByTaskId(taskId);
    }
}

