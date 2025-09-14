package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.InterceptionRule;
import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合配置渲染器 - 同时支持录制和拦截功能
 * 
 * 核心设计：
 * 1. Tap 过滤器在 Router 之前，确保所有请求都能被录制
 * 2. 拦截路由优先级高于转发路由
 * 3. 被拦截的请求也会被 Tap 记录（包含模拟响应）
 */
@Component
public class HybridConfigRenderer {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridConfigRenderer.class);
    
    @Autowired
    private RecordingConfig recordingConfig;
    
    /**
     * 渲染混合配置（录制 + 拦截）
     */
    public String renderHybridConfig(int appPort, 
                                   List<RecordingRule> recordingRules,
                                   List<InterceptionRule> interceptionRules) {
        
        logger.debug("Rendering hybrid config: appPort={}, recordingRules={}, interceptionRules={}", 
                    appPort, recordingRules.size(), interceptionRules.size());
        
        // 1. 生成拦截路由块
        String interceptRoutes = interceptionRules.stream()
                .map(this::renderInterceptRoute)
                .collect(Collectors.joining());
        
        // 2. 生成录制匹配规则块（包含所有需要录制的路径）
        String tapMatches = generateTapMatchConfig(recordingRules);
        
        // 3. 渲染完整配置
        String envoyYaml = String.format(HYBRID_ENVOY_TEMPLATE,
                recordingConfig.getEnvoy().getAdminPort(),    // admin port
                recordingConfig.getEnvoy().getPort(),         // listener port
                interceptRoutes,                              // 拦截路由
                tapMatches,                                   // Tap 匹配规则
                recordingConfig.getEnvoy().getTapDir(),       // tap 输出目录
                recordingConfig.getEnvoy().getMaxBufferedBytes(), // max rx bytes
                recordingConfig.getEnvoy().getMaxBufferedBytes(), // max tx bytes
                appPort);                                     // 应用端口
        
        logger.debug("Generated hybrid Envoy config:\n{}", envoyYaml);
        return envoyYaml;
    }
    
    /**
     * 渲染单个拦截路由
     */
    private String renderInterceptRoute(InterceptionRule rule) {
        String routeName = "intercept-" + Math.abs(rule.getPath().hashCode());
        
        // 构建响应头
        String responseHeaders = rule.getMockResponse().getHeaders().entrySet().stream()
                .map(entry -> String.format(
                    "                    - header: { key: \"%s\", value: \"%s\" }",
                    escapeYamlString(entry.getKey()),
                    escapeYamlString(entry.getValue())))
                .collect(Collectors.joining("\n"));
        
        // 如果没有自定义响应头，添加默认的 content-type
        if (responseHeaders.isEmpty()) {
            String contentType = rule.getMockResponse().getContentType();
            responseHeaders = String.format(
                "                    - header: { key: \"content-type\", value: \"%s\" }",
                contentType);
        }
        
        return String.format(INTERCEPT_ROUTE_TEMPLATE,
                routeName,
                escapeYamlString(rule.getPath()),
                rule.getMethod().toUpperCase(),
                rule.getMockResponse().getStatusCode(),
                escapeYamlString(rule.getMockResponse().getBody()),
                responseHeaders);
    }
    
    /**
     * 生成 Tap 匹配配置
     * 处理单个规则和多个规则的不同格式
     */
    private String generateTapMatchConfig(List<RecordingRule> recordingRules) {
        if (recordingRules.isEmpty()) {
            return ""; // 没有规则时返回空
        }

        if (recordingRules.size() == 1) {
            // 单个规则时直接使用 http_request_headers_match
            RecordingRule rule = recordingRules.get(0);
            return String.format(SINGLE_TAP_MATCH_TEMPLATE,
                    escapeYamlString(rule.getPath()),
                    rule.getMethod().toUpperCase());
        } else {
            // 多个规则时使用 or_match
            String tapMatches = recordingRules.stream()
                    .map(this::renderTapMatch)
                    .collect(Collectors.joining());
            return String.format(OR_MATCH_TAP_TEMPLATE, tapMatches);
        }
    }

    /**
     * 渲染单个 Tap 匹配规则（用于 or_match 中）
     */
    private String renderTapMatch(RecordingRule rule) {
        return String.format(TAP_MATCH_TEMPLATE,
                escapeYamlString(rule.getPath()),
                rule.getMethod().toUpperCase());
    }
    
    /**
     * YAML 字符串转义
     * 对于 inline_string，我们需要更安全的转义方式
     */
    private String escapeYamlString(String str) {
        if (str == null) return "";

        // 对于 JSON 字符串，使用单引号包围或者 Base64 编码
        // 这里使用简单的字符替换，避免双引号转义问题
        return str.replace("\"", "'")  // 将双引号替换为单引号
                  .replace("\n", " ")   // 将换行替换为空格
                  .replace("\r", " ")   // 将回车替换为空格
                  .replace("\t", " ");  // 将制表符替换为空格
    }
    
    // === 配置模板 ===
    
    private static final String HYBRID_ENVOY_TEMPLATE = """
            admin:
              address:
                socket_address: { address: 0.0.0.0, port_value: %d }
            
            static_resources:
              listeners:
              - name: inbound
                address:
                  socket_address: { address: 0.0.0.0, port_value: %d }
                filter_chains:
                - filters:
                  - name: envoy.filters.network.http_connection_manager
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                      stat_prefix: ingress_http
                      http2_protocol_options: {}
                      
                      route_config:
                        name: local_route
                        virtual_hosts:
                        - name: local_service
                          domains: ["*"]
                          routes:
            %s
                          - match: { prefix: "/" }
                            route: { cluster: local_app }
            
                      http_filters:
                      - name: envoy.filters.http.tap
                        typed_config:
                          "@type": type.googleapis.com/envoy.extensions.filters.http.tap.v3.Tap
                          common_config:
                            static_config:
                              match_config:
            %s
                              output_config:
                                sinks:
                                - format: JSON_BODY_AS_STRING
                                  file_per_tap:
                                    path_prefix: %s/rec-
                                max_buffered_rx_bytes: %d
                                max_buffered_tx_bytes: %d
                      - name: envoy.filters.http.router
                        typed_config:
                          "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
            
              clusters:
              - name: local_app
                connect_timeout: 1s
                type: STATIC
                load_assignment:
                  cluster_name: local_app
                  endpoints:
                  - lb_endpoints:
                    - endpoint:
                        address:
                          socket_address:
                            address: 127.0.0.1
                            port_value: %d
            """;
    
    private static final String INTERCEPT_ROUTE_TEMPLATE = """
                          - name: %s
                            match:
                              path: "%s"
                              headers:
                              - name: ":method"
                                exact_match: "%s"
                            direct_response:
                              status: %d
                              body:
                                inline_string: "%s"
                            response_headers_to_add:
            %s
            """;
    
    private static final String TAP_MATCH_TEMPLATE = """
                                  - http_request_headers_match:
                                      headers:
                                      - name: :path
                                        exact_match: "%s"
                                      - name: :method
                                        exact_match: "%s"
            """;

    // 单个规则的 Tap 匹配模板
    private static final String SINGLE_TAP_MATCH_TEMPLATE = """
                                http_request_headers_match:
                                  headers:
                                  - name: :path
                                    exact_match: "%s"
                                  - name: :method
                                    exact_match: "%s"
            """;

    // 多个规则的 or_match 模板
    private static final String OR_MATCH_TAP_TEMPLATE = """
                                or_match:
                                  rules:
            %s
            """;
}
