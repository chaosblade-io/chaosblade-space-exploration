package com.chaosblade.svc.k8sgraph.dto;

import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 命名空间分析任务DTO
 */
public class NsAnalysisTaskDTO {

    private Long id;
    private String taskId;
    private String namespace;
    private Long systemId;
    private String status;
    private String triggerType;
    private Integer topN;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    private Long totalTimeMs;
    private Integer currentPhase;
    private Integer progressPercent;
    private String errorCode;
    private String errorMessage;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public NsAnalysisTaskDTO() {}

    public static NsAnalysisTaskDTO fromEntity(NsAnalysisTask entity) {
        NsAnalysisTaskDTO dto = new NsAnalysisTaskDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setNamespace(entity.getNamespace());
        dto.setSystemId(entity.getSystemId());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setTriggerType(entity.getTriggerType() != null ? entity.getTriggerType().name() : null);
        dto.setTopN(entity.getTopN());
        dto.setStartedAt(entity.getStartedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        dto.setTotalTimeMs(entity.getTotalTimeMs());
        dto.setCurrentPhase(entity.getCurrentPhase());
        dto.setProgressPercent(entity.getProgressPercent());
        dto.setErrorCode(entity.getErrorCode());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public Integer getTopN() { return topN; }
    public void setTopN(Integer topN) { this.topN = topN; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Long getTotalTimeMs() { return totalTimeMs; }
    public void setTotalTimeMs(Long totalTimeMs) { this.totalTimeMs = totalTimeMs; }

    public Integer getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(Integer currentPhase) { this.currentPhase = currentPhase; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

