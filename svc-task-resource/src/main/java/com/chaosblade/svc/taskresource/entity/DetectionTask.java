package com.chaosblade.svc.taskresource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 检测任务实体类
 */
@Entity
@Table(name = "detection_tasks")
public class DetectionTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "api_id", nullable = false)
    private Long apiId;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "fault_configurations_id", nullable = false)
    private Long faultConfigurationsId;

    @Column(name = "slo_id", nullable = false)
    private Long sloId;

    @Column(name = "request_num", nullable = false)
    private Integer requestNum;

    @Column(name = "api_definition_id")
    private Integer apiDefinitionId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public DetectionTask() {}

    public DetectionTask(String name, String description, Long systemId, Long apiId,
                        String createdBy, Long faultConfigurationsId, Long sloId, Integer requestNum) {
        this.name = name;
        this.description = description;
        this.systemId = systemId;
        this.apiId = apiId;
        this.createdBy = createdBy;
        this.faultConfigurationsId = faultConfigurationsId;
        this.sloId = sloId;
        this.requestNum = requestNum;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Long getFaultConfigurationsId() {
        return faultConfigurationsId;
    }

    public void setFaultConfigurationsId(Long faultConfigurationsId) {
        this.faultConfigurationsId = faultConfigurationsId;
    }

    public Long getSloId() {
        return sloId;
    }

    public void setSloId(Long sloId) {
        this.sloId = sloId;
    }

    public Integer getRequestNum() {
        return requestNum;
    }

    public void setRequestNum(Integer requestNum) {
        this.requestNum = requestNum;
    }

    public Integer getApiDefinitionId() {
        return apiDefinitionId;
    }

    public void setApiDefinitionId(Integer apiDefinitionId) {
        this.apiDefinitionId = apiDefinitionId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
