/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

