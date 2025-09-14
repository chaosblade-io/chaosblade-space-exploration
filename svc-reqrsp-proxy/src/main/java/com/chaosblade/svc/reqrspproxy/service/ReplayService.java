package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.dto.ReplayRequest;
import com.chaosblade.svc.reqrspproxy.dto.ReplayResult;
import com.chaosblade.svc.reqrspproxy.entity.RequestPattern;
import com.chaosblade.svc.reqrspproxy.repository.RequestPatternRepository;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 请求重放服务
 */
@Service
public class ReplayService {

    private static final Logger logger = LoggerFactory.getLogger(ReplayService.class);

    @Autowired private KubernetesClient k8s;
    @Autowired private RequestPatternRepository requestPatternRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    // HTTP/2 伪头部前缀，需过滤
    private static final Set<String> H2_PSEUDO_HEADERS = Set.of(
            ":method", ":scheme", ":authority", ":path"
    );

    /**
     * 基于 execution_id 查询请求模式并重放到指定 service
     */
    public List<ReplayResult> replay(ReplayRequest request) {
        Long executionId = request.getExecutionId();
        String ns = request.getNamespace();
        String svc = request.getServiceName();

        logger.info("Replay start: executionId={}, ns={}, svc={}", executionId, ns, svc);

        // 1) 查询请求模式
        List<RequestPattern> patterns = requestPatternRepository
                .findByExecutionIdAndServiceName(executionId, svc);
        if (patterns == null || patterns.isEmpty()) {
            logger.warn("No request patterns for executionId={} and service={}", executionId, svc);
            return Collections.emptyList();
        }

        // 2) 服务发现：获取 Service & 端口（取第一个端口）
        io.fabric8.kubernetes.api.model.Service svcObj = k8s.services().inNamespace(ns).withName(svc).get();
        if (svcObj == null || svcObj.getSpec() == null || svcObj.getSpec().getPorts() == null || svcObj.getSpec().getPorts().isEmpty()) {
            throw new RuntimeException("Service not found or has no ports: " + svc);
        }
        ServicePort sp = svcObj.getSpec().getPorts().get(0);
        Integer targetPort = sp.getPort();
        if (targetPort == null) {
            throw new RuntimeException("Service has no ports: " + svc);
        }
        String defaultScheme = isHttpsPort(sp) ? "https" : "http";

        // 3) 端口转发：本地随机端口 -> Service 端口
        try (LocalPortForward pf = k8s.services().inNamespace(ns).withName(svc)
                .portForward(targetPort)) {

            int localPort = pf.getLocalPort();
            logger.info("Port-forward established: localhost:{} -> {}/{}:{} (defaultScheme={})", localPort, ns, svc, targetPort, defaultScheme);

            // 4) 并发回放
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, patterns.size())));
            try {
                List<CompletableFuture<ReplayResult>> futures = patterns.stream()
                        .map(p -> CompletableFuture.supplyAsync(() -> replaySingle(localPort, p, defaultScheme), pool))
                        .collect(Collectors.toList());

                List<ReplayResult> results = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());

                logger.info("Replay finished: {} requests", results.size());
                return results;
            } finally {
                pool.shutdown();
            }
        } catch (Exception e) {
            logger.error("Port-forward or replay failed: {}", e.getMessage(), e);
            throw new RuntimeException("Replay failed: " + e.getMessage(), e);
        }
    }

    private ReplayResult replaySingle(int localPort, RequestPattern p, String defaultScheme) {
        String method = p.getMethod();
        String urlPath = p.getUrl();
        String headersJson = p.getRequestHeaders();
        String body = p.getRequestBody();

        // 从 headers 或 URL 推断 scheme，优先 headers 的 :scheme/host 决定；否则用 default
        String scheme = inferScheme(headersJson, defaultScheme);

        String path = extractPathAndQuery(urlPath);
        String fullUrl = scheme + "://localhost:" + localPort + path;

        HttpHeaders headers = buildFilteredHeaders(headersJson);

        // 详细请求调试日志
        logger.info("=== Replay Request Debug ===");
        logger.info("Method: {}", method);
        logger.info("Full URL: {}", fullUrl);
        logger.info("Original headers JSON: {}", headersJson);
        logger.info("Filtered headers: {}", headers);
        logger.info("Request body: {}", body);
        logger.info("=== End Request Debug ===");

        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            long t0 = System.currentTimeMillis();
            ResponseEntity<byte[]> resp = restTemplate.exchange(fullUrl, httpMethod, entity, byte[].class);
            long t1 = System.currentTimeMillis();

            ReplayResult result = new ReplayResult(fullUrl, method, null);
            result.setStatusCode(resp.getStatusCode().value());
            Map<String, List<String>> respHeaders = new LinkedHashMap<>();
            resp.getHeaders().forEach((k, list) -> respHeaders.put(k, new ArrayList<>(list)));
            result.setResponseHeaders(respHeaders);
            logger.info("result: ",result);
            byte[] respBytes = Optional.ofNullable(resp.getBody()).orElse(new byte[0]);
            MediaType ctType = resp.getHeaders().getContentType();
            java.nio.charset.Charset cs = Optional.ofNullable(ctType).map(MediaType::getCharset).orElse(java.nio.charset.StandardCharsets.UTF_8);
            String respBody = new String(respBytes, cs);

            // 调试日志
            try {
                String ct = Optional.ofNullable(ctType).map(MediaType::toString).orElse("unknown");
                String te = String.join(",", resp.getHeaders().getOrDefault("Transfer-Encoding", List.of()));
                String preview = respBody.length() > 256 ? respBody.substring(0, 256) + "...(truncated)" : respBody;
                logger.info("Replay debug: status={}, content-type={}, transfer-encoding={}, body-bytes={}, tookMs={}, preview={}",
                        result.getStatusCode(), ct, te, respBytes.length, (t1 - t0), preview);
            } catch (Exception ignore) {}

            result.setResponseBody(respBody);
            return result;
        } catch (Exception ex) {
            logger.warn("Replay exception for {} {}: {}", method, fullUrl, ex.toString());
            ReplayResult result = new ReplayResult(fullUrl, method, "");
            result.setStatusCode(0);
            result.setResponseHeaders(Collections.emptyMap());
            result.setErrorMessage(ex.getMessage());
            return result;
        }
    }

    private HttpHeaders buildFilteredHeaders(String headersJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (headersJson == null || headersJson.isEmpty()) return headers;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(headersJson, Map.class);
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey();
                Object vObj = e.getValue();
                if (key == null) continue;
                String lower = key.toLowerCase();
                if (key.startsWith(":")) continue; // 过滤 HTTP/2 伪头
                if (H2_PSEUDO_HEADERS.contains(lower)) continue;

                // 保留认证/会话相关请求头（大小写不敏感）
                boolean isAuthRelated = lower.equals("authorization")
                        || lower.equals("cookie")
                        || lower.equals("proxy-authorization")
                        || lower.startsWith("x-auth-")
                        || lower.startsWith("x-api-");

                // 非认证头部做名称有效性校验
                if (!isAuthRelated && !isValidHeaderName(key)) continue;

                if (vObj instanceof Collection<?>) {
                    for (Object vv : ((Collection<?>) vObj)) {
                        if (vv != null) headers.add(key, String.valueOf(vv));
                    }
                } else if (vObj != null && vObj.getClass().isArray()) {
                    Object[] arr = (Object[]) vObj;
                    for (Object vv : arr) if (vv != null) headers.add(key, String.valueOf(vv));
                } else {
                    String v = vObj == null ? null : String.valueOf(vObj);
                    if (v != null) headers.add(key, v);
                }
            }
        } catch (Exception ex) {
            logger.warn("Parse headers JSON failed, will use default Accept only: {}", ex.getMessage());
        }
        return headers;
    }

    private String inferScheme(String headersJson, String defaultScheme) {
        // 1) 优先使用 :scheme 头（如果存在且有效）
        if (headersJson != null && !headersJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(headersJson, Map.class);
                Object s = map.get(":scheme");
                if (s != null) {
                    String v = String.valueOf(s).toLowerCase();
                    if ("http".equals(v) || "https".equals(v)) return v;
                }
            } catch (Exception ignore) {}
        }
        // 2) 默认回退
        return (defaultScheme == null || defaultScheme.isBlank()) ? "http" : defaultScheme;
    }

    private boolean isHttpsPort(ServicePort sp) {
        String name = sp.getName();
        Integer port = sp.getPort();
        if (name != null && name.toLowerCase().contains("https")) return true;
        if (port != null && (port == 443 || port == 8443 || port == 9443)) return true;
        return false;
    }

    // RFC7230 token 校验（字母、数字或!#$%&'*+-.^_`|~）
    private boolean isValidHeaderName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') &&
                    "!#$%&'*+-.^_`|~".indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    private String extractPathAndQuery(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) return "/";
        int schemeIdx = urlOrPath.indexOf("://");
        if (schemeIdx > 0) {
            int idx = urlOrPath.indexOf('/', schemeIdx + 3);
            if (idx > 0) {
                return urlOrPath.substring(idx);
            }
            return "/";
        }
        return urlOrPath.startsWith("/") ? urlOrPath : "/" + urlOrPath;
    }
}

