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

package com.chaosblade.svc.taskexecutor.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * 独立运行的小工具：
 * - 直接从数据库读取 executionId 对应的拦截素材（baggage_map、intercept_replay_results）
 * - 生成 /api/fixtures/upsert 的请求体 JSON 并写入文件
 * - 不依赖 Spring 容器，不影响主流程，运行速度快
 *
 * 运行方式（IDE 里运行 main 或命令行）：
 *   -JVM 环境变量可用：DB_URL / DB_USERNAME / DB_PASSWORD
 *   -程序参数：executionId [namespace] [ttlSec] [outputPath]
 *   示例：8 train-ticket 600 fixtures-upsert-exec-8.json
 */
public class ExecFixturesPayloadDumpMain {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: ExecFixturesPayloadDumpMain <executionId> [namespace=train-ticket] [ttlSec=600] [outputPath=fixtures-upsert-exec-<id>.json]");
            return;
        }
        long executionId = Long.parseLong(args[0]);
        String namespace = (args.length >= 2) ? args[1] : "train-ticket";
        int ttlSec = (args.length >= 3) ? Integer.parseInt(args[2]) : 600;
        String outPath = (args.length >= 4) ? args[3] : ("fixtures-upsert-exec-" + executionId + ".json");

        String url = getEnvOrDefault("DB_URL", "jdbc:mysql://116.63.51.45:3306/spaceexploration?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        String user = getEnvOrDefault("DB_USERNAME", "root");
        String pass = getEnvOrDefault("DB_PASSWORD", "");

        System.out.println("[Dump] Connecting DB: " + url + ", user=" + user);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Map<String, String> svcTokens = loadBaggageTokens(conn, executionId);
            List<Map<String, Object>> items = buildItems(conn, executionId, svcTokens);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("namespace", namespace);
            payload.put("recordId", String.valueOf(executionId));
            payload.put("ttlSec", ttlSec);
            payload.put("items", items);

            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(Path.of(outPath), json, StandardCharsets.UTF_8);
            System.out.println("[Dump] Wrote payload to: " + Path.of(outPath).toAbsolutePath());
            System.out.println("[Dump] Items count: " + items.size());
        }
    }

    private static Map<String, String> loadBaggageTokens(Connection conn, long executionId) throws SQLException {
        String sql = "SELECT service_name, value FROM baggage_map WHERE execution_id=?";
        Map<String, String> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return map;
    }

    private static List<Map<String, Object>> buildItems(Connection conn, long executionId, Map<String, String> svcTokens) throws SQLException, IOException {
        String sql = "SELECT service_name, request_url, request_method, response_status, response_headers, response_body " +
                "FROM intercept_replay_results WHERE execution_id=?";
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String svc = rs.getString("service_name");
                    String url = rs.getString("request_url");
                    String method = rs.getString("request_method");
                    Integer status = getInt(rs, "response_status");
                    String headersJson = rs.getString("response_headers");
                    String body = rs.getString("response_body");

                    List<String> baggageTokens = splitTokens(svcTokens.getOrDefault(svc, ""));
                    Map<String, Object> respHeaders = parseJsonToMap(headersJson);

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("serviceName", svc);
                    item.put("method", method);
                    item.put("path", safePath(url));
                    item.put("baggageTokens", baggageTokens);
                    item.put("respStatus", status);
                    item.put("respHeaders", respHeaders);
                    item.put("respBody", body);
                    items.add(item);
                }
            }
        }
        return items;
    }

    private static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col); return rs.wasNull() ? null : v;
    }

    private static List<String> splitTokens(String tokens) {
        if (tokens == null || tokens.isBlank()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (String t : tokens.split(",")) if (t != null && !t.isBlank()) list.add(t.trim());
        return list;
    }

    private static Map<String, Object> parseJsonToMap(String json) throws IOException {
        if (json == null || json.isBlank()) return null;
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private static String safePath(String url) {
        if (url == null) return "/";
        try {
            URI u = URI.create(url);
            return (u.getPath() != null && !u.getPath().isEmpty()) ? u.getPath() : "/";
        } catch (Exception ex) {
            return url; // 尽力返回
        }
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}

