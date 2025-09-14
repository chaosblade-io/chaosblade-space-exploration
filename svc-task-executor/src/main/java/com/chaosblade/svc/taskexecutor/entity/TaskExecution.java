package com.chaosblade.svc.taskexecutor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_execution")
public class TaskExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "namespace", nullable = false, length = 128)
    private String namespace;

    @Column(name = "req_def_id")
    private Long reqDefId;

    @Column(name = "request_num", nullable = false)
    private Integer requestNum = 1;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "INIT";

    @Column(name = "analyze_task_id", length = 64)
    private String analyzeTaskId;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "intercept_record_id", length = 64)
    private String interceptRecordId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Long getReqDefId() { return reqDefId; }
    public void setReqDefId(Long reqDefId) { this.reqDefId = reqDefId; }
    public Integer getRequestNum() { return requestNum; }
    public void setRequestNum(Integer requestNum) { this.requestNum = requestNum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public String getAnalyzeTaskId() { return analyzeTaskId; }
    public void setAnalyzeTaskId(String analyzeTaskId) { this.analyzeTaskId = analyzeTaskId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getInterceptRecordId() { return interceptRecordId; }
    public void setInterceptRecordId(String interceptRecordId) { this.interceptRecordId = interceptRecordId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}

