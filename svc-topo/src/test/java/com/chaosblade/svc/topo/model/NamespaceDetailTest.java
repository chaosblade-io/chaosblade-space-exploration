package com.chaosblade.svc.topo.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NamespaceDetail 单元测试类
 */
class NamespaceDetailTest {

    @Test
    void testDefaultConstructor() {
        NamespaceDetail detail = new NamespaceDetail();
        assertNull(detail.getId());
        assertNull(detail.getSystemKey());
        assertNull(detail.getK8sNamespace());
        assertNull(detail.getName());
        assertNull(detail.getDescription());
        assertNull(detail.getOwner());
        assertNull(detail.getDefaultEnvironment());
    }

    @Test
    void testConstructorWithParameters() {
        NamespaceDetail detail = new NamespaceDetail(
                1L, 
                "train-ticket", 
                "default", 
                "订票系统", 
                "火车票订票系统（被测系统）", 
                "admin", 
                "prod"
        );
        
        assertEquals(1L, detail.getId());
        assertEquals("train-ticket", detail.getSystemKey());
        assertEquals("default", detail.getK8sNamespace());
        assertEquals("订票系统", detail.getName());
        assertEquals("火车票订票系统（被测系统）", detail.getDescription());
        assertEquals("admin", detail.getOwner());
        assertEquals("prod", detail.getDefaultEnvironment());
    }

    @Test
    void testSettersAndGetters() {
        NamespaceDetail detail = new NamespaceDetail();
        
        detail.setId(1L);
        detail.setSystemKey("train-ticket");
        detail.setK8sNamespace("default");
        detail.setName("订票系统");
        detail.setDescription("火车票订票系统（被测系统）");
        detail.setOwner("admin");
        detail.setDefaultEnvironment("prod");
        
        assertEquals(1L, detail.getId());
        assertEquals("train-ticket", detail.getSystemKey());
        assertEquals("default", detail.getK8sNamespace());
        assertEquals("订票系统", detail.getName());
        assertEquals("火车票订票系统（被测系统）", detail.getDescription());
        assertEquals("admin", detail.getOwner());
        assertEquals("prod", detail.getDefaultEnvironment());
    }

    @Test
    void testToString() {
        NamespaceDetail detail = new NamespaceDetail(
                1L, 
                "train-ticket", 
                "default", 
                "订票系统", 
                "火车票订票系统（被测系统）", 
                "admin", 
                "prod"
        );
        
        String toStringResult = detail.toString();
        assertTrue(toStringResult.contains("train-ticket"));
        assertTrue(toStringResult.contains("订票系统"));
        assertTrue(toStringResult.startsWith("NamespaceDetail{"));
    }
}