package com.chaosblade.svc.taskexecutor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "systems")
public class System {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "system_key", nullable = false, length = 64, unique = true)
  private String systemKey;

  @Column(name = "name", length = 255)
  private String name;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public String getSystemKey() {
    return systemKey;
  }

  public String getName() {
    return name;
  }
}
