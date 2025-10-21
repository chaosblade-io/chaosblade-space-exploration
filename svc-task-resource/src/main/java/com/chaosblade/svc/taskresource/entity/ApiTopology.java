package com.chaosblade.svc.taskresource.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** API拓扑实体类 */
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

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    if (discoveredAt == null) {
      discoveredAt = LocalDateTime.now();
    }
  }

  // Constructors
  public ApiTopology() {}

  public ApiTopology(
      Long systemId, Long apiId, LocalDateTime discoveredAt, String sourceVersion, String notes) {
    this.systemId = systemId;
    this.apiId = apiId;
    this.discoveredAt = discoveredAt;
    this.sourceVersion = sourceVersion;
    this.notes = notes;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public LocalDateTime getDiscoveredAt() {
    return discoveredAt;
  }

  public void setDiscoveredAt(LocalDateTime discoveredAt) {
    this.discoveredAt = discoveredAt;
  }

  public String getSourceVersion() {
    return sourceVersion;
  }

  public void setSourceVersion(String sourceVersion) {
    this.sourceVersion = sourceVersion;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
