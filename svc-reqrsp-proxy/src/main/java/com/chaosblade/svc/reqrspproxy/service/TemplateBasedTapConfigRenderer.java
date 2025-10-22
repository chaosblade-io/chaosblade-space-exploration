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

package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 基于外部模板的 Envoy Tap 配置渲染器 使用 Mustache 模板引擎处理动态内容 */
@Component
public class TemplateBasedTapConfigRenderer {

  private static final Logger logger =
      LoggerFactory.getLogger(TemplateBasedTapConfigRenderer.class);
  private static final String TEMPLATE_PATH = "envoy/envoy-tap-record.tmpl.yaml";

  @Autowired private RecordingConfig recordingConfig;

  private Mustache template;
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  @PostConstruct
  public void initTemplate() {
    try {
      ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
      InputStreamReader reader = new InputStreamReader(resource.getInputStream());
      template = mustacheFactory.compile(reader, "envoy-config");
      logger.info("Envoy template loaded successfully from {}", TEMPLATE_PATH);
    } catch (IOException e) {
      logger.error("Failed to load Envoy template from {}: {}", TEMPLATE_PATH, e.getMessage(), e);
      throw new RuntimeException("Failed to initialize Envoy template", e);
    }
  }

  /**
   * 渲染 Envoy 配置 YAML
   *
   * @param appPort 应用端口
   * @param rules 录制规则
   * @return Envoy 配置 YAML 字符串
   */
  public String render(int appPort, List<RecordingRule> rules) {
    logger.debug("Rendering Envoy config for app port {} with {} rules", appPort, rules.size());

    if (rules == null || rules.isEmpty()) {
      throw new IllegalArgumentException("At least one recording rule is required");
    }

    // 构建模板上下文
    Map<String, Object> context = buildTemplateContext(appPort, rules);

    StringWriter writer = new StringWriter();
    template.execute(writer, context);
    String envoyYaml = writer.toString();

    logger.debug("Generated Envoy config:\n{}", envoyYaml);
    return envoyYaml;
  }

  /** 构建模板渲染上下文 */
  private Map<String, Object> buildTemplateContext(int appPort, List<RecordingRule> rules) {
    Map<String, Object> context = new HashMap<>();

    // 基础配置参数
    context.put("adminPort", recordingConfig.getEnvoy().getAdminPort());
    context.put("envoyPort", recordingConfig.getEnvoy().getPort());
    context.put("appPort", appPort);
    context.put("tapDir", recordingConfig.getEnvoy().getTapDir());
    context.put("tapPrefix", "rec-");
    context.put("maxBufferedBytes", recordingConfig.getEnvoy().getMaxBufferedBytes());

    // 转换规则为模板友好格式
    List<Map<String, Object>> ruleList =
        rules.stream().map(this::convertRuleToMap).collect(Collectors.toList());

    // 处理 Envoy 验证要求：or_match.rules 必须至少有 2 个项目
    // 如果只有一个规则，使用 and_match 而不是 or_match
    context.put("rules", ruleList);
    context.put("useSingleRule", ruleList.size() == 1);
    context.put("useMultipleRules", ruleList.size() > 1);

    logger.debug(
        "Template context: adminPort={}, envoyPort={}, appPort={}, rules={}, useSingleRule={}",
        context.get("adminPort"),
        context.get("envoyPort"),
        appPort,
        ruleList.size(),
        context.get("useSingleRule"));

    return context;
  }

  /** 将 RecordingRule 转换为模板映射 */
  private Map<String, Object> convertRuleToMap(RecordingRule rule) {
    Map<String, Object> ruleMap = new HashMap<>();
    ruleMap.put("path", rule.getPath());
    ruleMap.put("method", rule.getMethod().toUpperCase());
    // 对于 URL 路径匹配，总是使用前缀匹配
    ruleMap.put("isPrefix", true);
    return ruleMap;
  }

  /** 验证模板是否正确加载 */
  public boolean isTemplateLoaded() {
    return template != null;
  }
}
