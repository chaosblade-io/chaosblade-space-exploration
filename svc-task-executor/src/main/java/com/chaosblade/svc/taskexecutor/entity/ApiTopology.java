package com.chaosblade.svc.taskexecutor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_topologies")
public class ApiTopology {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "api_id", nullable = false)
    private Long apiId;

    @Column(name = "discovered_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime discoveredAt;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public Long getApiId() { return apiId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

