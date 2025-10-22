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

package com.chaosblade.svc.faultscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpecNormalizer 单元测试
 */
class SpecNormalizerTest {
    
    private SpecNormalizer specNormalizer;
    
    @BeforeEach
    void setUp() {
        specNormalizer = new SpecNormalizer();
    }
    
    @Test
    void testNormalizeWithSpecOnly() {
        // 准备测试数据 - 只有 spec
        Map<String, Object> input = Map.of(
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "container",
                                        "target", "network",
                                        "action", "delay"
                                )
                        )
                )
        );
        
        String bladeName = "test-fault";
        Map<String, String> labels = Map.of("test", "true");
        
        // 执行测试
        Map<String, Object> result = specNormalizer.normalize(input, bladeName, labels);
        
        // 验证结果
        assertEquals("chaosblade.io/v1alpha1", result.get("apiVersion"));
        assertEquals("ChaosBlade", result.get("kind"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertEquals(bladeName, metadata.get("name"));
        assertEquals(labels, metadata.get("labels"));
        
        assertEquals(input.get("spec"), result.get("spec"));
    }
    
    @Test
    void testNormalizeWithFullCR() {
        // 准备测试数据 - 完整 CR
        Map<String, Object> input = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of(
                        "name", "old-name",
                        "labels", Map.of("old", "label")
                ),
                "spec", Map.of(
                        "experiments", List.of(
                                Map.of(
                                        "scope", "pod",
                                        "target", "cpu",
                                        "action", "fullload"
                                )
                        )
                )
        );
        
        String bladeName = "new-fault";
        Map<String, String> labels = Map.of("new", "label");
        
        // 执行测试
        Map<String, Object> result = specNormalizer.normalize(input, bladeName, labels);
        
        // 验证结果
        assertEquals("chaosblade.io/v1alpha1", result.get("apiVersion"));
        assertEquals("ChaosBlade", result.get("kind"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertEquals(bladeName, metadata.get("name")); // 应该被覆盖
        
        @SuppressWarnings("unchecked")
        Map<String, String> mergedLabels = (Map<String, String>) metadata.get("labels");
        assertEquals("label", mergedLabels.get("new"));
        assertEquals("label", mergedLabels.get("old")); // 应该被合并
        
        assertEquals(input.get("spec"), result.get("spec"));
    }
    
    @Test
    void testNormalizeWithNullInput() {
        String bladeName = "test-fault";
        Map<String, String> labels = Map.of("test", "true");
        
        assertThrows(IllegalArgumentException.class, () -> {
            specNormalizer.normalize(null, bladeName, labels);
        });
    }
    
    @Test
    void testNormalizeWithEmptyInput() {
        String bladeName = "test-fault";
        Map<String, String> labels = Map.of("test", "true");
        
        assertThrows(IllegalArgumentException.class, () -> {
            specNormalizer.normalize(Map.of(), bladeName, labels);
        });
    }
    
    @Test
    void testNormalizeWithMissingSpec() {
        Map<String, Object> input = Map.of("other", "value");
        String bladeName = "test-fault";
        Map<String, String> labels = Map.of("test", "true");
        
        assertThrows(RuntimeException.class, () -> {
            specNormalizer.normalize(input, bladeName, labels);
        });
    }
    
    @Test
    void testToYaml() throws JsonProcessingException {
        Map<String, Object> normalized = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of("name", "test"),
                "spec", Map.of("experiments", List.of())
        );
        
        String yaml = specNormalizer.toYaml(normalized);
        
        assertNotNull(yaml);
        assertTrue(yaml.contains("apiVersion: chaosblade.io/v1alpha1"));
        assertTrue(yaml.contains("kind: ChaosBlade"));
        assertTrue(yaml.contains("name: test"));
    }
    
    @Test
    void testToJson() throws JsonProcessingException {
        Map<String, Object> normalized = Map.of(
                "apiVersion", "chaosblade.io/v1alpha1",
                "kind", "ChaosBlade",
                "metadata", Map.of("name", "test"),
                "spec", Map.of("experiments", List.of())
        );
        
        String json = specNormalizer.toJson(normalized);
        
        assertNotNull(json);
        assertTrue(json.contains("\"apiVersion\":\"chaosblade.io/v1alpha1\""));
        assertTrue(json.contains("\"kind\":\"ChaosBlade\""));
        assertTrue(json.contains("\"name\":\"test\""));
    }
    
    @Test
    void testValidateSpecValid() {
        Map<String, Object> validSpec = Map.of(
                "experiments", List.of(
                        Map.of(
                                "scope", "container",
                                "target", "network",
                                "action", "delay"
                        )
                )
        );
        
        assertTrue(specNormalizer.validateSpec(validSpec));
    }
    
    @Test
    void testValidateSpecInvalid() {
        // 测试空 spec
        assertFalse(specNormalizer.validateSpec(null));
        assertFalse(specNormalizer.validateSpec(Map.of()));
        
        // 测试缺少 experiments
        assertFalse(specNormalizer.validateSpec(Map.of("other", "value")));
        
        // 测试空 experiments
        assertFalse(specNormalizer.validateSpec(Map.of("experiments", List.of())));
        
        // 测试缺少必需字段的 experiment
        Map<String, Object> invalidSpec = Map.of(
                "experiments", List.of(
                        Map.of("scope", "container") // 缺少 target 和 action
                )
        );
        assertFalse(specNormalizer.validateSpec(invalidSpec));
    }
    
    @Test
    void testFromJson() throws JsonProcessingException {
        String json = "{\"apiVersion\":\"chaosblade.io/v1alpha1\",\"kind\":\"ChaosBlade\"}";
        
        Map<String, Object> result = specNormalizer.fromJson(json);
        
        assertEquals("chaosblade.io/v1alpha1", result.get("apiVersion"));
        assertEquals("ChaosBlade", result.get("kind"));
    }
    
    @Test
    void testFromJsonInvalid() {
        String invalidJson = "{invalid json}";
        
        assertThrows(JsonProcessingException.class, () -> {
            specNormalizer.fromJson(invalidJson);
        });
    }
    
    @Test
    void testFromYaml() throws JsonProcessingException {
        String yaml = "apiVersion: chaosblade.io/v1alpha1\nkind: ChaosBlade\n";
        
        Map<String, Object> result = specNormalizer.fromYaml(yaml);
        
        assertEquals("chaosblade.io/v1alpha1", result.get("apiVersion"));
        assertEquals("ChaosBlade", result.get("kind"));
    }
    
    @Test
    void testFromYamlInvalid() {
        String invalidYaml = "invalid: yaml: content: [unclosed";
        
        assertThrows(JsonProcessingException.class, () -> {
            specNormalizer.fromYaml(invalidYaml);
        });
    }
}
