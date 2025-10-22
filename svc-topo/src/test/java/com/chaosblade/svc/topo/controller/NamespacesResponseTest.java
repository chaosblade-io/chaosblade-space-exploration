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

import static org.junit.jupiter.api.Assertions.*;

import com.chaosblade.svc.topo.model.NamespacesResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** NamespacesResponse 单元测试类 */
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
