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
import com.chaosblade.svc.reqrspproxy.dto.FixtureUpsertRequest;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 基于 envoy/envoy-tap-filter.tmpl.yaml 的拦截配置渲染器 生成带有 direct_response 与 baggage 条件匹配的 Envoy 配置 */
@Component
public class FilterInterceptionConfigRenderer {
  private static final Logger logger =
      LoggerFactory.getLogger(FilterInterceptionConfigRenderer.class);
  private static final String TEMPLATE_PATH = "envoy/envoy-tap-filter.tmpl.yaml";

  @Autowired private RecordingConfig recordingConfig;

  private Mustache template;
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  @PostConstruct
  public void initTemplate() {
    try {
      ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
      InputStreamReader reader = new InputStreamReader(resource.getInputStream());
      template = mustacheFactory.compile(reader, "envoy-filter-config");
      logger.info("Envoy filter template loaded successfully from {}", TEMPLATE_PATH);
    } catch (IOException e) {
      logger.error(
          "Failed to load Envoy filter template from {}: {}", TEMPLATE_PATH, e.getMessage(), e);
      throw new RuntimeException("Failed to initialize Envoy filter template", e);
    }
  }

  /**
   * 渲染 Envoy 过滤拦截配置
   *
   * @param appPort 应用容器端口
   * @param items 请求中的规则项
   * @param tapEnabled 是否启用Tap（可选）
   * @param tapFilePrefix Tap输出前缀（可选）
   */
  public String render(
      int appPort,
      List<FixtureUpsertRequest.FixtureItem> items,
      boolean tapEnabled,
      String tapFilePrefix) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("At least one interception item is required");
    }

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("adminPort", recordingConfig.getEnvoy().getAdminPort());
    ctx.put("listenerPort", recordingConfig.getEnvoy().getPort());
    ctx.put("appPort", appPort);
    ctx.put("tapEnabled", tapEnabled);
    if (tapEnabled) {
      ctx.put(
          "tapFilePrefix",
          tapFilePrefix != null
              ? tapFilePrefix
              : (recordingConfig.getEnvoy().getTapDir() + "/fi-"));
    }

    List<Map<String, Object>> intercepts =
        items.stream().map(this::toInterceptMap).collect(Collectors.toList());
    ctx.put("intercepts", intercepts);
    ctx.put("multiIntercepts", intercepts.size() > 1);
    if (!intercepts.isEmpty()) {
      ctx.put("firstIntercept", intercepts.get(0));
    }

    StringWriter writer = new StringWriter();
    template.execute(writer, ctx);
    String yaml = writer.toString();
    logger.debug("Generated Envoy filter config:\n{}", yaml);
    return yaml;
  }

  private Map<String, Object> toInterceptMap(FixtureUpsertRequest.FixtureItem item) {
    Map<String, Object> m = new HashMap<>();
    m.put("method", safe(item.getMethod()).toUpperCase());
    m.put("path", safe(item.getPath()));
    m.put("pathSanitized", sanitizePath(item.getPath()));

    // baggageTokens: 任一匹配即命中。优先采用 safe_regex（OR）。如只一个，亦可 contains。
    List<String> tokens = Optional.ofNullable(item.getBaggageTokens()).orElse(List.of());
    if (tokens.size() == 1) {
      m.put("baggageContains", escapeYamlString(tokens.get(0)));
    } else if (!tokens.isEmpty()) {
      String orRegex = tokens.stream().map(this::escapeRegex).collect(Collectors.joining("|"));
      m.put("baggageRegex", orRegex);
    }

    // 响应信息（带校验与默认兜底）
    Integer statusIn = item.getRespStatus();
    int statusOut;
    if (statusIn == null || statusIn < 200 || statusIn >= 600) {
      statusOut = 500;
      logger.warn(
          "Invalid respStatus {} for interception rule (service={}, path={}), defaulting to 500",
          statusIn,
          item.getServiceName(),
          item.getPath());
    } else {
      statusOut = statusIn;
    }
    m.put("respStatus", statusOut);

    String contentType = "application/json;charset=UTF-8";
    Map<String, Object> rh = item.getRespHeaders();
    if (rh != null && !rh.isEmpty()) {
      contentType = getHeaderIgnoreCase(rh, "content-type", contentType);
    }
    m.put("respContentType", escapeYamlString(contentType));

    // body 直接作为 JSON 字符串嵌入（模板使用 {{&respBodyJson}} 原样输出）
    String body = Optional.ofNullable(item.getRespBody()).orElse("").trim();
    if (body.isEmpty()) {
      body =
          "{\"status\":0,\"msg\":\"Service temporarily unavailable due to interception"
              + " rule\",\"data\":null}";
      logger.warn(
          "Empty respBody for interception rule (service={}, path={}), defaulting to fallback JSON",
          item.getServiceName(),
          item.getPath());
    }
    m.put("respBodyJson", body);
    return m;
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }

  private String sanitizePath(String path) {
    if (path == null) return "root";
    return path.replaceAll("[^a-zA-Z0-9]", "-").replaceAll("-+", "-");
  }

  private String escapeYamlString(String str) {
    if (str == null) return "";
    return str.replace("\"", "'").replace("\n", " ").replace("\r", " ").replace("\t", " ");
  }

  private String escapeRegex(String s) {
    // 简单正则转义（仅处理常见特殊字符）
    return s.replace("\\", "\\\\")
        .replace(".", "\\.")
        .replace("+", "\\+")
        .replace("*", "\\*")
        .replace("?", "\\?")
        .replace("|", "\\|")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("^", "\\^")
        .replace("$", "\\$");
  }

  private String getHeaderIgnoreCase(Map<String, Object> headers, String key, String defaultValue) {
    if (headers == null || headers.isEmpty()) return defaultValue;
    for (Map.Entry<String, Object> e : headers.entrySet()) {
      String k = e.getKey();
      if (k != null && k.equalsIgnoreCase(key)) {
        String v = stringifyHeaderValue(e.getValue());
        return (v != null && !v.isEmpty()) ? v : defaultValue;
      }
    }
    return defaultValue;
  }

  private String stringifyHeaderValue(Object val) {
    if (val == null) return null;
    if (val instanceof java.util.Collection<?>) {
      for (Object x : (java.util.Collection<?>) val) {
        if (x != null) return String.valueOf(x);
      }
      return null;
    }
    if (val.getClass().isArray()) {
      Object[] arr = (Object[]) val;
      return arr.length > 0 && arr[0] != null ? String.valueOf(arr[0]) : null;
    }
    return String.valueOf(val);
  }

  public boolean isTemplateLoaded() {
    return template != null;
  }
}
