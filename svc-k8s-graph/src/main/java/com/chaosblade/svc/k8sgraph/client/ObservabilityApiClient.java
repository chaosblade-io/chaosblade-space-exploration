package com.chaosblade.svc.k8sgraph.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 可观测性 API 客户端
 * 调用 TraceList、TraceDetail、Servicemap 等外部 API
 */
@Component
public class ObservabilityApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityApiClient.class);

    @Value("${observability.api.base-url:http://1.94.151.57:8003}")
    private String baseUrl;

    @Value("${observability.api.project-id:f21z1y9i}")
    private String projectId;

    @Value("${observability.api.cookie:st-device-id=74e953f4-42f0-4700-822f-eb560a7cd086; _lfa=LF1.1.9c9770d065654d55.1754920867795; _ga=GA1.1.303269026.1759975533; st-session-id=76c7afb6-adc7-4a3f-b020-f1bf80b3276d; _ga_K4F9D90KG7=GS2.1.s1766632212$o1$g1$t1766632874$j56$l0$h0}")
    private String authCookie;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建带 Cookie 认证的请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", authCookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    /**
     * 发送 GET 请求
     */
    private ResponseEntity<String> doGet(String url) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }
    
    /**
     * 获取服务调用拓扑图
     */
    public JsonNode getServiceMap(long fromMs, long toMs) {
        String url = String.format("%s/api/project/%s/overview/map", baseUrl, projectId);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", fromMs)
                .queryParam("to", toMs);

        logger.info("Fetching service map: from={}, to={}", fromMs, toMs);

        try {
            ResponseEntity<String> response = doGet(builder.toUriString());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // 返回 data.applications.map
                return root.path("data").path("applications").path("map");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch service map: {}", e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取服务的 Trace 列表
     */
    public JsonNode getTraceList(String serviceName, long fromMs, long toMs) {
        String url = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            // 构建 query 参数
            Map<String, Object> query = new HashMap<>();
            query.put("view", "traces");

            // 构建 filter 对象
            Map<String, String> filter = new HashMap<>();
            filter.put("field", "ServiceName");
            filter.put("op", "=");
            filter.put("value", serviceName);
            query.put("filters", new Object[]{ filter });

            String queryJson = objectMapper.writeValueAsString(query);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("query", encodedQuery)
                    .queryParam("from", fromMs)
                    .queryParam("to", toMs);

            logger.info("Fetching trace list for service {}: from={}, to={}", serviceName, fromMs, toMs);

            ResponseEntity<String> response = doGet(builder.toUriString());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // 返回 data.traces.trace
                return root.path("data").path("traces").path("trace");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace list for service {}: {}", serviceName, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取 Trace 详情（不需要时间范围）
     * traceId 已经唯一标识了特定的 trace
     */
    public JsonNode getTraceDetail(String traceId) {
        String url = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            // 构建 query 参数，包含 trace_id
            Map<String, Object> query = new HashMap<>();
            query.put("view", "traces");
            query.put("filters", new Object[]{});
            query.put("trace_id", traceId);

            String queryJson = objectMapper.writeValueAsString(query);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());

            // 使用一个较大的时间范围（最近30天）
            long now = System.currentTimeMillis();
            long thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000;

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("query", encodedQuery)
                    .queryParam("from", thirtyDaysAgo)
                    .queryParam("to", now);

            logger.info("Fetching trace detail for traceId {}", traceId);

            ResponseEntity<String> response = doGet(builder.toUriString());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // 返回 data.traces.trace
                return root.path("data").path("traces").path("trace");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace detail for traceId {}: {}", traceId, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取 Trace 详情（带时间范围，向后兼容）
     */
    public JsonNode getTraceDetail(String traceId, long fromMs, long toMs) {
        return getTraceDetail(traceId);
    }
}

