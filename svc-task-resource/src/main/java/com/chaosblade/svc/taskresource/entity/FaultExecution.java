package com.chaosblade.svc.taskresource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 故障执行实体类 - 基于fault_injection_results表
 */
@Entity
@Table(name = "fault_injection_results")
public class FaultExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "execution_id", nullable = false)
    private Long executionId;
    
    @Column(name = "topology_node_id", nullable = false)
    private Long topologyNodeId;
    
    @Column(name = "template_type", nullable = false, length = 64)
    private String templateType;
    
    @Column(name = "parameters", columnDefinition = "JSON")
    private String parameters;
    
    @Column(name = "result_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ResultStatus resultStatus;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "duration_ms")
    private Integer durationMs;
    
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    // 枚举类型
    public enum ResultStatus {
        SUCCEEDED, FAILED, PARTIAL
    }
    
    // 构造函数
    public FaultExecution() {}
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }
    
    public Long getTopologyNodeId() {
        return topologyNodeId;
    }
    
    public void setTopologyNodeId(Long topologyNodeId) {
        this.topologyNodeId = topologyNodeId;
    }
    
    public String getTemplateType() {
        return templateType;
    }
    
    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }
    
    public String getParameters() {
        return parameters;
    }
    
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
    
    public ResultStatus getResultStatus() {
        return resultStatus;
    }
    
    public void setResultStatus(ResultStatus resultStatus) {
        this.resultStatus = resultStatus;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Integer getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
