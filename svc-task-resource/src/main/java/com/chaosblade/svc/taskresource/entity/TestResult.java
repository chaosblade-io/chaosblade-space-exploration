package com.chaosblade.svc.taskresource.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 只读映射到 svc-task-executor 产出的 test_result 表，用于在资源模块聚合返回测试指标 */
@Entity
@Table(name = "test_result")
public class TestResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "execution_id", nullable = false)
  private Long executionId;

  @Column(name = "test_case_id", nullable = false)
  private Long testCaseId;

  @Column(name = "p50")
  private Integer p50;

  @Column(name = "p95")
  private Integer p95;

  @Column(name = "p99")
  private Integer p99;

  @Column(name = "err_rate")
  private BigDecimal errRate;

  public Long getId() {
    return id;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public Long getTestCaseId() {
    return testCaseId;
  }

  public Integer getP50() {
    return p50;
  }

  public Integer getP95() {
    return p95;
  }

  public Integer getP99() {
    return p99;
  }

  public BigDecimal getErrRate() {
    return errRate;
  }
}
