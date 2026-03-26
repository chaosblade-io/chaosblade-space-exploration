package com.chaosblade.svc.taskresource.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskresource.dto.ProbeTaskDtos;
import com.chaosblade.svc.taskresource.entity.DetectionTask;
import com.chaosblade.svc.taskresource.entity.FaultConfig;
import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import com.chaosblade.svc.taskresource.entity.TaskSlo;
import com.chaosblade.svc.taskresource.repository.DetectionTaskRepository;
import com.chaosblade.svc.taskresource.repository.FaultConfigRepository;
import com.chaosblade.svc.taskresource.repository.HttpReqDefRepository;
import com.chaosblade.svc.taskresource.repository.TaskSloRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class ProbeTaskService {
    private static final Logger logger = LoggerFactory.getLogger(ProbeTaskService.class);

    private final HttpReqDefRepository httpReqDefRepository;
    private final DetectionTaskRepository detectionTaskRepository;
    private final TaskSloRepository taskSloRepository;
    private final FaultConfigRepository faultConfigRepository;
    private final SystemService systemService;
    private final ApiService apiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProbeTaskService(HttpReqDefRepository httpReqDefRepository,
                            DetectionTaskRepository detectionTaskRepository,
                            TaskSloRepository taskSloRepository,
                            FaultConfigRepository faultConfigRepository,
                            SystemService systemService,
                            ApiService apiService) {
        this.httpReqDefRepository = httpReqDefRepository;
        this.detectionTaskRepository = detectionTaskRepository;
        this.taskSloRepository = taskSloRepository;
        this.faultConfigRepository = faultConfigRepository;
        this.systemService = systemService;
        this.apiService = apiService;
    }

    @Transactional
    public ProbeTaskDtos.ProbeTaskCreateResponse createProbeTask(ProbeTaskDtos.ProbeTaskCreateRequest req) {
        // 0) 基本校验
        validateRequest(req);

        // 0.1) 业务预检：外键存在性，避免落库时报FK错误
        if (!systemService.existsById(req.systemId)) {
            throw new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + req.systemId);
        }
        if (!apiService.existsById(req.apiId)) {
            throw new BusinessException("API_NOT_FOUND", "API不存在: " + req.apiId);
        }

        // 1) 复用已有 http_req_def 或创建新的
        Long apiDefinitionId;
        if (req.apiDefinitionId != null && req.apiDefinitionId > 0) {
            // 前端传了已有的 http_req_def ID，直接复用
            if (!httpReqDefRepository.existsById(req.apiDefinitionId)) {
                throw new BusinessException("API_DEF_NOT_FOUND", "http_req_def 不存在: " + req.apiDefinitionId);
            }
            apiDefinitionId = req.apiDefinitionId;
            logger.info("Reusing existing http_req_def: id={}", apiDefinitionId);
        } else if (req.apiDefinition == null && req.apiId != null) {
            // 前端没传 apiDefinition，通过 apiId 自动查找关联的 http_req_def
            List<HttpReqDef> candidates = httpReqDefRepository.findByApiId(req.apiId);
            HttpReqDef found = candidates.isEmpty() ? null : candidates.get(0);
            if (found == null) {
                throw new BusinessException("API_DEF_NOT_FOUND", "未找到 apiId=" + req.apiId + " 对应的 http_req_def，请先在数据库中预定义");
            }
            apiDefinitionId = found.getId();
            logger.info("Auto-resolved http_req_def by apiId={}: reqDefId={}", req.apiId, apiDefinitionId);
        } else {
            apiDefinitionId = createHttpReqDef(req.apiDefinition);
        }

        // 2) 创建 detection_tasks 主任务（补充 api_definition_id）
        Long taskId = createDetectionTask(req, apiDefinitionId);

        // 3) 批量创建 task_slo
        int sloCount = 0;
        if (req.taskSlo != null) {
            for (ProbeTaskDtos.TaskSloItem item : req.taskSlo) {
                TaskSlo slo = new TaskSlo();
                slo.setId(com.chaosblade.svc.taskresource.util.Ids.newId());
                slo.setTaskId(taskId);
                slo.setNodeId(item.nodeId);
                slo.setP95(item.p95);
                slo.setP99(item.p99);
                slo.setErrRate(item.errRate);
                taskSloRepository.save(slo);
                sloCount++;
            }
        }

        // 4) 批量创建 fault_config（faultscript 对象 JSON 序列化）
        int fcCount = 0;
        if (req.faultConfigurations != null) {
            for (ProbeTaskDtos.FaultConfigurationItem fc : req.faultConfigurations) {
                FaultConfig entity = new FaultConfig();
                entity.setNodeId(fc.nodeId);
                entity.setTaskId(taskId);
                // 设置类型（支持前端传入的 type 字段）
                entity.setType(fc.type);
                try {
                    // 兼容前端字段可能写成 faulscript 的情况（通过 DTO setter 已映射到 faultscript）
                    entity.setFaultscript(objectMapper.writeValueAsString(fc.faultscript));
                } catch (JsonProcessingException e) {
                    throw new BusinessException("FAULTSCRIPT_JSON_INVALID", "faultscript 不是合法JSON对象", e);
                }
                faultConfigRepository.save(entity);
                fcCount++;
            }
        }

        logger.info("Probe task created: taskId={}, apiDefinitionId={}, taskSloCount={}, faultConfigCount={}",
                taskId, apiDefinitionId, sloCount, fcCount);
        return new ProbeTaskDtos.ProbeTaskCreateResponse(taskId, apiDefinitionId, fcCount, sloCount);
    }

    private void validateRequest(ProbeTaskDtos.ProbeTaskCreateRequest req) {
        if (req == null) throw new BusinessException("REQ_INVALID", "请求体不能为空");
        if (isBlank(req.name) || isBlank(req.description) || req.systemId == null || req.apiId == null
                || isBlank(req.createdBy) || req.requestNum == null) {
            throw new BusinessException("REQ_FIELDS_MISSING", "必填字段缺失: name/description/systemId/apiId/createdBy/requestNum");
        }
        // apiDefinition 验证：三种模式都合法
        // 1. apiDefinitionId 有值 → 复用已有 http_req_def
        // 2. apiDefinition 为空但 apiId 有值 → 通过 apiId 自动查找
        // 3. apiDefinition 有值 → 创建新的
        if (req.apiDefinitionId != null && req.apiDefinitionId > 0) {
            // 模式1: 复用模式
        } else if (req.apiDefinition == null && req.apiId != null) {
            // 模式2: 通过 apiId 自动查找，验证在 createProbeTask 中进行
        } else if (req.apiDefinition != null) {
            // 模式3: 创建新的
            if (httpReqDefRepository.existsByCode(req.apiDefinition.code)) {
                throw new BusinessException("API_DEFINITION_CODE_EXISTS", "API 定义创建失败：code '"+req.apiDefinition.code+"' 已存在");
            }
            try { HttpReqDef.HttpMethod.valueOf(req.apiDefinition.method); } catch (Exception e) {
                throw new BusinessException("METHOD_INVALID", "非法的HTTP方法: "+req.apiDefinition.method);
            }
            try { HttpReqDef.BodyMode.valueOf(req.apiDefinition.bodyMode); } catch (Exception e) {
                throw new BusinessException("BODY_MODE_INVALID", "非法的请求体模式: "+req.apiDefinition.bodyMode);
            }
        } else {
            throw new BusinessException("API_DEF_MISSING", "apiDefinition、apiDefinitionId、apiId 至少提供一个");
        }
    }

    private Long createHttpReqDef(ProbeTaskDtos.ApiDefinitionDTO def) {
        HttpReqDef entity = new HttpReqDef();
        entity.setCode(def.code);
        entity.setName(def.name);
        entity.setMethod(HttpReqDef.HttpMethod.valueOf(def.method));
        entity.setUrlTemplate(def.urlTemplate);
        entity.setHeaders(def.headers);
        entity.setQueryParams(def.queryParams);
        entity.setBodyMode(HttpReqDef.BodyMode.valueOf(def.bodyMode));
        entity.setContentType(def.contentType);
        entity.setBodyTemplate(def.bodyTemplate);
        entity.setApiId(def.apiId);
        HttpReqDef saved = httpReqDefRepository.save(entity);
        return saved.getId();
    }

    private Long createDetectionTask(ProbeTaskDtos.ProbeTaskCreateRequest req, Long apiDefinitionId) {
        DetectionTask task = new DetectionTask();
        task.setName(req.name);
        task.setDescription(req.description);
        task.setSystemId(req.systemId);
        task.setApiId(req.apiId);
        task.setCreatedBy(req.createdBy);
        task.setUpdatedBy(req.updatedBy);
        task.setRequestNum(req.requestNum);
        // 关键：回填 http_req_def 的ID到 detection_tasks.api_definition_id
        if (apiDefinitionId != null) {
            task.setApiDefinitionId(apiDefinitionId.intValue());
        }
        if (req.createdAt != null) task.setCreatedAt(toLocalDateTime(req.createdAt));
        if (req.updatedAt != null) task.setUpdatedAt(toLocalDateTime(req.updatedAt));
        if (req.archivedAt != null) task.setArchivedAt(toLocalDateTime(req.archivedAt));
        DetectionTask saved = detectionTaskRepository.save(task);
        return saved.getId();
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}

