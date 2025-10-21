package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

/** 简化版测试用例：限制每条用例最多2个并发故障（0/1/2），便于可控规模输出。 */
public class SimplifiedTestCaseDTO {
  private int faultCount; // 0, 1, 2
  private List<FaultTarget> faults; // 当 faultCount=0 时为空列表
  private String description; // 例如 "baseline", "single fault: svc", "dual fault: svcA + svcB"

  public static class FaultTarget {
    private String serviceName;
    private TestCaseDTO.ChaosSpec chaos; // 复用已有的混沌参数结构
    private Integer replicas; // 便于展示
    private String selectionReason; // 若来自Step1选择，则带上理由；否则为 "COVERAGE"
    private Long score; // 若来自Step1，则带上分数

    public FaultTarget() {}

    public FaultTarget(
        String serviceName,
        TestCaseDTO.ChaosSpec chaos,
        Integer replicas,
        String selectionReason,
        Long score) {
      this.serviceName = serviceName;
      this.chaos = chaos;
      this.replicas = replicas;
      this.selectionReason = selectionReason;
      this.score = score;
    }

    public String getServiceName() {
      return serviceName;
    }

    public TestCaseDTO.ChaosSpec getChaos() {
      return chaos;
    }

    public Integer getReplicas() {
      return replicas;
    }

    public String getSelectionReason() {
      return selectionReason;
    }

    public Long getScore() {
      return score;
    }
  }

  public SimplifiedTestCaseDTO() {}

  public SimplifiedTestCaseDTO(int faultCount, List<FaultTarget> faults, String description) {
    this.faultCount = faultCount;
    this.faults = faults;
    this.description = description;
  }

  public int getFaultCount() {
    return faultCount;
  }

  public List<FaultTarget> getFaults() {
    return faults;
  }

  public String getDescription() {
    return description;
  }
}
