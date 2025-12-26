package com.chaosblade.svc.k8sgraph.client;

import com.chaosblade.svc.k8sgraph.domain.trace.TraceRequestFilter;
import com.chaosblade.svc.k8sgraph.domain.trace.TraceRequestQuery;
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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

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

    @Value("${observability.api.cookie:sid=ab46c3ac87b2e6b30a0eba82d0be33f3; coroot_session=eyJpZCI6MX0=.L4mM7jLoBFBkvEE7K27aKr_F8eGhGd2l5pJMZyjr2Rk=}")
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
     * 发送 GET 请求（使用 URL 字符串）
     */
    private ResponseEntity<String> doGet(String url) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    /**
     * 发送 GET 请求（使用 URI 对象，避免二次编码）
     */
    private ResponseEntity<String> doGetWithUri(URI uri) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
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
        String baseApiUrl = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            // 使用 TraceRequestQuery 构建请求
            TraceRequestQuery traceRequestQuery = new TraceRequestQuery();
            traceRequestQuery.setView("traces");

            TraceRequestFilter filter = new TraceRequestFilter("ServiceName", "=", serviceName);
            traceRequestQuery.setFilters(Collections.singletonList(filter));

            String queryJson = objectMapper.writeValueAsString(traceRequestQuery);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());

            // 直接拼接 URL，避免 UriComponentsBuilder 的二次编码
            String requestUrl = baseApiUrl + "?query=" + encodedQuery + "&from=" + fromMs + "&to=" + toMs;

            logger.info("Fetching trace list for service {}: from={}, to={}", serviceName, fromMs, toMs);
            logger.info("Request URL: {}", requestUrl);
            logger.info("Query JSON (before encoding): {}", queryJson);

            // 使用 URI 对象发起请求，避免再次编码
            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));

            logger.info("Response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // 调试：输出 data.traces 下的所有字段
                JsonNode tracesNode = root.path("data").path("traces");
                if (!tracesNode.isMissingNode()) {
                    StringBuilder fields = new StringBuilder();
                    tracesNode.fieldNames().forEachRemaining(f -> fields.append(f).append(", "));
                    logger.info("data.traces fields: {}", fields.toString());
                }

                // 根据参考代码，可能是 data.traces.traces
                JsonNode traceListNode = root.path("data").path("traces").path("traces");
                logger.info("Found traces at: data.traces.traces, isArray: {}, size: {}",
                    traceListNode.isArray(), traceListNode.size());

                return traceListNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace list for service {}: {}", serviceName, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取 Trace 详情
     * @param traceId trace ID
     * @return trace 详情的 span 数组
     */
    public JsonNode getTraceDetail(String traceId) {
        String baseApiUrl = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            TraceRequestQuery traceRequestQuery = new TraceRequestQuery();
            traceRequestQuery.setView("traces");
            traceRequestQuery.setFilters(Collections.emptyList());
            traceRequestQuery.setTraceId(traceId);

            String queryJson = objectMapper.writeValueAsString(traceRequestQuery);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());

            String requestUrl = baseApiUrl + "?query=" + encodedQuery;

            logger.info("Fetching trace detail for traceId: {}", traceId);
            logger.info("Request URL: {}", requestUrl);

            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));
            logger.info("Response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode traceNode = root.path("data").path("traces").path("trace");
                logger.info("Found trace detail at: data.traces.trace, isArray: {}, size: {}",
                    traceNode.isArray(), traceNode.size());
                return traceNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace detail for traceId {}: {}", traceId, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }
}