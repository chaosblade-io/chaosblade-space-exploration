package com.chaosblade.svc.faultscheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Spec 规范化处理器 处理 JSON 到 YAML 的转换和 ChaosBlade CR 的规范化 */
@Component
public class SpecNormalizer {

  private static final Logger logger = LoggerFactory.getLogger(SpecNormalizer.class);

  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  /**
   * 规范化输入数据 入参可能是完整 CR 或仅有 spec；统一产出 {apiVersion,kind,metadata,spec}
   *
   * @param input 输入数据
   * @param bladeName 故障名称
   * @param labels 标签
   * @return 规范化后的 CR 数据
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> normalize(
      Map<String, Object> input, String bladeName, Map<String, String> labels) {
    logger.debug("Normalizing input for bladeName: {}", bladeName);
    logger.trace("Input data: {}", input);

    if (input == null || input.isEmpty()) {
      throw new IllegalArgumentException("Input data cannot be null or empty");
    }

    Map<String, Object> root = new LinkedHashMap<>(input);
    Map<String, Object> normalized = new LinkedHashMap<>();

    try {
      // 检查是否只传了 spec
      if (!root.containsKey("apiVersion") && root.containsKey("spec")) {
        logger.debug("Input contains only spec, adding CR headers");

        normalized.put("apiVersion", "chaosblade.io/v1alpha1");
        normalized.put("kind", "ChaosBlade");
        normalized.put("metadata", createMetadata(bladeName, labels));
        normalized.put("spec", root.get("spec"));

      } else {
        logger.debug("Input contains full CR, normalizing metadata");

        // 传了完整 CR：覆盖 metadata.name/合并 labels
        normalized.put("apiVersion", root.getOrDefault("apiVersion", "chaosblade.io/v1alpha1"));
        normalized.put("kind", root.getOrDefault("kind", "ChaosBlade"));

        // 处理 metadata
        Map<String, Object> metadata =
            (Map<String, Object>) root.getOrDefault("metadata", new LinkedHashMap<>());
        metadata.put("name", bladeName);

        // 合并 labels
        Map<String, String> mergedLabels = new LinkedHashMap<>(labels);
        Object existingLabels = metadata.get("labels");
        if (existingLabels instanceof Map<?, ?> labelMap) {
          labelMap.forEach((k, v) -> mergedLabels.put(String.valueOf(k), String.valueOf(v)));
        }
        metadata.put("labels", mergedLabels);

        normalized.put("metadata", metadata);
        normalized.put("spec", root.get("spec"));
      }

      // 验证 spec 不为空
      if (normalized.get("spec") == null) {
        throw new IllegalArgumentException("Spec cannot be null");
      }

      logger.debug("Successfully normalized input for bladeName: {}", bladeName);
      logger.trace("Normalized data: {}", normalized);

      return normalized;

    } catch (Exception e) {
      logger.error("Failed to normalize input for bladeName: {}", bladeName, e);
      throw new RuntimeException("Failed to normalize input data", e);
    }
  }

  /**
   * 创建标准的 metadata
   *
   * @param bladeName 故障名称
   * @param labels 标签
   * @return metadata 对象
   */
  private Map<String, Object> createMetadata(String bladeName, Map<String, String> labels) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("name", bladeName);
    metadata.put("labels", new LinkedHashMap<>(labels));
    return metadata;
  }

  /**
   * 将规范化后的数据转换为 YAML 字符串
   *
   * @param normalized 规范化后的数据
   * @return YAML 字符串
   * @throws JsonProcessingException 转换异常
   */
  public String toYaml(Map<String, Object> normalized) throws JsonProcessingException {
    try {
      logger.debug("Converting normalized data to YAML");

      String yaml = yamlMapper.writeValueAsString(normalized);

      logger.trace("Generated YAML: {}", yaml);
      return yaml;

    } catch (JsonProcessingException e) {
      logger.error("Failed to convert to YAML", e);
      throw e;
    }
  }

  /**
   * 将规范化后的数据转换为 JSON 字符串
   *
   * @param normalized 规范化后的数据
   * @return JSON 字符串
   * @throws JsonProcessingException 转换异常
   */
  public String toJson(Map<String, Object> normalized) throws JsonProcessingException {
    try {
      logger.debug("Converting normalized data to JSON");

      String json = jsonMapper.writeValueAsString(normalized);

      logger.trace("Generated JSON: {}", json);
      return json;

    } catch (JsonProcessingException e) {
      logger.error("Failed to convert to JSON", e);
      throw e;
    }
  }

  /**
   * 验证 ChaosBlade spec 的基本结构
   *
   * @param spec spec 数据
   * @return 是否有效
   */
  @SuppressWarnings("unchecked")
  public boolean validateSpec(Map<String, Object> spec) {
    if (spec == null || spec.isEmpty()) {
      logger.warn("Spec is null or empty");
      return false;
    }

    try {
      // 检查是否包含 experiments
      Object experiments = spec.get("experiments");
      if (experiments == null) {
        logger.warn("Spec missing 'experiments' field");
        return false;
      }

      // 如果 experiments 是 List，检查是否为空
      if (experiments instanceof java.util.List<?> expList) {
        if (expList.isEmpty()) {
          logger.warn("Experiments list is empty");
          return false;
        }

        // 检查每个实验的基本字段
        for (Object exp : expList) {
          if (exp instanceof Map<?, ?> expMap) {
            if (!expMap.containsKey("scope")
                || !expMap.containsKey("target")
                || !expMap.containsKey("action")) {
              logger.warn("Experiment missing required fields (scope, target, action)");
              return false;
            }
          }
        }
      }

      logger.debug("Spec validation passed");
      return true;

    } catch (Exception e) {
      logger.error("Error during spec validation", e);
      return false;
    }
  }

  /**
   * 从 JSON 字符串解析为 Map
   *
   * @param json JSON 字符串
   * @return 解析后的 Map
   * @throws JsonProcessingException 解析异常
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> fromJson(String json) throws JsonProcessingException {
    try {
      logger.debug("Parsing JSON string to Map");

      Map<String, Object> result = jsonMapper.readValue(json, Map.class);

      logger.trace("Parsed JSON: {}", result);
      return result;

    } catch (JsonProcessingException e) {
      logger.error("Failed to parse JSON", e);
      throw e;
    }
  }

  /**
   * 从 YAML 字符串解析为 Map
   *
   * @param yaml YAML 字符串
   * @return 解析后的 Map
   * @throws JsonProcessingException 解析异常
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> fromYaml(String yaml) throws JsonProcessingException {
    try {
      logger.debug("Parsing YAML string to Map");

      Map<String, Object> result = yamlMapper.readValue(yaml, Map.class);

      logger.trace("Parsed YAML: {}", result);
      return result;

    } catch (JsonProcessingException e) {
      logger.error("Failed to parse YAML", e);
      throw e;
    }
  }
}
