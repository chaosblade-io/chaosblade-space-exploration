package com.chaosblade.svc.taskexecutor.client;

import com.chaosblade.svc.taskexecutor.config.ProxyProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProxyClient {
    private final RestTemplate restTemplate;
    private final ProxyProperties props;

    public ProxyClient(ProxyProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
    }

    private <T> T retrying(Call<T> call) {
        int attempts = 0;
        RuntimeException last = null;
        while (attempts < Math.max(1, props.getMaxRetries())) {
            try {
                return call.run();
            } catch (HttpStatusCodeException ex) {
                // 4xx 不重试
                if (ex.getStatusCode().is4xxClientError()) throw ex;
                last = ex;
            } catch (RuntimeException ex) {
                last = ex;
            }
            attempts++;
            try { Thread.sleep((long) (props.getBackoffMs() * Math.pow(2, attempts-1))); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        if (last != null) throw last;
        throw new RuntimeException("proxy call failed without exception");
    }

    @FunctionalInterface
    private interface Call<T> { T run(); }

    public Map<String, Object> analyze(Map<String, Object> payload) {
        return retrying(() -> {
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, h);
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/request-patterns/analyze",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }

    public Map<String, Object> getAnalyzeTask(String taskId) {
        return retrying(() -> {
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/request-patterns/tasks/"+taskId,
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }

    public Map<String, Object> startRecording(String namespace, String serviceName, List<Map<String,Object>> rules, Integer appPort, Integer durationSec) {
        return retrying(() -> {
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("namespace", namespace);
            payload.put("serviceName", serviceName);
            if (appPort != null && appPort > 0) payload.put("appPort", appPort);
            if (durationSec != null && durationSec > 0) payload.put("durationSec", durationSec);
            payload.put("rules", rules);
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, h);
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/recordings/start",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }

    public Map<String, Object> replay(Long executionId, String namespace, String serviceName, Map<String,String> headers) {
        return retrying(() -> {
            HttpHeaders h = new HttpHeaders();
            headers.forEach(h::add);
            h.setContentType(MediaType.APPLICATION_JSON);
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("excution_id", executionId);
            payload.put("namespace", namespace);
            payload.put("service_name", serviceName);
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, h);
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/request-patterns/replay",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }

    public Map<String, Object> interceptorsUpsert(String namespace, String recordId, int ttlSec, List<Map<String,Object>> items) {
        return retrying(() -> {
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("namespace", namespace);
            payload.put("recordId", recordId);
            payload.put("ttlSec", ttlSec);
            payload.put("items", items);
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, h);
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/fixtures/upsert",
                HttpMethod.POST, req,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }

    public Map<String,Object> getInterceptorStatus(String recordId) {
        return retrying(() -> {
            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                props.getBaseUrl()+"/api/fixtures/record/"+recordId+"/status",
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            return resp.getBody();
        });
    }
}

