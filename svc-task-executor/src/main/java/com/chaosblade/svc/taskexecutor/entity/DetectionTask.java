package com.chaosblade.svc.taskexecutor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    public Long getId() { return id; }
    public Long getApiId() { return apiId; }
    public Long getSystemId() { return systemId; }
    public Integer getApiDefinitionId() { return apiDefinitionId; }
    public Integer getRequestNum() { return requestNum; }
}

