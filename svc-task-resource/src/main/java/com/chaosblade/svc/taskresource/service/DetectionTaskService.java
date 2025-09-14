package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.dto.PageResponse;
import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.dto.DetectionTaskDtos;
import com.chaosblade.svc.taskresource.dto.ExecutionDetailsDto;
import com.chaosblade.svc.taskresource.entity.*;
import com.chaosblade.svc.taskresource.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 检测任务服务类
 */
@Service
@Transactional
public class DetectionTaskService {

    private static final Logger logger = LoggerFactory.getLogger(DetectionTaskService.class);

    @Autowired
    private DetectionTaskRepository detectionTaskRepository;

    @Autowired
    private FaultConfigurationService faultConfigurationService;

    @Autowired
    private SystemService systemService;

    @Autowired
    private ApiService apiService;

    @Autowired private SystemRepository systemRepository;
    @Autowired private HttpReqDefRepository httpReqDefRepository;
    @Autowired private FaultConfigRepository faultConfigRepository;
    @Autowired private TaskSloRepository taskSloRepository;
    @Autowired private ApiTopologyRepository apiTopologyRepository;
    @Autowired private ApiTopologyNodeRepository apiTopologyNodeRepository;
    @Autowired private ApiTopologyEdgeRepository apiTopologyEdgeRepository;
    @Autowired private TaskExecutionRepository taskExecutionRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private com.chaosblade.svc.taskresource.repository.TestResultRepository testResultRepository;
    @Autowired private com.chaosblade.svc.taskresource.repository.TaskExecutionLogRepository taskExecutionLogRepository;
    @Autowired private com.chaosblade.svc.taskresource.repository.TaskConclusionRepository taskConclusionRepository;

    /**
     * 获取检测任务列表
     */
    @Transactional(readOnly = true)
    public PageResponse<DetectionTask> getDetectionTasks(Long systemId, Long apiId, Long faultConfigurationsId,
                                                        Long sloId, String createdBy, String name,
                                                        LocalDateTime startDate, LocalDateTime endDate,
                                                        int page, int size) {
        logger.debug("Getting detection tasks with filters - systemId: {}, apiId: {}, faultConfigurationsId: {}, " +
                    "sloId: {}, createdBy: {}, name: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                    systemId, apiId, faultConfigurationsId, sloId, createdBy, name, startDate, endDate, page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DetectionTask> taskPage = detectionTaskRepository.findByConditions(
                faultConfigurationsId, systemId, apiId, sloId, createdBy, name, startDate, endDate, pageable);

        return PageResponse.of(taskPage.getContent(), taskPage.getTotalElements(), page, size);
    }

    /**
     * 根据ID获取检测任务（旧方法）
     */
    @Transactional(readOnly = true)
    public DetectionTask getDetectionTaskById(Long taskId) {
        logger.debug("Getting detection task by id: {}", taskId);
        return detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + taskId));
    }

    /**
     * 根据ID获取检测任务详情（聚合）
     */
    @Transactional(readOnly = true)
    public DetectionTaskDtos.DetectionTaskDetails getDetectionTaskDetails(Long taskId) {
        logger.debug("Getting detection task details by id: {}", taskId);
        DetectionTask task = getDetectionTaskById(taskId);
        DetectionTaskDtos.DetectionTaskDetails details = new DetectionTaskDtos.DetectionTaskDetails(task);
        // 系统信息
        details.sys = systemRepository.findById(task.getSystemId()).orElse(null);
        // API 请求定义：优先根据 apiId 选取最新创建的 HttpReqDef（若有多个版本）
        details.apiDefinition = (task.getApiId() != null)
                ? httpReqDefRepository.findTop1ByApiIdOrderByCreatedAtDesc(task.getApiId()).orElse(null)
                : null;
        // 拓扑：systemId + apiId
        apiTopologyRepository.findBySystemIdAndApiId(task.getSystemId(), task.getApiId()).ifPresent(top -> {
            details.topology = top;
            details.topologyNodes = apiTopologyNodeRepository.findByTopologyId(top.getId());
            details.topologyEdges = apiTopologyEdgeRepository.findByTopologyId(top.getId());
        });
        // 故障配置：按 taskId
        details.faultConfigs = faultConfigRepository.findByConditions(null, null, taskId, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        // SLO：按 taskId
        details.taskSlos = taskSloRepository.findByConditions(null, null, null, taskId, null, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        // 执行状态：取最新一条
        details.latestExecutionStatus = taskExecutionRepository.findTop1ByTaskIdOrderByStartedAtDesc(taskId)
                .map(TaskExecution::getStatus).orElse(null);
        return details;
    }

    /**
     * 根据名称获取检测任务
     */
    @Transactional(readOnly = true)
    public DetectionTask getDetectionTaskByName(String name) {
        logger.debug("Getting detection task by name: {}", name);

        return detectionTaskRepository.findByName(name)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + name));
    }

    /**
     * 创建新检测任务
     */
    public DetectionTask createDetectionTask(DetectionTask detectionTask) {
        logger.info("Creating new detection task: {}", detectionTask.getName());

        // 验证故障配置是否存在
        if (!faultConfigurationService.existsById(detectionTask.getFaultConfigurationsId())) {
            throw new BusinessException("FAULT_CONFIG_NOT_FOUND", "故障配置不存在: " + detectionTask.getFaultConfigurationsId());
        }

        // 验证系统是否存在
        if (!systemService.existsById(detectionTask.getSystemId())) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + detectionTask.getSystemId());
        }

        // 验证API是否存在
        if (!apiService.existsById(detectionTask.getApiId())) {
            throw new BusinessException("API_NOT_FOUND", "API不存在: " + detectionTask.getApiId());
        }

        // 检查任务名称是否已存在
        if (detectionTaskRepository.existsByName(detectionTask.getName())) {
            throw new BusinessException("DETECTION_TASK_NAME_EXISTS", "检测任务名称已存在: " + detectionTask.getName());
        }

        DetectionTask savedTask = detectionTaskRepository.save(detectionTask);
        logger.info("Detection task created successfully with id: {}", savedTask.getId());

        return savedTask;
    }

    /**
     * 更新检测任务信息
     */
    public DetectionTask updateDetectionTask(Long taskId, DetectionTask taskUpdate) {
        logger.info("Updating detection task: {}", taskId);

        DetectionTask existingTask = getDetectionTaskById(taskId);

        // 检查名称是否与其他任务冲突
        if (taskUpdate.getName() != null && !taskUpdate.getName().equals(existingTask.getName())) {
            if (detectionTaskRepository.existsByName(taskUpdate.getName())) {
                throw new BusinessException("DETECTION_TASK_NAME_EXISTS", "检测任务名称已存在: " + taskUpdate.getName());
            }
            existingTask.setName(taskUpdate.getName());
        }

        // 更新其他字段
        if (taskUpdate.getDescription() != null) {
            existingTask.setDescription(taskUpdate.getDescription());
        }
        if (taskUpdate.getSystemId() != null) {
            if (!systemService.existsById(taskUpdate.getSystemId())) {
                throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + taskUpdate.getSystemId());
            }
            existingTask.setSystemId(taskUpdate.getSystemId());
        }
        if (taskUpdate.getApiId() != null) {
            if (!apiService.existsById(taskUpdate.getApiId())) {
                throw new BusinessException("API_NOT_FOUND", "API不存在: " + taskUpdate.getApiId());
            }
            existingTask.setApiId(taskUpdate.getApiId());
        }
        if (taskUpdate.getFaultConfigurationsId() != null) {
            if (!faultConfigurationService.existsById(taskUpdate.getFaultConfigurationsId())) {
                throw new BusinessException("FAULT_CONFIG_NOT_FOUND", "故障配置不存在: " + taskUpdate.getFaultConfigurationsId());
            }
            existingTask.setFaultConfigurationsId(taskUpdate.getFaultConfigurationsId());
        }
        if (taskUpdate.getSloId() != null) {
            existingTask.setSloId(taskUpdate.getSloId());
        }
        if (taskUpdate.getRequestNum() != null) {
            existingTask.setRequestNum(taskUpdate.getRequestNum());
        }
        if (taskUpdate.getUpdatedBy() != null) {
            existingTask.setUpdatedBy(taskUpdate.getUpdatedBy());
        }

        DetectionTask savedTask = detectionTaskRepository.save(existingTask);
        logger.info("Detection task updated successfully: {}", savedTask.getId());

        return savedTask;
    }

    /**
     * 删除检测任务（软删除）
     */
    public void deleteDetectionTask(Long taskId) {
        logger.info("Deleting detection task: {}", taskId);

        DetectionTask task = getDetectionTaskById(taskId);

        // 检查任务是否已归档
        if (task.getArchivedAt() != null) {
            throw new BusinessException("TASK_ALREADY_ARCHIVED", "任务已归档，无法删除: " + taskId);
        }

        // 软删除：设置归档时间
        task.setArchivedAt(LocalDateTime.now());
        detectionTaskRepository.save(task);
        logger.info("Detection task archived successfully: {}", taskId);
    }

    /**
     * 执行检测任务
     */
    public DetectionTask executeDetectionTask(Long taskId) {
        logger.info("Executing detection task: {}", taskId);

        DetectionTask task = getDetectionTaskById(taskId);

        // 检查任务是否已归档
        if (task.getArchivedAt() != null) {
            throw new BusinessException("TASK_ARCHIVED", "已归档的任务无法执行: " + taskId);
        }

        // 更新任务的更新时间
        task.setUpdatedBy("system");

        DetectionTask savedTask = detectionTaskRepository.save(task);

        // TODO: 这里应该异步执行实际的检测逻辑
        // 可以使用 @Async 注解或消息队列来处理

        logger.info("Detection task execution started: {}", savedTask.getId());
        return savedTask;
    }

    /**
     * 取消检测任务（归档任务）
     */
    public DetectionTask cancelDetectionTask(Long taskId) {
        logger.info("Cancelling detection task: {}", taskId);

        DetectionTask task = getDetectionTaskById(taskId);

        // 检查任务是否已归档
        if (task.getArchivedAt() != null) {
            throw new BusinessException("TASK_ALREADY_ARCHIVED", "任务已归档，无法取消: " + taskId);
        }

        // 归档任务
        task.setArchivedAt(LocalDateTime.now());
        task.setUpdatedBy("system");

        DetectionTask savedTask = detectionTaskRepository.save(task);
        logger.info("Detection task cancelled successfully: {}", savedTask.getId());

        return savedTask;
    }

    /**
     * 获取活跃任务列表（未归档）
     */
    @Transactional(readOnly = true)
    public PageResponse<DetectionTask> getActiveTasks(int page, int size) {
        logger.debug("Getting active tasks - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DetectionTask> taskPage = detectionTaskRepository.findActiveTasksWithPagination(pageable);

        return PageResponse.of(taskPage.getContent(), taskPage.getTotalElements(), page, size);
    }

    /**
     * 获取已归档任务列表
     */
    @Transactional(readOnly = true)
    public PageResponse<DetectionTask> getArchivedTasks(int page, int size) {
        logger.debug("Getting archived tasks - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "archivedAt"));
        Page<DetectionTask> taskPage = detectionTaskRepository.findArchivedTasksWithPagination(pageable);

        return PageResponse.of(taskPage.getContent(), taskPage.getTotalElements(), page, size);
    }

    /**
     * 检查检测任务是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long taskId) {
        return detectionTaskRepository.existsById(taskId);
    }

    /**
     * 获取任务执行历史
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskExecution> getTaskExecutions(Long taskId, int page, int size) {
        logger.debug("Getting task executions for task: {}, page: {}, size: {}", taskId, page, size);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<TaskExecution> executions = taskExecutionRepository.findByTaskIdOrderByStartedAtDesc(taskId, pageable);
        return PageResponse.of(executions.getContent(), executions.getTotalElements(), page, size);
    }

    /**
     * 获取任务执行详情（增强版）- 旧签名（保留一段时间以兼容）
     */
    @Deprecated
    @Transactional(readOnly = true)
    public ExecutionDetailsDto getExecutionDetails(Long taskId, Long executionId) {
        logger.warn("[DEPRECATED] getExecutionDetails called with taskId={}, executionId={}", taskId, executionId);
        return getExecutionDetailsByExecutionId(executionId);
    }

    /**
     * 获取任务执行详情（根据 executionId）
     */
    @Transactional(readOnly = true)
    public ExecutionDetailsDto getExecutionDetailsByExecutionId(Long executionId) {
        logger.debug("Getting execution details by executionId={}", executionId);
        TaskExecution exec = taskExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException("TASK_EXECUTION_NOT_FOUND", "执行记录不存在: " + executionId));
        DetectionTask task = getDetectionTaskById(exec.getTaskId());

        ExecutionDetailsDto dto = new ExecutionDetailsDto();
        dto.basic = new ExecutionDetailsDto.BasicInfo();
        dto.basic.id = exec.getId();
        dto.basic.taskName = task.getName();
        dto.basic.environment = exec.getNamespace();
        dto.basic.api = apiService.getApiById(task.getApiId());
        dto.basic.initiator = task.getCreatedBy();
        dto.basic.startTime = exec.getStartedAt();
        dto.basic.currentStatus = exec.getStatus();
        dto.basic.cumulativeDuration = exec.getDurationSeconds();
        logger.info("[ExecDetails] execId={}, taskId={}, status={}, namespace={}, startedAt={}, finishedAt={}, durationSec={}, apiId={}",
                exec.getId(), exec.getTaskId(), exec.getStatus(), exec.getNamespace(), exec.getStartedAt(), exec.getFinishedAt(), exec.getDurationSeconds(), task.getApiId());


        // 执行日志：读取 executor 侧落库的 task_execution_log
        {
            java.util.List<com.chaosblade.svc.taskresource.entity.TaskExecutionLog> logs =
                    taskExecutionLogRepository.findByExecutionIdOrderByTsAsc(executionId);
            java.util.List<ExecutionDetailsDto.LogEntry> out = new java.util.ArrayList<>();
            for (var lg : logs) {
                ExecutionDetailsDto.LogEntry e = new ExecutionDetailsDto.LogEntry();
                e.ts = lg.getTs();
                e.level = switch (java.util.Objects.requireNonNullElse(lg.getLevel(), 1)) {
                    case 0 -> "DEBUG"; case 1 -> "INFO"; case 2 -> "WARN"; case 3 -> "ERROR"; default -> "INFO"; };
                e.message = lg.getMessage();

                out.add(e);
            }
            dto.logs = out;
        }
            logger.info("[ExecDetails] logs.size={}", (dto.logs==null?0:dto.logs.size()));


        var cases = testCaseRepository.findByExecutionId(executionId);
        // 合并 testCases + metrics
        var trList = testResultRepository.findByExecutionId(executionId);
        logger.info("[ExecDetails] cases.size={}, testResults.size={}", (cases==null?0:cases.size()), (trList==null?0:trList.size()));

        java.util.Map<Long, com.chaosblade.svc.taskresource.entity.TestResult> trMap = new java.util.HashMap<>();
        if (trList != null) {
            for (com.chaosblade.svc.taskresource.entity.TestResult tr : trList) {
                trMap.put(tr.getTestCaseId(), tr);
            }
        }
        java.util.List<ExecutionDetailsDto.TestCaseItem> items = new java.util.ArrayList<>();
        if (cases != null) {
            com.fasterxml.jackson.databind.ObjectMapper om2 = new com.fasterxml.jackson.databind.ObjectMapper();
            for (TestCase tc : cases) {
                com.chaosblade.svc.taskresource.entity.TestResult tr = trMap.get(tc.getId());
                // 兼容历史数据：若按 test_case_id 精确匹配不到，则按旧算法(caseId hash)回查
                if (tr == null) {
                    try {
                        java.util.List<?> arr = (tc.getFaultsJson()==null||tc.getFaultsJson().isBlank())
                                ? java.util.List.of() : om2.readValue(tc.getFaultsJson(), java.util.List.class);
                        String caseIdStr;
                        if (arr.isEmpty()) caseIdStr = "baseline";
                        else {
                            java.util.List<String> svcs = new java.util.ArrayList<>();
                            for (Object o : arr) if (o instanceof java.util.Map) {
                                Object sn = ((java.util.Map<?,?>) o).get("serviceName");
                                if (sn != null) svcs.add(String.valueOf(sn));
                            }
                            java.util.Collections.sort(svcs);
                            caseIdStr = String.join("+", svcs);
                        }
                        long legacyId = caseIdStr.equals("baseline") ? 0L : Math.abs(caseIdStr.hashCode());
                        tr = trMap.get(legacyId);
                        logger.debug("[ExecDetails] fallback-mapping: caseIdStr='{}', legacyId={}, found={} (tcId={})",
                                caseIdStr, legacyId, (tr!=null), tc.getId());

                    } catch (Exception ignore) {}
                }
                ExecutionDetailsDto.TestCaseItem it = new ExecutionDetailsDto.TestCaseItem();
                it.id = tc.getId();
                it.taskId = tc.getTaskId();
                it.caseType = tc.getCaseType()!=null? tc.getCaseType().name() : null;
                it.targetCount = tc.getTargetCount();
                it.faultsJson = tc.getFaultsJson();
                it.createdAt = tc.getCreatedAt();
                logger.debug("[ExecDetails] caseId={}, matchedMetrics={}, p50={}, p95={}, p99={}, errRate={}", tc.getId(), (tr!=null), (tr!=null?tr.getP50():null), (tr!=null?tr.getP95():null), (tr!=null?tr.getP99():null), (tr!=null?tr.getErrRate():null));

                it.executionId = tc.getExecutionId();

                if (tr != null) {
                    it.p50 = tr.getP50();
                    it.p95 = tr.getP95();
                    it.p99 = tr.getP99();
                    it.errRate = tr.getErrRate();
                }
                items.add(it);
            }
        }
        dto.testCases = items;
        {
            int cntWith = 0;
            int total = (items==null?0:items.size());
            if (items != null) {
                for (var tci : items) {
                    if (tci.p50 != null || tci.p95 != null || tci.p99 != null || tci.errRate != null) cntWith++;
                }
            }
            logger.info("[ExecDetails] testCases.size={}, withMetrics={}, withoutMetrics={}", total, cntWith, (total - cntWith));
        }



        java.util.Map<String, java.util.Set<String>> svc2types = new java.util.LinkedHashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        if (cases != null) {
            for (var tc : cases) {
                String json = tc.getFaultsJson();
                if (json == null || json.isBlank()) continue;
                try {
                    java.util.List<?> arr = om.readValue(json, java.util.List.class);

                    if (arr == null) continue;
                    for (Object o : arr) {
                        if (!(o instanceof java.util.Map)) continue;
                        java.util.Map<?,?> m = (java.util.Map<?,?>) o;
                        Object svcObj = m.get("serviceName");
                        String svc = (svcObj==null)? null : String.valueOf(svcObj);
                        if (svc == null || svc.isBlank()) continue;
                        String ftype = "unknown";
                        Object fd = m.get("faultDefinition");
                        if (fd instanceof java.util.Map) {
                            Object spec = ((java.util.Map<?,?>) fd).get("spec");
                            if (spec instanceof java.util.Map) {
                                Object exps = ((java.util.Map<?,?>) spec).get("experiments");
                                if (exps instanceof java.util.List && !((java.util.List<?>) exps).isEmpty()) {
                                    Object first = ((java.util.List<?>) exps).get(0);
                                    if (first instanceof java.util.Map) {
                                        Object action = ((java.util.Map<?,?>) first).get("action");
                                        if (action != null) ftype = String.valueOf(action);
                                    }
                                }
                            }
                        }
                        svc2types.computeIfAbsent(svc, k -> new java.util.LinkedHashSet<>()).add(ftype);
                    }
                } catch (Exception ignore) { }
            }
        }
        java.util.List<ExecutionDetailsDto.FaultInjectionSummary> fi = new java.util.ArrayList<>();
        for (var e : svc2types.entrySet()) {
            ExecutionDetailsDto.FaultInjectionSummary s = new ExecutionDetailsDto.FaultInjectionSummary();
            s.serviceName = e.getKey();
            s.faultTypes = new java.util.ArrayList<>(e.getValue());
            fi.add(s);
        }
        dto.faultInjections = fi;
        logger.info("[ExecDetails] svc2types.size={}, faultInjections.size={}, services={}", svc2types.size(), fi.size(), svc2types.keySet());


        ExecutionDetailsDto.RealtimeStatus r = new ExecutionDetailsDto.RealtimeStatus();
        r.totalTestCases = (cases != null) ? cases.size() : 0;
        r.completedTestCases = "DONE".equalsIgnoreCase(exec.getStatus()) ? r.totalTestCases : 0;
        r.totalServices = svc2types.size();
        r.completedServices = "DONE".equalsIgnoreCase(exec.getStatus()) ? r.totalServices : 0;
        logger.info("[ExecDetails] realtime: totalCases={}, completedCases={}, totalSvcs={}, completedSvcs={}, testingSvcs={}", r.totalTestCases, r.completedTestCases, r.totalServices, r.completedServices, r.testingServices);

        r.testingServices = ("INJECTING_AND_REPLAYING".equalsIgnoreCase(exec.getStatus())) ? Math.max(0, r.totalServices - r.completedServices) : 0;
        dto.realtime = r;

        
        try {
            dto.modelConclusion = taskConclusionRepository.findByExecutionId(executionId)
                    .map(com.chaosblade.svc.taskresource.entity.TaskConclusion::getModelContent)
                    .orElse(null);
        } catch (Exception ignore) { dto.modelConclusion = null; }

        return dto;
    }

    /**
     * 分页查询所有执行记录（可筛选）
     */
    @Transactional(readOnly = true)
    public PageResponse<DetectionTaskDtos.TaskExecutionView> getExecutions(Long taskId,
                                                                           String status,
                                                                           String namespace,
                                                                           LocalDateTime startDate,
                                                                           LocalDateTime endDate,
                                                                           int page,
                                                                           int size) {
        logger.debug("Query executions: taskId={}, status={}, namespace={}, startDate={}, endDate={}, page={}, size={}",
                taskId, status, namespace, startDate, endDate, page, size);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<TaskExecution> p = taskExecutionRepository.findByConditions(taskId, status, namespace, startDate, endDate, pageable);
        var list = p.getContent();
        // 批量查询任务名称，避免 N+1
        java.util.Set<Long> taskIds = new java.util.LinkedHashSet<>();
        for (TaskExecution te : list) taskIds.add(te.getTaskId());
        java.util.Map<Long, String> id2name = new java.util.HashMap<>();
        for (DetectionTask t : detectionTaskRepository.findAllById(taskIds)) {
            id2name.put(t.getId(), t.getName());
        }
        java.util.List<DetectionTaskDtos.TaskExecutionView> items = new java.util.ArrayList<>();
        for (TaskExecution te : list) {
            DetectionTaskDtos.TaskExecutionView v = new DetectionTaskDtos.TaskExecutionView();
            v.id = te.getId();
            v.status = te.getStatus();
            v.namespace = te.getNamespace();
            v.requestNum = te.getRequestNum();
            v.errorCode = te.getErrorCode();
            v.errorMsg = te.getErrorMsg();
            v.startedAt = te.getStartedAt();
            v.finishedAt = te.getFinishedAt();
            v.duration = te.getDurationSeconds();
            v.taskName = id2name.get(te.getTaskId());
            items.add(v);
        }
        return PageResponse.of(items, p.getTotalElements(), page, size);
    }


}
