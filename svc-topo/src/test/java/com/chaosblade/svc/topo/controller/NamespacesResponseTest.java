package com.chaosblade.svc.topo.controller;

import com.chaosblade.svc.topo.model.NamespacesResponse;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NamespacesResponse 单元测试类
 */
class NamespacesResponseTest {

    @Test
    void testDefaultConstructor() {
        NamespacesResponse response = new NamespacesResponse();
        assertNull(response.getNamespaces());
    }

    @Test
    void testConstructorWithNamespaces() {
        List<String> namespaces = Arrays.asList("namespace1", "namespace2", "namespace3");
        NamespacesResponse response = new NamespacesResponse(namespaces);
        
        assertNotNull(response.getNamespaces());
        assertEquals(3, response.getNamespaces().size());
        assertEquals("namespace1", response.getNamespaces().get(0));
        assertEquals("namespace2", response.getNamespaces().get(1));
        assertEquals("namespace3", response.getNamespaces().get(2));
    }

    @Test
    void testSetNamespaces() {
        NamespacesResponse response = new NamespacesResponse();
        List<String> namespaces = Arrays.asList("namespaceA", "namespaceB");
        
        response.setNamespaces(namespaces);
        
        assertNotNull(response.getNamespaces());
        assertEquals(2, response.getNamespaces().size());
        assertEquals("namespaceA", response.getNamespaces().get(0));
        assertEquals("namespaceB", response.getNamespaces().get(1));
    }

    @Test
    void testToString() {
        List<String> namespaces = Arrays.asList("test-namespace");
        NamespacesResponse response = new NamespacesResponse(namespaces);
        
        String toStringResult = response.toString();
        assertTrue(toStringResult.contains("test-namespace"));
        assertTrue(toStringResult.startsWith("NamespacesResponse{"));
    }
}