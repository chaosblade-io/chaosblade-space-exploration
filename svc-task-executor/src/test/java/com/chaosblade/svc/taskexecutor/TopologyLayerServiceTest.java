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

import com.chaosblade.svc.taskexecutor.service.TopologyLayerService;
import com.chaosblade.svc.taskexecutor.service.TopologyLayerService.Layer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TopologyLayerServiceTest {

  @Autowired private TopologyLayerService topologyLayerService;

  @Test
  void testGetLayersByTaskId3_basicInvariants() {
    Long taskId = 3L;
    List<Layer> layers = topologyLayerService.getLayersByTaskId(taskId);
    Assertions.assertNotNull(layers, "layers should not be null");
    Assertions.assertFalse(layers.isEmpty(), "layers should not be empty for taskId=3");

    // assert indexes are increasing from 1
    for (int i = 0; i < layers.size(); i++) {
      Assertions.assertEquals(
          i + 1, layers.get(i).getIndex(), "layer index should start at 1 and increase by 1");
      Assertions.assertNotNull(layers.get(i).getNodes(), "each layer must have nodes list");
    }

    // assert no duplicate node ids across layers
    Set<Long> seen = new HashSet<>();
    for (Layer layer : layers) {
      layer
          .getNodes()
          .forEach(
              n -> {
                Assertions.assertTrue(
                    seen.add(n.getId()),
                    "node id should appear in exactly one layer: " + n.getId());
              });
    }
  }
}
