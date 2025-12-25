package com.chaosblade.svc.faultscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
        Map<String, Object> experiment = createExperiment("container", "network", "delay");
        Map<String, Object> spec = createSpec(Collections.singletonList(experiment));
        Map<String, Object> input = createInputWithSpec(spec);

        String bladeName = "test-fault";
        Map<String, String> labels = createLabels("test", "true");
        
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
        Map<String, Object> input = new HashMap<>();
        input.put("apiVersion", "chaosblade.io/v1alpha1");
        input.put("kind", "ChaosBlade");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "old-name");
        metadata.put("labels", createLabels("old", "label"));
        input.put("metadata", metadata);

        Map<String, Object> experiment = createExperiment("pod", "cpu", "fullload");
        Map<String, Object> spec = createSpec(Collections.singletonList(experiment));
        input.put("spec", spec);

        String bladeName = "new-fault";
        Map<String, String> labels = createLabels("new", "label");
        
        // 执行测试
        Map<String, Object> result = specNormalizer.normalize(input, bladeName, labels);

        // 验证结果
        assertEquals("chaosblade.io/v1alpha1", result.get("apiVersion"));
        assertEquals("ChaosBlade", result.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMetadata = (Map<String, Object>) result.get("metadata");
        assertEquals(bladeName, resultMetadata.get("name")); // 应该被覆盖

        @SuppressWarnings("unchecked")
        Map<String, String> mergedLabels = (Map<String, String>) resultMetadata.get("labels");
        assertEquals("label", mergedLabels.get("new"));
        assertEquals("label", mergedLabels.get("old")); // 应该被合并

        assertEquals(input.get("spec"), result.get("spec"));
    }

    @Test
    void testNormalizeWithNullInput() {
        String bladeName = "test-fault";
        Map<String, String> labels = createLabels("test", "true");
        
        assertThrows(IllegalArgumentException.class, () -> {
            specNormalizer.normalize(null, bladeName, labels);
        });
    }
    
    @Test
    void testNormalizeWithEmptyInput() {
        String bladeName = "test-fault";
        Map<String, String> labels = createLabels("test", "true");

        assertThrows(IllegalArgumentException.class, () -> {
            specNormalizer.normalize(new HashMap<>(), bladeName, labels);
        });
    }

    @Test
    void testNormalizeWithMissingSpec() {
        Map<String, Object> input = new HashMap<>();
        input.put("other", "value");
        String bladeName = "test-fault";
        Map<String, String> labels = createLabels("test", "true");

        assertThrows(RuntimeException.class, () -> {
            specNormalizer.normalize(input, bladeName, labels);
        });
    }

    @Test
    void testToYaml() throws JsonProcessingException {
        Map<String, Object> emptySpec = createSpec(Collections.emptyList());
        Map<String, Object> normalized = createNormalized("chaosblade.io/v1alpha1", "ChaosBlade", "test", emptySpec);
        
        String yaml = specNormalizer.toYaml(normalized);
        
        assertNotNull(yaml);
        assertTrue(yaml.contains("apiVersion: chaosblade.io/v1alpha1"));
        assertTrue(yaml.contains("kind: ChaosBlade"));
        assertTrue(yaml.contains("name: test"));
    }
    
    @Test
    void testToJson() throws JsonProcessingException {
        Map<String, Object> emptySpec = createSpec(Collections.emptyList());
        Map<String, Object> normalized = createNormalized("chaosblade.io/v1alpha1", "ChaosBlade", "test", emptySpec);

        String json = specNormalizer.toJson(normalized);

        assertNotNull(json);
        assertTrue(json.contains("\"apiVersion\":\"chaosblade.io/v1alpha1\""));
        assertTrue(json.contains("\"kind\":\"ChaosBlade\""));
        assertTrue(json.contains("\"name\":\"test\""));
    }

    @Test
    void testValidateSpecValid() {
        Map<String, Object> experiment = createExperiment("container", "network", "delay");
        Map<String, Object> validSpec = createSpec(Collections.singletonList(experiment));

        assertTrue(specNormalizer.validateSpec(validSpec));
    }

    @Test
    void testValidateSpecInvalid() {
        // 测试空 spec
        assertFalse(specNormalizer.validateSpec(null));
        assertFalse(specNormalizer.validateSpec(new HashMap<>()));

        // 测试缺少 experiments
        Map<String, Object> noExpSpec = new HashMap<>();
        noExpSpec.put("other", "value");
        assertFalse(specNormalizer.validateSpec(noExpSpec));
        
        // 测试空 experiments
        assertFalse(specNormalizer.validateSpec(createSpec(Collections.emptyList())));

        // 测试缺少必需字段的 experiment
        Map<String, Object> incompleteExperiment = new HashMap<>();
        incompleteExperiment.put("scope", "container"); // 缺少 target 和 action
        Map<String, Object> invalidSpec = createSpec(Collections.singletonList(incompleteExperiment));
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

    // 辅助方法：创建实验配置
    private Map<String, Object> createExperiment(String scope, String target, String action) {
        Map<String, Object> experiment = new HashMap<>();
        experiment.put("scope", scope);
        experiment.put("target", target);
        experiment.put("action", action);
        return experiment;
    }

    // 辅助方法：创建 spec
    private Map<String, Object> createSpec(List<Map<String, Object>> experiments) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("experiments", experiments);
        return spec;
    }

    // 辅助方法：创建 input 带 spec
    private Map<String, Object> createInputWithSpec(Map<String, Object> spec) {
        Map<String, Object> input = new HashMap<>();
        input.put("spec", spec);
        return input;
    }

    // 辅助方法：创建 labels
    private Map<String, String> createLabels(String key, String value) {
        Map<String, String> labels = new HashMap<>();
        labels.put(key, value);
        return labels;
    }

    // 辅助方法：创建 normalized CR
    private Map<String, Object> createNormalized(String apiVersion, String kind, String name, Map<String, Object> spec) {
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("apiVersion", apiVersion);
        normalized.put("kind", kind);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        normalized.put("metadata", metadata);
        normalized.put("spec", spec);
        return normalized;
    }
}
