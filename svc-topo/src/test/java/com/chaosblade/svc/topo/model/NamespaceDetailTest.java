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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** NamespaceDetail 单元测试类 */
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
    NamespaceDetail detail =
        new NamespaceDetail(
            1L, "train-ticket", "default", "订票系统", "火车票订票系统（被测系统）", "admin", "prod");

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
    NamespaceDetail detail =
        new NamespaceDetail(
            1L, "train-ticket", "default", "订票系统", "火车票订票系统（被测系统）", "admin", "prod");

    String toStringResult = detail.toString();
    assertTrue(toStringResult.contains("train-ticket"));
    assertTrue(toStringResult.contains("订票系统"));
    assertTrue(toStringResult.startsWith("NamespaceDetail{"));
  }
}
