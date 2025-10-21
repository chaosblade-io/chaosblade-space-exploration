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

package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.SystemInfo;
import com.chaosblade.svc.topo.model.SystemListResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemListResponse测试类
 */
public class SystemListResponseTest {

    @Test
    public void testSystemListResponseCreation() {
        // 创建测试数据
        List<SystemInfo> systemInfos = new ArrayList<>();
        
        SystemInfo systemInfo1 = new SystemInfo();
        systemInfo1.setId(1L);
        systemInfo1.setKey("train-ticket");
        systemInfo1.setName("Train Ticket System");
        systemInfo1.setDescription("A microservice-based train ticket booking system");
        systemInfo1.setOwner("Chaos Engineering Team");
        systemInfo1.setDefaultEnvironment("development");
        
        SystemInfo systemInfo2 = new SystemInfo();
        systemInfo2.setId(2L);
        systemInfo2.setKey("online-banking");
        systemInfo2.setName("Online Banking System");
        systemInfo2.setDescription("A secure online banking platform");
        systemInfo2.setOwner("Financial Services Team");
        systemInfo2.setDefaultEnvironment("production");
        
        systemInfos.add(systemInfo1);
        systemInfos.add(systemInfo2);
        
        // 创建SystemListResponse对象
        SystemListResponse.SystemListData data = new SystemListResponse.SystemListData(systemInfos, systemInfos.size());
        SystemListResponse response = new SystemListResponse(true, data);
        
        // 验证结果
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().getTotal());
        assertEquals(2, response.getData().getItems().size());
        
        // 验证第一个系统信息
        SystemInfo firstSystem = response.getData().getItems().get(0);
        assertEquals(Long.valueOf(1L), firstSystem.getId());
        assertEquals("train-ticket", firstSystem.getKey());
        assertEquals("Train Ticket System", firstSystem.getName());
        assertEquals("A microservice-based train ticket booking system", firstSystem.getDescription());
        assertEquals("Chaos Engineering Team", firstSystem.getOwner());
        assertEquals("development", firstSystem.getDefaultEnvironment());
        
        // 验证第二个系统信息
        SystemInfo secondSystem = response.getData().getItems().get(1);
        assertEquals(Long.valueOf(2L), secondSystem.getId());
        assertEquals("online-banking", secondSystem.getKey());
        assertEquals("Online Banking System", secondSystem.getName());
        assertEquals("A secure online banking platform", secondSystem.getDescription());
        assertEquals("Financial Services Team", secondSystem.getOwner());
        assertEquals("production", secondSystem.getDefaultEnvironment());
    }
    
    @Test
    public void testSystemListResponseDefaultConstructor() {
        // 测试默认构造函数
        SystemListResponse response = new SystemListResponse();
        
        // 验证默认值
        assertNull(response.getSuccess());
        assertNull(response.getData());
        
        // 测试setter方法
        response.setSuccess(true);
        SystemListResponse.SystemListData data = new SystemListResponse.SystemListData();
        response.setData(data);
        
        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
    }
    
    @Test
    public void testSystemListDataDefaultConstructor() {
        // 测试SystemListData默认构造函数
        SystemListResponse.SystemListData data = new SystemListResponse.SystemListData();
        
        // 验证默认值
        assertNull(data.getItems());
        assertNull(data.getTotal());
        
        // 测试setter方法
        List<SystemInfo> items = new ArrayList<>();
        data.setItems(items);
        data.setTotal(5);
        
        assertNotNull(data.getItems());
        assertEquals(5, data.getTotal());
    }
}