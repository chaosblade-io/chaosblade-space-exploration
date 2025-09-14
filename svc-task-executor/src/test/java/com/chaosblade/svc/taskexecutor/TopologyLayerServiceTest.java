package com.chaosblade.svc.taskexecutor;

import com.chaosblade.svc.taskexecutor.service.TopologyLayerService;
import com.chaosblade.svc.taskexecutor.service.TopologyLayerService.Layer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest
public class TopologyLayerServiceTest {

    @Autowired
    private TopologyLayerService topologyLayerService;

    @Test
    void testGetLayersByTaskId3_basicInvariants() {
        Long taskId = 3L;
        List<Layer> layers = topologyLayerService.getLayersByTaskId(taskId);
        Assertions.assertNotNull(layers, "layers should not be null");
        Assertions.assertFalse(layers.isEmpty(), "layers should not be empty for taskId=3");

        // assert indexes are increasing from 1
        for (int i = 0; i < layers.size(); i++) {
            Assertions.assertEquals(i + 1, layers.get(i).getIndex(), "layer index should start at 1 and increase by 1");
            Assertions.assertNotNull(layers.get(i).getNodes(), "each layer must have nodes list");
        }

        // assert no duplicate node ids across layers
        Set<Long> seen = new HashSet<>();
        for (Layer layer : layers) {
            layer.getNodes().forEach(n -> {
                Assertions.assertTrue(seen.add(n.getId()), "node id should appear in exactly one layer: " + n.getId());
            });
        }
    }
}

