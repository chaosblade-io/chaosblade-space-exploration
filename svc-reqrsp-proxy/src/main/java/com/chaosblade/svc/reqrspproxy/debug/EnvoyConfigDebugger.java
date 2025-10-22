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

package com.chaosblade.svc.reqrspproxy.debug;

import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import com.chaosblade.svc.reqrspproxy.service.TemplateBasedTapConfigRenderer;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Envoy 配置调试工具 用于验证生成的 YAML 配置是否正确 */
@Component
public class EnvoyConfigDebugger implements CommandLineRunner {

  private static final Logger logger = LoggerFactory.getLogger(EnvoyConfigDebugger.class);

  @Autowired private TemplateBasedTapConfigRenderer renderer;

  @Override
  public void run(String... args) throws Exception {
    if (args.length > 0 && "debug-envoy".equals(args[0])) {
      debugEnvoyConfig();
    }
  }

  public void debugEnvoyConfig() {
    logger.info("=== Envoy 配置调试开始 ===");

    try {
      // 创建测试规则
      RecordingRule rule1 = new RecordingRule();
      rule1.setPath("/api/v1/orderservice/order/tickets");
      rule1.setMethod("POST");

      RecordingRule rule2 = new RecordingRule();
      rule2.setPath("/api/v1/orderservice/order");
      rule2.setMethod("GET");

      List<RecordingRule> rules = Arrays.asList(rule1, rule2);
      int appPort = 12031;

      // 生成配置
      String yamlContent = renderer.render(appPort, rules);

      logger.info("生成的 Envoy 配置:");
      logger.info("==========================================");
      logger.info("\n{}", yamlContent);
      logger.info("==========================================");

      // 验证 YAML 语法
      try {
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(yamlContent);
        logger.info("✅ YAML 语法验证通过");

        // 检查关键字段
        validateYamlContent(yamlContent);

      } catch (Exception e) {
        logger.error("❌ YAML 语法验证失败: {}", e.getMessage(), e);

        // 输出带行号的内容以便调试
        String[] lines = yamlContent.split("\n");
        logger.error("带行号的 YAML 内容:");
        for (int i = 0; i < lines.length; i++) {
          logger.error("{:3d}: {}", i + 1, lines[i]);
        }
      }

    } catch (Exception e) {
      logger.error("配置生成失败: {}", e.getMessage(), e);
    }

    logger.info("=== Envoy 配置调试结束 ===");
  }

  private void validateYamlContent(String yamlContent) {
    logger.info("验证 YAML 内容结构...");

    // 检查必要的配置项
    String[] requiredItems = {
      "admin:",
      "static_resources:",
      "listeners:",
      "clusters:",
      "envoy.filters.network.http_connection_manager",
      "envoy.filters.http.tap",
      "envoy.filters.http.router",
      "http_request_headers_match:",
      "exact_match:",
      "file_per_tap:",
      "path_prefix:"
    };

    for (String item : requiredItems) {
      if (yamlContent.contains(item)) {
        logger.info("✅ 包含必要配置: {}", item);
      } else {
        logger.warn("⚠️  缺少配置项: {}", item);
      }
    }

    // 检查规则数量
    long ruleCount =
        yamlContent
            .lines()
            .filter(line -> line.trim().contains("http_request_headers_match:"))
            .count();
    logger.info("✅ 检测到 {} 个录制规则", ruleCount);

    // 检查端口配置
    if (yamlContent.contains("port_value: 9901")) {
      logger.info("✅ Admin 端口配置正确");
    }
    if (yamlContent.contains("port_value: 15006")) {
      logger.info("✅ Envoy 监听端口配置正确");
    }
    if (yamlContent.contains("port_value: 12031")) {
      logger.info("✅ 应用端口配置正确");
    }
  }

  /** 手动调用调试方法（用于测试） */
  public static void main(String[] args) {
    // 这个方法可以在开发时手动调用进行调试
    System.out.println("请在 Spring Boot 应用中使用 --debug-envoy 参数运行此调试器");
  }
}
