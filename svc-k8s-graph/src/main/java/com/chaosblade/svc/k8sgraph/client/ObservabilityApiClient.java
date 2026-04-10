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

    @Value("${observability.api.base-url:http://116.63.51.45:30800}")
    private String baseUrl;

    @Value("${observability.api.project-id:60hjkbo3}")
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
                // 返回 data.map (不是 data.applications.map)
                JsonNode mapNode = root.path("data").path("map");
                logger.info("Fetched service map, isArray: {}, size: {}",
                    mapNode.isArray(), mapNode.isArray() ? mapNode.size() : 0);
                return mapNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch service map: {}", e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取服务的 Trace 列表
     * 使用 /api/project/{projectId}/app/{appId}/tracing 端点
     */
    public JsonNode getTraceList(String serviceName, long fromMs, long toMs) {
        return getTraceList(null, serviceName, fromMs, toMs);
    }

    /**
     * 获取指定命名空间下服务的 Trace 列表
     */
    public JsonNode getTraceList(String namespace, String serviceName, long fromMs, long toMs) {
        String appId = buildAppId(namespace, serviceName);
        String encodedAppId;
        try {
            encodedAppId = URLEncoder.encode(appId, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            encodedAppId = appId.replace(":", "%3A");
        }
        
        String url = String.format("%s/api/project/%s/app/%s/tracing", baseUrl, projectId, encodedAppId);

        try {
            String requestUrl = url + "?trace=otel::-:-:&from=" + fromMs + "&to=" + toMs;

            logger.info("Fetching trace list for service {}: from={}, to={}", serviceName, fromMs, toMs);
            logger.debug("Request URL: {}", requestUrl);

            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode spansNode = root.path("data").path("spans");
                logger.info("Found {} spans for service {}",
                    spansNode.isArray() ? spansNode.size() : 0, serviceName);
                return spansNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace list for service {}: {}", serviceName, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 构建应用 ID
     * 格式: projectId:namespace:Deployment:serviceName
     */
    private String buildAppId(String namespace, String serviceName) {
        String ns = (namespace != null && !namespace.isEmpty()) ? namespace : "default";
        return projectId + ":" + ns + ":Deployment:" + serviceName;
    }

    /**
     * 获取指定服务的Trace统计摘要
     * 返回各API的请求率、错误率、延迟分位数等统计信息
     *
     * @param serviceName 服务名
     * @param fromMs 开始时间戳
     * @param toMs 结束时间戳
     * @return data.traces.summary.stats 节点
     */
    public JsonNode getTraceSummary(String serviceName, long fromMs, long toMs) {
        String baseApiUrl = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            TraceRequestQuery query = new TraceRequestQuery();
            query.setView("overview");
            query.setFilters(Collections.singletonList(
                new TraceRequestFilter("ServiceName", "=", serviceName)
            ));

            String queryJson = objectMapper.writeValueAsString(query);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());
            String requestUrl = baseApiUrl + "?query=" + encodedQuery + "&from=" + fromMs + "&to=" + toMs;

            logger.info("Fetching trace summary for service {}: from={}, to={}", serviceName, fromMs, toMs);
            logger.debug("Request URL: {}", requestUrl);

            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode statsNode = root.path("data").path("traces").path("summary").path("stats");
                logger.info("Found {} API stats for service {}",
                    statsNode.isArray() ? statsNode.size() : 0, serviceName);
                return statsNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace summary for service {}: {}", serviceName, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取指定服务和API的具体Trace列表
     *
     * @param serviceName 服务名
     * @param spanName API名称（span名称）
     * @param fromMs 开始时间戳
     * @param toMs 结束时间戳
     * @return data.traces.traces 节点
     */
    public JsonNode getTraceItemList(String serviceName, String spanName, long fromMs, long toMs) {
        String baseApiUrl = String.format("%s/api/project/%s/overview/traces", baseUrl, projectId);

        try {
            TraceRequestQuery query = new TraceRequestQuery();
            query.setView("traces");
            query.setFilters(java.util.Arrays.asList(
                new TraceRequestFilter("ServiceName", "=", serviceName),
                new TraceRequestFilter("SpanName", "=", spanName)
            ));

            String queryJson = objectMapper.writeValueAsString(query);
            String encodedQuery = URLEncoder.encode(queryJson, StandardCharsets.UTF_8.toString());
            String requestUrl = baseApiUrl + "?query=" + encodedQuery + "&from=" + fromMs + "&to=" + toMs;

            logger.info("Fetching trace items for service={}, spanName={}", serviceName, spanName);
            logger.debug("Request URL: {}", requestUrl);

            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode tracesNode = root.path("data").path("traces").path("traces");
                logger.info("Found {} traces for service={}, spanName={}",
                    tracesNode.isArray() ? tracesNode.size() : 0, serviceName, spanName);
                return tracesNode;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace items for service={}, spanName={}: {}",
                serviceName, spanName, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取 Trace 详情
     * @param traceId trace ID
     * @param namespace 命名空间
     * @param serviceName 服务名
     * @param fromMs 开始时间（毫秒时间戳）
     * @param toMs 结束时间（毫秒时间戳）
     * @return trace 详情的 span 数组
     */
    public JsonNode getTraceDetail(String traceId, String namespace, String serviceName, long fromMs, long toMs) {
        String appId = buildAppId(namespace, serviceName);
        String encodedAppId;
        try {
            encodedAppId = URLEncoder.encode(appId, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            encodedAppId = appId.replace(":", "%3A");
        }
        
        String url = String.format("%s/api/project/%s/app/%s/tracing", baseUrl, projectId, encodedAppId);

        try {
            // 使用提供的时间范围
            String requestUrl = url + "?trace=otel:" + traceId + ":-:-:&from=" + fromMs + "&to=" + toMs;

            logger.info("Fetching trace detail for traceId: {}, service: {}, from: {}, to: {}", 
                traceId, serviceName, fromMs, toMs);
            logger.debug("Request URL: {}", requestUrl);

            ResponseEntity<String> response = doGetWithUri(new URI(requestUrl));
            logger.info("Response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode spansNode = root.path("data").path("spans");
                if (spansNode.isArray() && spansNode.size() > 0) {
                    logger.info("Found {} spans for traceId {}", spansNode.size(), traceId);
                    return spansNode;
                }
                logger.warn("No spans found for traceId: {}", traceId);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch trace detail for traceId {}: {}", traceId, e.getMessage(), e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * 获取 Trace 详情（兼容旧接口，不传 serviceName 和时间范围）
     */
    public JsonNode getTraceDetail(String traceId) {
        long toMs = System.currentTimeMillis();
        long fromMs = toMs - 3600_000; // 默认最近1小时
        return getTraceDetail(traceId, "default", "unknown", fromMs, toMs);
    }
}