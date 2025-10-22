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

import com.chaosblade.svc.reqrspproxy.entity.HttpReqDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Netty 超时处理器
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

/**
 * HTTP请求执行器
 * 根据HttpReqDef配置发起实际的HTTP请求
 */
@Service
public class HttpRequestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestExecutor.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final AuthenticationService authenticationService;

    public HttpRequestExecutor(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        this.objectMapper = new ObjectMapper();
        // 配置连接池
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom")
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // 配置 HttpClient（统一设置 120 秒连接与读写超时）
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 120_000) // 连接超时 120s
                .responseTimeout(Duration.ofSeconds(120))                                 // 响应超时 120s
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(120));   // 读超时 120s
                    conn.addHandlerLast(new WriteTimeoutHandler(120));  // 写超时 120s
                })
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
                    configurer.defaultCodecs().enableLoggingRequestDetails(true);
                })
                .defaultHeader(HttpHeaders.USER_AGENT, "ChaosBlade-RequestPattern-Service/1.0")
                .build();
    }

    /**
     * 执行HTTP请求
     */
    public Mono<HttpRequestResult> executeRequest(HttpReqDef reqDef, Map<String, Object> variables) {
        logger.info("Executing HTTP request: {} {}", reqDef.getMethod(), reqDef.getUrlTemplate());

        return executeRequestWithRetry(reqDef, variables, false);
    }

    /**
     * 执行HTTP请求（带重试逻辑）
     */
    private Mono<HttpRequestResult> executeRequestWithRetry(HttpReqDef reqDef, Map<String, Object> variables, boolean isRetry) {
        try {
            // 1. 处理URL模板
            String url = processTemplate(reqDef.getUrlTemplate(), variables);

            // 2. 处理请求头
            HttpHeaders headers = processHeaders(reqDef.getHeaders(), variables);

            // 3. 处理查询参数
            Map<String, String> queryParams = processQueryParams(reqDef.getQueryParams(), variables);

            // 4. 构建完整URL（包含查询参数）
            String fullUrl = buildFullUrl(url, queryParams);

            // 5. 处理请求体
            Object requestBody = processRequestBody(reqDef, variables);

            // 6. 发起请求
            return executeWebClientRequest(reqDef.getMethod(), fullUrl, headers, requestBody)
                    .map(response -> new HttpRequestResult(true, response.getStatusCode().value(),
                            response.getHeaders(), response.getBody(), null))
                    .onErrorResume(throwable -> {
                        logger.error("Request execution failed: {}", throwable.getMessage(), throwable);

                        // 检查是否是token过期错误
                        if (!isRetry && isTokenExpiredError(throwable)) {
                            logger.warn("Token expired error detected, clearing token cache and retrying...");
                            authenticationService.clearToken();
                            return executeRequestWithRetry(reqDef, variables, true);
                        }

                        return Mono.just(new HttpRequestResult(false, 0, null, null, throwable.getMessage()));
                    });

        } catch (Exception e) {
            logger.error("Failed to prepare request: {}", e.getMessage(), e);
            return Mono.just(new HttpRequestResult(false, 0, null, null, e.getMessage()));
        }
    }

    /**
     * 检查是否是token过期错误
     */
    private boolean isTokenExpiredError(Throwable throwable) {
        if (throwable == null) return false;

        String message = throwable.getMessage();
        if (message == null) return false;

        // 检查错误消息中是否包含token过期相关的关键词
        return message.contains("Token expired") ||
               message.contains("HTTP 500") && message.contains("TokenException") ||
               message.contains("HTTP 401");
    }
    
    /**
     * 处理模板字符串，替换变量
     */
    private String processTemplate(String template, Map<String, Object> variables) {
        if (!StringUtils.hasText(template) || variables == null || variables.isEmpty()) {
            return template;
        }
        
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 处理请求头
     */
    private HttpHeaders processHeaders(String headersJson, Map<String, Object> variables) {
        HttpHeaders headers = new HttpHeaders();

        if (StringUtils.hasText(headersJson)) {
            try {
                Map<String, String> headerMap = objectMapper.readValue(headersJson,
                        new TypeReference<Map<String, String>>() {});

                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    String value = processTemplate(entry.getValue(), variables);
                    headers.add(entry.getKey(), value);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse headers JSON: {}", e.getMessage());
            }
        }

        // 处理 Authorization 头 - 仅在未提供时，才尝试补充最新有效 token；绝不覆盖已有值
        if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            try {
                String validToken = authenticationService.getValidToken();
                if (StringUtils.hasText(validToken)) {
                    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);
                    logger.debug("Added Authorization header with fresh token (no override)");
                } else {
                    logger.debug("No token available from AuthenticationService and no Authorization provided");
                }
            } catch (Exception e) {
                logger.warn("Failed to obtain valid token: {}", e.getMessage());
            }
        } else {
            logger.debug("Authorization header already provided in request definition; keeping as-is");
        }

        // 确保有 Content-Type
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        // 确保有 Accept
        if (!headers.containsKey(HttpHeaders.ACCEPT)) {
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        }

        return headers;
    }
    
    /**
     * 处理查询参数
     */
    private Map<String, String> processQueryParams(String queryParamsJson, Map<String, Object> variables) {
        Map<String, String> queryParams = new HashMap<>();
        
        if (StringUtils.hasText(queryParamsJson)) {
            try {
                Map<String, String> paramMap = objectMapper.readValue(queryParamsJson, 
                        new TypeReference<Map<String, String>>() {});
                
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    String value = processTemplate(entry.getValue(), variables);
                    queryParams.put(entry.getKey(), value);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse query params JSON: {}", e.getMessage());
            }
        }
        
        return queryParams;
    }
    
    /**
     * 构建完整URL（包含查询参数）
     */
    private String buildFullUrl(String baseUrl, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return baseUrl;
        }
        
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        boolean first = !baseUrl.contains("?");
        
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (first) {
                urlBuilder.append("?");
                first = false;
            } else {
                urlBuilder.append("&");
            }
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return urlBuilder.toString();
    }
    
    /**
     * 处理请求体
     */
    private Object processRequestBody(HttpReqDef reqDef, Map<String, Object> variables) {
        if (reqDef.getBodyMode() == HttpReqDef.BodyMode.NONE) {
            return null;
        }
        
        try {
            logger.debug("Processing request body - mode: {}, bodyTemplate: {}, rawBody: {}",
                    reqDef.getBodyMode(), reqDef.getBodyTemplate(), reqDef.getRawBody());

            switch (reqDef.getBodyMode()) {
                case JSON:
                case FORM:
                    if (StringUtils.hasText(reqDef.getBodyTemplate())) {
                        // body_template 在数据库中是 JSON 类型，但在 Java 中是字符串
                        // 直接返回字符串，不需要再次序列化
                        String bodyJson = reqDef.getBodyTemplate();
                        logger.debug("Using JSON body template directly: {}", bodyJson);
                        return bodyJson;
                    }
                    break;
                case RAW:
                    // 优先使用 raw_body，如果为空则使用 body_template
                    if (StringUtils.hasText(reqDef.getRawBody())) {
                        String rawBody = processTemplate(reqDef.getRawBody(), variables);
                        logger.debug("Processed raw body: {}", rawBody);
                        return rawBody;
                    } else if (StringUtils.hasText(reqDef.getBodyTemplate())) {
                        // 如果 raw_body 为空但 body_template 有值，使用 body_template
                        logger.debug("raw_body is empty, using body_template for RAW mode");
                        // body_template 在数据库中是 JSON 类型，但在 Java 中是字符串
                        // 直接返回字符串，不需要再次序列化
                        String bodyFromTemplate = reqDef.getBodyTemplate();
                        logger.debug("Using body template directly for RAW mode: {}", bodyFromTemplate);
                        return bodyFromTemplate;
                    }
                    break;
                case NONE:
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to process request body: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 执行WebClient请求
     */
    private Mono<ResponseEntity<String>> executeWebClientRequest(HttpReqDef.HttpMethod method,
            String url, HttpHeaders headers, Object body) {

        logger.info("=== HTTP 请求详情 ===");
        logger.info("请求方法: {}", method);
        logger.info("请求URL: {}", url);
        logger.info("请求头完整信息:");
        headers.forEach((key, values) -> {
            logger.info("  {}: {}", key, String.join(", ", values));
        });
        logger.info("请求体类型: {}", body != null ? body.getClass().getSimpleName() : "null");
        logger.info("请求体内容: {}", body);
        logger.info("请求体长度: {}", body != null ? body.toString().length() : 0);

        try {
            WebClient.RequestBodyUriSpec requestUriSpec = webClient.method(HttpMethod.valueOf(method.name()));
            WebClient.RequestBodySpec requestBodySpec = requestUriSpec.uri(url);

            // 设置请求头
            WebClient.RequestHeadersSpec<?> requestHeadersSpec = requestBodySpec
                    .headers(httpHeaders -> {
                        httpHeaders.addAll(headers);
                        // 确保有 Content-Type
                        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE) && body != null) {
                            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                        }
                        // 确保有 Accept
                        if (!httpHeaders.containsKey(HttpHeaders.ACCEPT)) {
                            httpHeaders.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                        }
                    });

            // 设置请求体
            if (body != null) {
                logger.debug("Setting request body: {}", body);
                if (body instanceof String) {
                    // 字符串类型的请求体
                    requestHeadersSpec = requestBodySpec.bodyValue(body);
                } else {
                    // 对象类型，序列化为 JSON 字符串
                    try {
                        String jsonBody = objectMapper.writeValueAsString(body);
                        logger.debug("Serialized request body: {}", jsonBody);
                        requestHeadersSpec = requestBodySpec.bodyValue(jsonBody);
                    } catch (Exception e) {
                        logger.error("Failed to serialize request body: {}", e.getMessage(), e);
                        requestHeadersSpec = requestBodySpec.bodyValue(body);
                    }
                }
            }

            return requestHeadersSpec
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                logger.error("HTTP error response: {}", clientResponse.statusCode());
                                return clientResponse.bodyToMono(String.class)
                                        .map(errorBody -> {
                                            logger.error("Error response body: {}", errorBody);
                                            return new RuntimeException("HTTP " + clientResponse.statusCode() + ": " + errorBody);
                                        });
                            })
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .doOnSuccess(response -> {
                        logger.info("=== HTTP 响应成功 ===");
                        logger.info("响应状态码: {}", response.getStatusCode());
                        logger.info("响应URL: {}", url);
                        logger.info("响应头:");
                        response.getHeaders().forEach((key, values) -> {
                            logger.info("  {}: {}", key, String.join(", ", values));
                        });
                        logger.info("响应体: {}", response.getBody());
                    })
                    .doOnError(error -> {
                        logger.error("=== HTTP 请求失败 ===");
                        logger.error("失败URL: {}", url);
                        logger.error("错误类型: {}", error.getClass().getSimpleName());
                        logger.error("错误消息: {}", error.getMessage());
                        logger.error("完整错误:", error);
                    });

        } catch (Exception e) {
            logger.error("Failed to build request for URL {}: {}", url, e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * HTTP请求执行结果
     */
    public static class HttpRequestResult {
        private boolean success;
        private int statusCode;
        private HttpHeaders responseHeaders;
        private String responseBody;
        private String errorMessage;
        
        public HttpRequestResult(boolean success, int statusCode, HttpHeaders responseHeaders, 
                String responseBody, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.responseHeaders = responseHeaders;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public HttpHeaders getResponseHeaders() { return responseHeaders; }
        public String getResponseBody() { return responseBody; }
        public String getErrorMessage() { return errorMessage; }
    }
}
