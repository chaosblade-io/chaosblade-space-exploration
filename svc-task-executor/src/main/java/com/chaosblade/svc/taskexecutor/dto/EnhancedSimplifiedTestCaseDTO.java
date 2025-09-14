package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

/**
 * 返回结构：每个测试用例包含一个 faults 数组（每个元素是 EnhancedFaultTargetDTO）。
 * 排序要求：单故障在前，baseline（空 faults）其次，双故障在后。
 */
public class EnhancedSimplifiedTestCaseDTO {
    private List<EnhancedFaultTargetDTO> faults; // 0/1/2 个

    public EnhancedSimplifiedTestCaseDTO() {}
    public EnhancedSimplifiedTestCaseDTO(List<EnhancedFaultTargetDTO> faults) { this.faults = faults; }

    public List<EnhancedFaultTargetDTO> getFaults() { return faults; }
}

