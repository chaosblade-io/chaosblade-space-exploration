package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.dto.InterceptionRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 纯拦截配置渲染器
 * 生成只包含拦截功能的 Envoy 配置，不包含录制（Tap）功能
 */
@Component
public class PureInterceptionConfigRenderer {
    
    private static final Logger logger = LoggerFactory.getLogger(PureInterceptionConfigRenderer.class);
    
    /**
     * 渲染纯拦截 Envoy 配置
     */
    public String renderPureInterceptionConfig(int appPort, List<InterceptionRule> interceptionRules) {
        logger.info("Rendering pure interception config for {} rules", interceptionRules.size());
        
        // 1. 生成拦截路由
        String interceptRoutes = interceptionRules.stream()
                .map(this::renderInterceptRoute)
                .collect(Collectors.joining());

        // 2. 渲染完整配置
        return String.format(PURE_INTERCEPTION_TEMPLATE, interceptRoutes, appPort);
    }
    
    /**
     * 渲染单个拦截路由
     */
    private String renderInterceptRoute(InterceptionRule rule) {
        String routeName = "intercept-" + Math.abs((rule.getPath() + rule.getMethod()).hashCode());
        String responseHeaders = renderResponseHeaders(rule);
        
        return String.format(INTERCEPT_ROUTE_TEMPLATE,
                routeName,
                escapeYamlString(rule.getPath()),
                rule.getMethod().toUpperCase(),
                rule.getMockResponse().getStatusCode(),
                escapeYamlString(rule.getMockResponse().getBody()),
                responseHeaders);
    }
    
    /**
     * 渲染响应头
     */
    private String renderResponseHeaders(InterceptionRule rule) {
        StringBuilder headers = new StringBuilder();
        
        // 添加 Content-Type
        if (rule.getMockResponse().getContentType() != null) {
            headers.append(String.format(RESPONSE_HEADER_TEMPLATE, 
                    "content-type", rule.getMockResponse().getContentType()));
        }
        
        // 添加自定义头
        if (rule.getMockResponse().getHeaders() != null) {
            rule.getMockResponse().getHeaders().forEach((key, value) -> {
                headers.append(String.format(RESPONSE_HEADER_TEMPLATE, 
                        escapeYamlString(key), escapeYamlString(value)));
            });
        }
        
        return headers.toString();
    }
    
    /**
     * YAML 字符串转义
     */
    private String escapeYamlString(String str) {
        if (str == null) return "";
        
        return str.replace("\"", "'")  // 将双引号替换为单引号
                  .replace("\n", " ")   // 将换行替换为空格
                  .replace("\r", " ")   // 将回车替换为空格
                  .replace("\t", " ");  // 将制表符替换为空格
    }
    
    // ==================== 模板定义 ====================

    private static final String PURE_INTERCEPTION_TEMPLATE =
            "admin:\n" +
            "  address:\n" +
            "    socket_address: { address: 0.0.0.0, port_value: 9901 }\n" +
            "\n" +
            "static_resources:\n" +
            "  listeners:\n" +
            "  - name: inbound\n" +
            "    address:\n" +
            "      socket_address: { address: 0.0.0.0, port_value: 15006 }\n" +
            "    filter_chains:\n" +
            "    - filters:\n" +
            "      - name: envoy.filters.network.http_connection_manager\n" +
            "        typed_config:\n" +
            "          \"@type\": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager\n" +
            "          stat_prefix: ingress_http\n" +
            "          http2_protocol_options: {}\n" +
            "          \n" +
            "          route_config:\n" +
            "            name: local_route\n" +
            "            virtual_hosts:\n" +
            "            - name: local_service\n" +
            "              domains: [\"*\"]\n" +
            "              routes:\n" +
            "%s\n" +
            "              - match: { prefix: \"/\" }\n" +
            "                route: { cluster: local_app }\n" +
            "          \n" +
            "          http_filters:\n" +
            "          - name: envoy.filters.http.router\n" +
            "            typed_config:\n" +
            "              \"@type\": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router\n" +
            "\n" +
            "  clusters:\n" +
            "  - name: local_app\n" +
            "    connect_timeout: 1s\n" +
            "    type: STATIC\n" +
            "    load_assignment:\n" +
            "      cluster_name: local_app\n" +
            "      endpoints:\n" +
            "      - lb_endpoints:\n" +
            "        - endpoint:\n" +
            "            address:\n" +
            "              socket_address:\n" +
            "                address: 127.0.0.1\n" +
            "                port_value: %d\n";

    private static final String INTERCEPT_ROUTE_TEMPLATE =
            "              - name: %s\n" +
            "                match:\n" +
            "                  path: \"%s\"\n" +
            "                  headers:\n" +
            "                  - name: \":method\"\n" +
            "                    exact_match: \"%s\"\n" +
            "                direct_response:\n" +
            "                  status: %d\n" +
            "                  body:\n" +
            "                    inline_string: \"%s\"\n" +
            "                response_headers_to_add:\n" +
            "%s\n";

    private static final String RESPONSE_HEADER_TEMPLATE =
            "                    - header: { key: \"%s\", value: \"%s\" }\n";
}
