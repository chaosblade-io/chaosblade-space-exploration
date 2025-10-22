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

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiQueryServiceCacheTest {

    private ApiQueryService apiQueryService;
    private TopologyGraph testTopology;

    @BeforeEach
    void setUp() {
        apiQueryService = new ApiQueryService();
        
        // 创建测试拓扑图
        testTopology = new TopologyGraph();
        
        // 创建实体
        Entity targetEntity = new Entity();
        targetEntity.setEntityId("rpc-oteldemo-checkoutservice-placeorder");
        targetEntity.setAppId("checkout@default");
        targetEntity.setType(EntityType.RPC);
        targetEntity.setDisplayName("rpc-oteldemo-checkoutservice-placeorder");
        
        // 创建节点
        Node targetNode = new Node("node-1", targetEntity);
        
        // 添加节点到拓扑图
        testTopology.addNode(targetNode);
    }

    @Test
    void testFindNodeByEntityIdWithCache() throws Exception {
        // 第一次查找，应该会遍历节点并填充缓存
        String entityId = "rpc-oteldemo-checkoutservice-placeorder";
        Node node1 = invokeFindNodeByEntityId(testTopology, entityId);
        assertNotNull(node1);
        assertEquals(entityId, node1.getEntity().getEntityId());
        
        // 检查缓存是否已填充
        Map<String, Node> cache = getEntityNodeCache();
        assertTrue(cache.containsKey(entityId));
        assertEquals(node1, cache.get(entityId));
        
        // 第二次查找，应该从缓存中获取
        Node node2 = invokeFindNodeByEntityId(testTopology, entityId);
        assertNotNull(node2);
        assertEquals(node1, node2);
        assertSame(node1, node2);
    }

    @Test
    void testFindNodeByEntityIdCacheMiss() throws Exception {
        // 查找不存在的节点
        Node node = invokeFindNodeByEntityId(testTopology, "non-existent-entity");
        assertNull(node);
        
        // 检查缓存是否未包含该实体ID
        Map<String, Node> cache = getEntityNodeCache();
        assertFalse(cache.containsKey("non-existent-entity"));
    }

    @Test
    void testCacheInvalidationWhenNodeRemoved() throws Exception {
        String entityId = "rpc-oteldemo-checkoutservice-placeorder";
        
        // 第一次查找，填充缓存
        Node node1 = invokeFindNodeByEntityId(testTopology, entityId);
        assertNotNull(node1);
        
        // 检查缓存
        Map<String, Node> cache = getEntityNodeCache();
        assertTrue(cache.containsKey(entityId));
        
        // 从拓扑图中移除节点
        testTopology.removeNode("node-1");
        
        // 再次查找，应该返回null并且缓存被清除
        Node node2 = invokeFindNodeByEntityId(testTopology, entityId);
        assertNull(node2);
        assertFalse(cache.containsKey(entityId));
    }

    /**
     * 通过反射调用私有方法 findNodeByEntityId
     */
    private Node invokeFindNodeByEntityId(TopologyGraph topology, String entityId) throws Exception {
        java.lang.reflect.Method method = ApiQueryService.class.getDeclaredMethod("findNodeByEntityId", TopologyGraph.class, String.class);
        method.setAccessible(true);
        return (Node) method.invoke(apiQueryService, topology, entityId);
    }

    /**
     * 通过反射获取 entityNodeCache
     */
    private Map<String, Node> getEntityNodeCache() throws Exception {
        Field field = ApiQueryService.class.getDeclaredField("entityNodeCache");
        field.setAccessible(true);
        return (Map<String, Node>) field.get(apiQueryService);
    }
}