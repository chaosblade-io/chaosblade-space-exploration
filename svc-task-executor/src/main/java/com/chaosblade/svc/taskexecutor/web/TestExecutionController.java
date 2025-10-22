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
import com.chaosblade.svc.taskexecutor.service.TestExecutionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test-executions")
public class TestExecutionController {

  private final TestExecutionService testExecutionService;

  public TestExecutionController(TestExecutionService testExecutionService) {
    this.testExecutionService = testExecutionService;
  }

  // 启动一次测试执行：随机选择一个非基线用例 -> 注入故障 -> 发起HTTP请求
  @PostMapping("/start")
  public ApiResponse<TestExecutionService.ExecutionResult> start(
      @RequestParam("taskId") Long taskId) {
    var result = testExecutionService.startExecution(taskId);
    return ApiResponse.ok(result);
  }

  // 查询执行结果（内存存储）
  @GetMapping("/{executionId}/results")
  public ApiResponse<TestExecutionService.ExecutionResult> get(
      @PathVariable("executionId") String executionId) {
    var result = testExecutionService.getExecution(executionId);
    return ApiResponse.ok(result);
  }

  @PostMapping("/analyze-patterns")
  public ApiResponse<TestExecutionService.PatternAnalysisResult> analyzePatterns(
      @RequestParam("taskId") Long taskId) {
    return ApiResponse.ok(testExecutionService.startPatternAnalysis(taskId));
  }

  @GetMapping("/pattern-analysis/{executionId}/results")
  public ApiResponse<TestExecutionService.PatternAnalysisResult> getPatternAnalysis(
      @PathVariable("executionId") String executionId) {
    return ApiResponse.ok(testExecutionService.getPatternAnalysis(executionId));
  }

  @PostMapping("/start-with-recording")
  public ApiResponse<TestExecutionService.CombinedExecutionResult> startWithRecording(
      @RequestParam("taskId") Long taskId) {
    return ApiResponse.ok(testExecutionService.startWithRecording(taskId));
  }

  @GetMapping("/combined/{executionId}/results")
  public ApiResponse<TestExecutionService.CombinedExecutionResult> getCombined(
      @PathVariable("executionId") String executionId) {
    return ApiResponse.ok(testExecutionService.getCombined(executionId));
  }

  @PostMapping("/batch-execute")
  public ApiResponse<TestExecutionService.BatchExecutionResult> batchExecute(
      @RequestParam("taskId") Long taskId) {
    return ApiResponse.ok(testExecutionService.startBatchExecution(taskId));
  }

  @GetMapping("/batch-executions/{batchId}")
  public ApiResponse<TestExecutionService.BatchExecutionResult> getBatch(
      @PathVariable("batchId") String batchId) {
    return ApiResponse.ok(testExecutionService.getBatchExecution(batchId));
  }
}
