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

package com.chaosblade.svc.topo.model;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NamespaceListResponse 单元测试类
 */
class NamespaceListResponseTest {

    @Test
    void testDefaultConstructor() {
        NamespaceListResponse response = new NamespaceListResponse();
        assertNull(response.getSuccess());
        assertNull(response.getData());
    }

    @Test
    void testConstructorWithParameters() {
        // 创建测试数据
        NamespaceDetail detail1 = new NamespaceDetail(1L, "train-ticket", "default", "订票系统", "描述1", "admin", "prod");
        NamespaceDetail detail2 = new NamespaceDetail(2L, "payment-system", "default", "支付系统", "描述2", "admin", "test");
        List<NamespaceDetail> items = Arrays.asList(detail1, detail2);
        
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(items, 2);
        NamespaceListResponse response = new NamespaceListResponse(true, data);
        
        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().getItems().size());
        assertEquals(2, response.getData().getTotal());
        assertEquals("订票系统", response.getData().getItems().get(0).getName());
        assertEquals("支付系统", response.getData().getItems().get(1).getName());
    }

    @Test
    void testSettersAndGetters() {
        NamespaceListResponse response = new NamespaceListResponse();
        
        // 创建测试数据
        NamespaceDetail detail = new NamespaceDetail(1L, "train-ticket", "default", "订票系统", "描述", "admin", "prod");
        List<NamespaceDetail> items = Arrays.asList(detail);
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(items, 1);
        
        response.setSuccess(true);
        response.setData(data);
        
        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getItems().size());
        assertEquals(1, response.getData().getTotal());
        assertEquals("订票系统", response.getData().getItems().get(0).getName());
    }

    @Test
    void testNamespaceListDataDefaultConstructor() {
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData();
        assertNull(data.getItems());
        assertNull(data.getTotal());
    }

    @Test
    void testNamespaceListDataConstructorWithParameters() {
        NamespaceDetail detail = new NamespaceDetail(1L, "train-ticket", "default", "订票系统", "描述", "admin", "prod");
        List<NamespaceDetail> items = Arrays.asList(detail);
        
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(items, 1);
        
        assertNotNull(data.getItems());
        assertEquals(1, data.getItems().size());
        assertEquals(1, data.getTotal());
        assertEquals("订票系统", data.getItems().get(0).getName());
    }

    @Test
    void testNamespaceListDataSettersAndGetters() {
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData();
        
        NamespaceDetail detail = new NamespaceDetail(1L, "train-ticket", "default", "订票系统", "描述", "admin", "prod");
        List<NamespaceDetail> items = Arrays.asList(detail);
        
        data.setItems(items);
        data.setTotal(1);
        
        assertNotNull(data.getItems());
        assertEquals(1, data.getItems().size());
        assertEquals(1, data.getTotal());
        assertEquals("订票系统", data.getItems().get(0).getName());
    }

    @Test
    void testToString() {
        // 测试NamespaceDetail toString
        NamespaceDetail detail = new NamespaceDetail(1L, "train-ticket", "default", "订票系统", "描述", "admin", "prod");
        String detailToString = detail.toString();
        assertTrue(detailToString.contains("train-ticket"));
        assertTrue(detailToString.contains("订票系统"));
        assertTrue(detailToString.startsWith("NamespaceDetail{"));

        // 测试NamespaceListData toString
        List<NamespaceDetail> items = Arrays.asList(detail);
        NamespaceListResponse.NamespaceListData data = new NamespaceListResponse.NamespaceListData(items, 1);
        String dataToString = data.toString();
        assertTrue(dataToString.contains("订票系统"));
        assertTrue(dataToString.startsWith("NamespaceListData{"));

        // 测试NamespaceListResponse toString
        NamespaceListResponse response = new NamespaceListResponse(true, data);
        String responseToString = response.toString();
        assertTrue(responseToString.contains("true"));
        assertTrue(responseToString.contains("订票系统"));
        assertTrue(responseToString.startsWith("NamespaceListResponse{"));
    }
}