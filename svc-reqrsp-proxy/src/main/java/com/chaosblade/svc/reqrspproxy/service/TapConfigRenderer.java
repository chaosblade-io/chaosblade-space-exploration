package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import com.chaosblade.svc.reqrspproxy.dto.RecordingRule;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Envoy Tap 配置渲染器 */
@Component
public class TapConfigRenderer {

  private static final Logger logger = LoggerFactory.getLogger(TapConfigRenderer.class);

  @Autowired private RecordingConfig recordingConfig;

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
    String matchBlocks = rules.stream().map(this::renderMatchRule).collect(Collectors.joining());

    String envoyYaml =
        String.format(
            ENVOY_CONFIG_TEMPLATE,
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

  /** 渲染单个匹配规则 注意：对于 URL 路径匹配，我们总是使用前缀匹配，因为 exact 匹配太严格 */
  private String renderMatchRule(RecordingRule rule) {
    // 所有路径都使用前缀匹配，因为这样可以捕获更多相关请求
    return String.format(
        PREFIX_MATCH_RULE_TEMPLATE, rule.getPath(), rule.getMethod().toUpperCase());
  }

  private static final String PREFIX_MATCH_RULE_TEMPLATE =
      """
                            - http_request_headers_match:
                                headers:
                                - name: ":path"
                                  string_match: { prefix: "%s" }
                                - name: ":method"
                                  string_match: { exact: "%s" }
      """;

  private static final String ENVOY_CONFIG_TEMPLATE =
      """
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
                    - match: { prefix: "/" }
                      route: { cluster: local_app }
                http_filters:
                - name: envoy.filters.http.tap
                  typed_config:
                    "@type": type.googleapis.com/envoy.extensions.filters.http.tap.v3.Tap
                    common_config:
                      static_config:
                        match_config:
                          or_match:
                            rules:
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
}
