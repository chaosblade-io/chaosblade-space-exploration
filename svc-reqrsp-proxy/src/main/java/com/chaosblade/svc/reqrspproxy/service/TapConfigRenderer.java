package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Envoy Tap 配置渲染器
 */
@Component
public class TapConfigRenderer {
    
    private static final Logger logger = LoggerFactory.getLogger(TapConfigRenderer.class);
    
    @Autowired
    private RecordingConfig recordingConfig;
    
    /**
     * 渲染 Envoy 配置 YAML
     * 
     * @param appPort 应用端口
     * @param rules 录制规则
     * @return Envoy 配置 YAML 字符串
     */
    public String render(int appPort, List<RecordingRule> rules) {
        logger.debug("Rendering Envoy config for app port {} with {} rules", appPort, rules.size());
        
        // 生成匹配规则块
        String matchBlocks = rules.stream()
                .map(this::renderMatchRule)
                .collect(Collectors.joining());
        
        String envoyYaml = String.format(ENVOY_CONFIG_TEMPLATE, 
                recordingConfig.getEnvoy().getAdminPort(),
                recordingConfig.getEnvoy().getPort(),
                matchBlocks,
                recordingConfig.getEnvoy().getTapDir(),
                recordingConfig.getEnvoy().getMaxBufferedBytes(),
                recordingConfig.getEnvoy().getMaxBufferedBytes(),
                appPort);
        
        logger.debug("Generated Envoy config:\n{}", envoyYaml);
        return envoyYaml;
    }
    
    /**
     * 渲染单个匹配规则
     * 注意：对于 URL 路径匹配，我们总是使用前缀匹配，因为 exact 匹配太严格
     */
    private String renderMatchRule(RecordingRule rule) {
        // 所有路径都使用前缀匹配，因为这样可以捕获更多相关请求
        return String.format(PREFIX_MATCH_RULE_TEMPLATE,
                rule.getPath(),
                rule.getMethod().toUpperCase());
    }
    
    private static final String PREFIX_MATCH_RULE_TEMPLATE =
            "                      - http_request_headers_match:\n" +
            "                          headers:\n" +
            "                          - name: \":path\"\n" +
            "                            string_match: { prefix: \"%s\" }\n" +
            "                          - name: \":method\"\n" +
            "                            string_match: { exact: \"%s\" }\n";

    private static final String ENVOY_CONFIG_TEMPLATE =
            "admin:\n" +
            "  address:\n" +
            "    socket_address: { address: 0.0.0.0, port_value: %d }\n" +
            "\n" +
            "static_resources:\n" +
            "  listeners:\n" +
            "  - name: inbound\n" +
            "    address:\n" +
            "      socket_address: { address: 0.0.0.0, port_value: %d }\n" +
            "    filter_chains:\n" +
            "    - filters:\n" +
            "      - name: envoy.filters.network.http_connection_manager\n" +
            "        typed_config:\n" +
            "          \"@type\": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager\n" +
            "          stat_prefix: ingress_http\n" +
            "          http2_protocol_options: {}\n" +
            "          route_config:\n" +
            "            name: local_route\n" +
            "            virtual_hosts:\n" +
            "            - name: local_service\n" +
            "              domains: [\"*\"]\n" +
            "              routes:\n" +
            "              - match: { prefix: \"/\" }\n" +
            "                route: { cluster: local_app }\n" +
            "          http_filters:\n" +
            "          - name: envoy.filters.http.tap\n" +
            "            typed_config:\n" +
            "              \"@type\": type.googleapis.com/envoy.extensions.filters.http.tap.v3.Tap\n" +
            "              common_config:\n" +
            "                static_config:\n" +
            "                  match_config:\n" +
            "                    or_match:\n" +
            "                      rules:\n" +
            "%s\n" +
            "                  output_config:\n" +
            "                    sinks:\n" +
            "                    - format: JSON_BODY_AS_STRING\n" +
            "                      file_per_tap:\n" +
            "                        path_prefix: %s/rec-\n" +
            "                    max_buffered_rx_bytes: %d\n" +
            "                    max_buffered_tx_bytes: %d\n" +
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
}
