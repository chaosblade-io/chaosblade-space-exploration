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

package com.chaosblade.svc.topo.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 实体ID生成器工具类
 *
 * 用于生成各种实体的唯一标识符
 */
public class EntityIdGenerator {

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9\\-_]");

    /**
     * 生成服务ID
     */
    public static String generateServiceId(String serviceName) {
        return "svc-" + sanitize(serviceName);
    }

    /**
     * 生成Pod ID
     */
    public static String generatePodId(String podName) {
        return "pod-" + sanitize(podName);
    }

    /**
     * 生成Host ID
     */
    public static String generateHostId(String hostName) {
        return "host-" + sanitize(hostName);
    }

    /**
     * 生成RPC ID
     */
    public static String generateRpcId(String rpcName) {
        return "rpc-" + sanitize(rpcName);
    }

    /**
     * 生成RPC分组ID
     */
    public static String generateRpcGroupId(String serviceName, String protocol) {
        return "rpcg-" + sanitize(serviceName) + "-" + sanitize(protocol);
    }

    /**
     * 生成命名空间ID
     */
    public static String generateNamespaceId(String namespace) {
        return "ns-" + sanitize(namespace);
    }

    /**
     * 生成中间件ID
     */
    public static String generateMiddlewareId(String middlewareName) {
        return "mw-" + sanitize(middlewareName);
    }

    /**
     * 生成外部服务ID
     */
    public static String generateExternalServiceId(String serviceName) {
        return "ext-" + sanitize(serviceName);
    }

    /**
     * 生成边ID
     */
    public static String generateEdgeId(String fromNodeId, String toNodeId, String relationType) {
        return sanitize(fromNodeId) + "-" + sanitize(toNodeId) + "-" + sanitize(relationType);
    }

    /**
     * 生成唯一ID（基于时间戳和计数器）
     */
    public static String generateUniqueId(String prefix) {
        long timestamp = System.currentTimeMillis();
        long counter = COUNTER.incrementAndGet();
        return sanitize(prefix) + "-" + timestamp + "-" + counter;
    }

    /**
     * 生成哈希ID（基于内容）
     */
    public static String generateHashId(String prefix, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // 取前8位
            return sanitize(prefix) + "-" + hexString.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            // 如果MD5不可用，使用hashCode
            return sanitize(prefix) + "-" + Math.abs(content.hashCode());
        }
    }

    /**
     * 清理字符串，移除特殊字符
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "unknown";
        }

        // 替换特殊字符为连字符
        String sanitized = SPECIAL_CHARS.matcher(input.trim()).replaceAll("-");

        // 移除多余的连字符
        sanitized = sanitized.replaceAll("-+", "-");

        // 移除开头和结尾的连字符
        sanitized = sanitized.replaceAll("^-+|-+$", "");

        // 如果结果为空，返回默认值
        if (sanitized.isEmpty()) {
            return "unknown";
        }

        // 转换为小写
        return sanitized.toLowerCase();
    }

    /**
     * 从操作名称中提取RPC名称
     */
    public static String extractRpcName(String operationName) {
        if (operationName == null) {
            return "unknown";
        }

        // 处理gRPC格式: "service.name/method"
        if (operationName.contains("/")) {
            String[] parts = operationName.split("/");
            if (parts.length >= 2) {
                return sanitize(parts[parts.length - 1]); // 取最后一部分作为方法名
            }
        }

        // 处理HTTP格式: "GET /api/users"
        if (operationName.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+.*")) {
            String[] parts = operationName.split("\\s+");
            if (parts.length >= 2) {
                String path = parts[1];
                // 提取路径的最后部分
                String[] pathParts = path.split("/");
                for (int i = pathParts.length - 1; i >= 0; i--) {
                    if (!pathParts[i].isEmpty() && !pathParts[i].matches("\\d+")) {
                        return sanitize(pathParts[i]);
                    }
                }
                return sanitize(parts[0].toLowerCase()); // 返回HTTP方法
            }
        }

        return sanitize(operationName);
    }

    /**
     * 从服务名中提取短名称
     */
    public static String extractShortServiceName(String serviceName) {
        if (serviceName == null) {
            return "unknown";
        }

        // 移除常见的前缀和后缀
        String shortName = serviceName;

        // 移除域名后缀
        if (shortName.contains(".")) {
            shortName = shortName.split("\\.")[0];
        }

        // 移除版本号
        shortName = shortName.replaceAll("-v\\d+", "");
        shortName = shortName.replaceAll("_v\\d+", "");

        // 移除环境标识
        shortName = shortName.replaceAll("-(dev|test|prod|staging)$", "");
        shortName = shortName.replaceAll("_(dev|test|prod|staging)$", "");

        return sanitize(shortName);
    }

    /**
     * 验证ID格式是否有效
     */
    public static boolean isValidId(String id) {
        return id != null &&
               !id.isEmpty() &&
               id.matches("^[a-zA-Z0-9\\-_]+$") &&
               !id.startsWith("-") &&
               !id.endsWith("-");
    }

    /**
     * 确保ID的唯一性（在同一类型的ID中）
     */
    public static String ensureUnique(String baseId, java.util.Set<String> existingIds) {
        if (!existingIds.contains(baseId)) {
            return baseId;
        }

        int counter = 1;
        String uniqueId;
        do {
            uniqueId = baseId + "-" + counter;
            counter++;
        } while (existingIds.contains(uniqueId));

        return uniqueId;
    }
}
