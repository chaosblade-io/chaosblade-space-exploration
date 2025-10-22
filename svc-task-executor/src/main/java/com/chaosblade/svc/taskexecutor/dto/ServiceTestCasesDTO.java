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

/** 某个服务的所有测试用例集合 */
public class ServiceTestCasesDTO {
  private String serviceName;
  private List<TestCaseDTO> cases;

  public ServiceTestCasesDTO() {}

  public ServiceTestCasesDTO(String serviceName, List<TestCaseDTO> cases) {
    this.serviceName = serviceName;
    this.cases = cases;
  }

  public String getServiceName() {
    return serviceName;
  }

  public List<TestCaseDTO> getCases() {
    return cases;
  }
}
