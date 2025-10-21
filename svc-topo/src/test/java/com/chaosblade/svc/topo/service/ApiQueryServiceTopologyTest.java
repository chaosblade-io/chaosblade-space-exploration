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

package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.TopologyByApiRequest;
import com.chaosblade.svc.topo.model.entity.*;
import com.chaosblade.svc.topo.model.topology.TopologyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiQueryServiceTopologyTest {

    private ApiQueryService apiQueryService;
    private TopologyGraph testTopology;

    @BeforeEach
    void setUp() {
        apiQueryService = new ApiQueryService();
        
        // 创建测试拓扑图
        testTopology = new TopologyGraph();
        
        // 创建实体
        Entity upstreamEntity = new Entity();
        upstreamEntity.setEntityId("upstream-service");
        upstreamEntity.setAppId("upstream@default");
        upstreamEntity.setType(EntityType.SERVICE);
        upstreamEntity.setDisplayName("Upstream Service");
        
        Entity targetEntity = new Entity();
        targetEntity.setEntityId("rpc-oteldemo-checkoutservice-placeorder");
        targetEntity.setAppId("checkout@default");
        targetEntity.setType(EntityType.RPC);
        targetEntity.setDisplayName("rpc-oteldemo-checkoutservice-placeorder");
        
        Entity downstreamEntity1 = new Entity();
        downstreamEntity1.setEntityId("downstream-service-1");
        downstreamEntity1.setAppId("payment@default");
        downstreamEntity1.setType(EntityType.SERVICE);
        downstreamEntity1.setDisplayName("Payment Service");
        
        Entity downstreamEntity2 = new Entity();
        downstreamEntity2.setEntityId("downstream-service-2");
        downstreamEntity2.setAppId("inventory@default");
        downstreamEntity2.setType(EntityType.SERVICE);
        downstreamEntity2.setDisplayName("Inventory Service");
        
        // 创建节点
        Node upstreamNode = new Node("node-1", upstreamEntity);
        Node targetNode = new Node("node-2", targetEntity);
        Node downstreamNode1 = new Node("node-3", downstreamEntity1);
        Node downstreamNode2 = new Node("node-4", downstreamEntity2);
        
        // 添加节点到拓扑图
        testTopology.addNode(upstreamNode);
        testTopology.addNode(targetNode);
        testTopology.addNode(downstreamNode1);
        testTopology.addNode(downstreamNode2);
        
        // 创建边
        Edge upstreamEdge = new Edge("edge-1", "node-1", "node-2", RelationType.INVOKES);
        Edge downstreamEdge1 = new Edge("edge-2", "node-2", "node-3", RelationType.INVOKES);
        Edge downstreamEdge2 = new Edge("edge-3", "node-2", "node-4", RelationType.INVOKES);
        
        // 添加边到拓扑图
        testTopology.addEdge(upstreamEdge);
        testTopology.addEdge(downstreamEdge1);
        testTopology.addEdge(downstreamEdge2);
    }

    @Test
    void testQueryTopologyByApiId() {
        // 创建请求
        TopologyByApiRequest request = new TopologyByApiRequest();
        request.setNamespace("default");
        request.setApiId("rpc-oteldemo-checkoutservice-placeorder");
        
        // 执行查询
        TopologyGraph result = apiQueryService.queryTopologyByApiId(testTopology, request);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(4, result.getNodes().size()); // 目标节点 + 1个上游节点 + 2个下游节点
        assertEquals(3, result.getEdges().size()); // 1个上游边 + 2个下游边
        
        // 验证包含目标节点
        boolean hasTargetNode = result.getNodes().stream()
            .anyMatch(node -> "rpc-oteldemo-checkoutservice-placeorder".equals(node.getEntity().getEntityId()));
        assertTrue(hasTargetNode);
        
        // 验证包含上游节点
        boolean hasUpstreamNode = result.getNodes().stream()
            .anyMatch(node -> "upstream-service".equals(node.getEntity().getEntityId()));
        assertTrue(hasUpstreamNode);
        
        // 验证包含下游节点
        boolean hasDownstreamNode1 = result.getNodes().stream()
            .anyMatch(node -> "downstream-service-1".equals(node.getEntity().getEntityId()));
        assertTrue(hasDownstreamNode1);
        
        boolean hasDownstreamNode2 = result.getNodes().stream()
            .anyMatch(node -> "downstream-service-2".equals(node.getEntity().getEntityId()));
        assertTrue(hasDownstreamNode2);
    }

    @Test
    void testQueryTopologyByApiIdWithNonExistentApi() {
        // 创建请求
        TopologyByApiRequest request = new TopologyByApiRequest();
        request.setNamespace("default");
        request.setApiId("non-existent-api");
        
        // 执行查询
        TopologyGraph result = apiQueryService.queryTopologyByApiId(testTopology, request);
        
        // 验证结果为空
        assertNotNull(result);
        assertEquals(0, result.getNodes().size());
        assertEquals(0, result.getEdges().size());
    }
}