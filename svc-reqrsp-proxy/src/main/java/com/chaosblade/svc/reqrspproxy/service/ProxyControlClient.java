package com.chaosblade.svc.reqrspproxy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 调用 proxy-agent Control API 的客户端。
 * 使用 Spring RestTemplate（兼容 Java 8）。
 */
@Component
public class ProxyControlClient {

    private static final Logger logger = LoggerFactory.getLogger(ProxyControlClient.class);

    @Autowired
    private ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 设置代理模式（passthrough/record/replay/intercept）
     */
    public Map<String, Object> setMode(String podIp, int controlPort, String mode) {
        String url = controlUrl(podIp, controlPort, "/control/mode");
        String body = String.format("{\"mode\":\"%s\"}", mode);
        return exchange(url, HttpMethod.PUT, body);
    }

    /**
     * 获取所有录制快照
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSnapshots(String podIp, int controlPort) {
        String url = controlUrl(podIp, controlPort, "/control/snapshots");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readValue(response.getBody(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.error("Failed to get snapshots from {}:{}", podIp, controlPort, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清空所有快照
     */
    public void clearSnapshots(String podIp, int controlPort) {
        String url = controlUrl(podIp, controlPort, "/control/snapshots");
        try {
            restTemplate.delete(url);
        } catch (Exception e) {
            logger.error("Failed to clear snapshots: {}", e.getMessage());
        }
    }

    /**
     * 添加拦截规则
     */
    public Map<String, Object> addRule(String podIp, int controlPort,
                                       String pathMatch, String method,
                                       int statusCode, String responseBody) {
        String url = controlUrl(podIp, controlPort, "/control/rules");
        try {
            // Build rule JSON manually (Java 8 compatible)
            String body = String.format(
                    "{\"path_match\":\"%s\",\"method\":\"%s\",\"response\":{\"status_code\":%d,\"body\":\"%s\"}}",
                    pathMatch, method, statusCode, responseBody.replace("\"", "\\\""));
            return exchange(url, HttpMethod.POST, body);
        } catch (Exception e) {
            logger.error("Failed to add rule: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 添加带 baggage 匹配的拦截规则
     */
    public Map<String, Object> addRuleWithBaggage(String podIp, int controlPort,
                                                   String pathMatch, String method,
                                                   int statusCode, String responseBody,
                                                   String baggageMatch) {
        String url = controlUrl(podIp, controlPort, "/control/rules");
        try {
            String escapedBody = responseBody != null ? responseBody.replace("\\", "\\\\").replace("\"", "\\\"") : "";
            StringBuilder sb = new StringBuilder();
            sb.append("{\"path_match\":\"").append(pathMatch).append("\"");
            sb.append(",\"method\":\"").append(method).append("\"");
            if (baggageMatch != null && !baggageMatch.isEmpty()) {
                sb.append(",\"baggage_match\":\"").append(baggageMatch).append("\"");
            }
            sb.append(",\"response\":{\"status_code\":").append(statusCode);
            sb.append(",\"body\":\"").append(escapedBody).append("\"}}");
            return exchange(url, HttpMethod.POST, sb.toString());
        } catch (Exception e) {
            logger.error("Failed to add rule with baggage: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 添加带 Base64 body 的拦截规则（用于 gRPC protobuf 响应重放）
     */
    public Map<String, Object> addRuleWithBase64Body(String podIp, int controlPort,
                                                      String pathMatch, String method,
                                                      int statusCode, String bodyBase64,
                                                      String baggageMatch) {
        String url = controlUrl(podIp, controlPort, "/control/rules");
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"path_match\":\"").append(pathMatch).append("\"");
            sb.append(",\"method\":\"").append(method).append("\"");
            if (baggageMatch != null && !baggageMatch.isEmpty()) {
                sb.append(",\"baggage_match\":\"").append(baggageMatch).append("\"");
            }
            sb.append(",\"response\":{\"status_code\":").append(statusCode);
            if (bodyBase64 != null && !bodyBase64.isEmpty()) {
                sb.append(",\"body_base64\":\"").append(bodyBase64).append("\"");
            }
            sb.append("}}");
            return exchange(url, HttpMethod.POST, sb.toString());
        } catch (Exception e) {
            logger.error("Failed to add rule with base64 body: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 删除拦截规则
     */
    public void removeRule(String podIp, int controlPort, String ruleId) {
        String url = controlUrl(podIp, controlPort, "/control/rules/" + ruleId);
        try {
            restTemplate.delete(url);
        } catch (Exception e) {
            logger.error("Failed to remove rule: {}", e.getMessage());
        }
    }

    /**
     * 获取运行统计
     */
    public Map<String, Object> getStats(String podIp, int controlPort) {
        String url = controlUrl(podIp, controlPort, "/control/stats");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readValue(response.getBody(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.error("Failed to get stats: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 健康检查
     */
    public boolean isHealthy(String podIp, int controlPort) {
        String url = controlUrl(podIp, controlPort, "/control/health");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody() != null && response.getBody().contains("\"ok\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private String controlUrl(String podIp, int controlPort, String path) {
        return String.format("http://%s:%d%s", podIp, controlPort, path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(String url, HttpMethod method, String body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            if (response.getBody() != null) {
                return objectMapper.readValue(response.getBody(),
                        new TypeReference<Map<String, Object>>() {});
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            logger.error("{} {} failed: {}", method, url, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
