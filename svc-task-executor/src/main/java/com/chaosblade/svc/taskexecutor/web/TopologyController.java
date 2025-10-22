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

package com.chaosblade.svc.taskexecutor.web;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.taskexecutor.dto.EnhancedSimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.dto.TestCaseDTO;
import com.chaosblade.svc.taskexecutor.service.FaultConfigQueryService;
import com.chaosblade.svc.taskexecutor.service.TestCaseGenerationService;
import com.chaosblade.svc.taskexecutor.service.TopologyLayerService;
import com.chaosblade.svc.taskexecutor.service.TopologyLayerService.Layer;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TopologyController {

  private final TopologyLayerService topologyLayerService;
  private final FaultConfigQueryService faultConfigQueryService;
  private final TestCaseGenerationService testCaseGenerationService;

  public TopologyController(
      TopologyLayerService topologyLayerService,
      FaultConfigQueryService faultConfigQueryService,
      TestCaseGenerationService testCaseGenerationService) {
    this.topologyLayerService = topologyLayerService;
    this.faultConfigQueryService = faultConfigQueryService;
    this.testCaseGenerationService = testCaseGenerationService;
  }

  /** 获取按层次分组的叶子节点列表（剥洋葱式层次）。 1) 去掉孤立节点；2) 迭代移除叶子节点，记录每一层。 */
  @GetMapping("/detection-tasks/{taskId}/topology/layers")
  public ApiResponse<List<Layer>> getTopologyLayers(@PathVariable("taskId") Long taskId) {
    List<Layer> layers = topologyLayerService.getLayersByTaskId(taskId);
    return ApiResponse.ok(layers);
  }

  /** 根据 taskId 获取故障注入配置信息 */
  @GetMapping("/detection-tasks/{taskId}/fault-configs")
  public ApiResponse<List<ServiceFaultConfig>> getFaultConfigs(
      @PathVariable("taskId") Long taskId) {
    List<ServiceFaultConfig> list = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
    return ApiResponse.ok(list);
  }

  /**
   * 生成测试用例（自底向上，从叶子到上层，包含基线与下游组合）
   *
   * <p>- 叶子节点（第1层）: <serviceName, faultConfigId> - 非叶子节点（第2层及以上）: <serviceName, faultConfigId,
   * <downstreamFaultScenarios>> - 基线用例：faultConfigId 为 null 表示不注入故障
   *
   * @param taskId 检测任务ID
   * @return ApiResponse<List<TestCaseDTO>> 返回该任务生成的全部测试用例
   *     <p>错误处理： - 当 taskId 不存在或无对应拓扑时，将抛出 BusinessException，由全局异常处理器统一返回 ApiResponse.error(...)
   */
  @GetMapping("/detection-tasks/{taskId}/test-cases")
  public ApiResponse<List<TestCaseDTO>> generateTestCases(@PathVariable("taskId") Long taskId) {
    List<TestCaseDTO> cases = testCaseGenerationService.generateForTask(taskId);
    return ApiResponse.ok(cases);
  }

  /**
   * Step1：仅输出高价值节点选择结果（打分/覆盖/必测集合），不生成用例。 - 对拓扑做 SCC 压缩为 DAG - 计算 in_paths / out_paths 与 score -
   * 选出：分支点、叶子、Top-K 高分 或 覆盖阈值达标的节点
   */
  @GetMapping("/detection-tasks/{taskId}/test-cases/step1")
  public ApiResponse<TestCaseGenerationService.Step1Result> getStep1(
      @PathVariable("taskId") Long taskId) {
    var result = testCaseGenerationService.computeStep1(taskId);
    return ApiResponse.ok(result);
  }

  /** 简化版用例生成（非组合爆炸）： - 0 故障：baseline - 1 故障：对 Step1 选中的每个目标服务，至少1条 - 2 故障：按分数降序的相邻对，规模可控 */
  @GetMapping("/detection-tasks/{taskId}/test-cases/simple")
  public ApiResponse<List<EnhancedSimplifiedTestCaseDTO>> generateSimpleTestCases(
      @PathVariable("taskId") Long taskId) {
    var cases = testCaseGenerationService.generateEnhancedSimpleCases(taskId);
    return ApiResponse.ok(cases);
  }
}
