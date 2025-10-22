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

package com.chaosblade.svc.taskexecutor;

import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.service.FaultConfigQueryService;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class FaultConfigQueryServiceTest {

  @Autowired private FaultConfigQueryService faultConfigQueryService;

  @Test
  void testGetFaultConfigsByTaskId3() {
    Long taskId = 3L;
    List<ServiceFaultConfig> list = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
    // 允许为空，但不应抛异常
    Assertions.assertNotNull(list);
    // 如果存在配置，校验结构
    if (!list.isEmpty()) {
      ServiceFaultConfig item = list.get(0);
      Assertions.assertNotNull(item.getServiceName());
      Assertions.assertNotNull(item.getFaultConfig());
    }
  }
}
