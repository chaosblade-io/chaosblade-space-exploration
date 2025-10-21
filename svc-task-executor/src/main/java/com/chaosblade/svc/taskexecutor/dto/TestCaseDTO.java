package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

/**
 * 单个测试用例： - 叶子节点: <serviceName, faultConfigId> - 非叶子: <serviceName, faultConfigId,
 * downstreamFaultScenarios>
 *
 * <p>新增元数据字段以支持“高价值节点优先”的定向用例： - selectionReason: 选中理由（BRANCH_POINT/LEAF/HIGH_SCORE） - score:
 * 路径覆盖评分（in_paths * out_paths） - replicas: 预估副本数 - chaos:
 * 标准化混沌参数（killMode/count/duration/timeout/expectedOutcome）
 */
public class TestCaseDTO {
  private String serviceName;
  private Long faultConfigId; // null 表示基线（不注入故障）
  private List<TestCaseDTO> downstreamFaultScenarios; // 非叶子节点使用

  // 元数据
  private String selectionReason;
  private Long score;
  private Integer replicas;
  private ChaosSpec chaos;

  public static class ChaosSpec {
    private String killMode; // e.g. "all" | null
    private Integer count; // e.g. 1 | null
    private Integer durationSec; // e.g. 60
    private Integer timeoutSec; // e.g. 60
    private String expectedOutcome; // e.g. FAST_5XX_OR_TIMEOUT, OK_200_OR_AFTER_RETRY

    public ChaosSpec() {}

    public ChaosSpec(
        String killMode,
        Integer count,
        Integer durationSec,
        Integer timeoutSec,
        String expectedOutcome) {
      this.killMode = killMode;
      this.count = count;
      this.durationSec = durationSec;
      this.timeoutSec = timeoutSec;
      this.expectedOutcome = expectedOutcome;
    }

    public String getKillMode() {
      return killMode;
    }

    public Integer getCount() {
      return count;
    }

    public Integer getDurationSec() {
      return durationSec;
    }

    public Integer getTimeoutSec() {
      return timeoutSec;
    }

    public String getExpectedOutcome() {
      return expectedOutcome;
    }
  }

  public TestCaseDTO() {}

  public TestCaseDTO(
      String serviceName, Long faultConfigId, List<TestCaseDTO> downstreamFaultScenarios) {
    this.serviceName = serviceName;
    this.faultConfigId = faultConfigId;
    this.downstreamFaultScenarios = downstreamFaultScenarios;
  }

  public TestCaseDTO(
      String serviceName, String selectionReason, Long score, Integer replicas, ChaosSpec chaos) {
    this.serviceName = serviceName;
    this.selectionReason = selectionReason;
    this.score = score;
    this.replicas = replicas;
    this.chaos = chaos;
  }

  public String getServiceName() {
    return serviceName;
  }

  public Long getFaultConfigId() {
    return faultConfigId;
  }

  public List<TestCaseDTO> getDownstreamFaultScenarios() {
    return downstreamFaultScenarios;
  }

  public String getSelectionReason() {
    return selectionReason;
  }

  public Long getScore() {
    return score;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public ChaosSpec getChaos() {
    return chaos;
  }
}
