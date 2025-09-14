package com.chaosblade.svc.taskexecutor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@SpringBootTest(classes = TaskExecutorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("default")
class FixtureUpsertPayloadDumpSpringTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

    @Test
    void dumpExec8() throws Exception {
        long executionId = 10L; // 可调整
        String namespace = "train-ticket"; // 可调整
        int ttlSec = 600; // 可调整

        Map<String,String> svcTokens = loadBaggageTokens(executionId);
        List<Map<String,Object>> items = loadItems(executionId, svcTokens);

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace);
        payload.put("recordId", String.valueOf(executionId));
        payload.put("ttlSec", ttlSec);
        payload.put("items", items);

        String json = PRETTY.writeValueAsString(payload);
        Path out = Path.of("fixtures-upsert-exec-"+executionId+".json");
        Files.writeString(out, json, StandardCharsets.UTF_8);
        System.out.println("[SpringDump] Wrote payload to: " + out.toAbsolutePath());
        System.out.println("[SpringDump] Items count: " + items.size());

        // 简单断言：生成至少 0 项（不抛异常即通过），可按需增强
        org.junit.jupiter.api.Assertions.assertNotNull(items);
    }

    private Map<String,String> loadBaggageTokens(long executionId) {
        String sql = "SELECT service_name, value FROM baggage_map WHERE execution_id=?";
        Map<String,String> map = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getString(1), rs.getString(2));
        }, executionId);
        return map;
    }

    private List<Map<String,Object>> loadItems(long executionId, Map<String,String> svcTokens) {
        String sql = "SELECT service_name, request_url, request_method, response_status, response_headers, response_body " +
                "FROM intercept_replay_results WHERE execution_id=?";
        List<Map<String,Object>> items = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String svc = rs.getString("service_name");
            String url = rs.getString("request_url");
            String method = rs.getString("request_method");
            Integer status = (Integer) rs.getObject("response_status");
            String headersJson = rs.getString("response_headers");
            String body = rs.getString("response_body");

            List<String> baggageTokens = splitTokens(svcTokens.getOrDefault(svc, ""));
            Map<String,Object> respHeaders = parseJsonToMap(headersJson);

            Map<String,Object> item = new LinkedHashMap<>();
            item.put("serviceName", svc);
            item.put("method", method);
            item.put("path", safePath(url));
            item.put("baggageTokens", baggageTokens);
            item.put("respStatus", status);
            item.put("respHeaders", respHeaders);
            item.put("respBody", body);
            items.add(item);
        }, executionId);
        return items;
    }

    private static List<String> splitTokens(String tokens) {
        if (tokens == null || tokens.isBlank()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (String t : tokens.split(",")) if (t != null && !t.isBlank()) list.add(t.trim());
        return list;
    }

    private static Map<String,Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return null;
        try { return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception ex) { return null; }
    }

    private static String safePath(String url) {
        if (url == null) return "/";
        try {
            java.net.URI u = java.net.URI.create(url);
            return (u.getPath()!=null && !u.getPath().isEmpty()) ? u.getPath() : "/";
        } catch (Exception ex) {
            return "/";
        }
    }
}

