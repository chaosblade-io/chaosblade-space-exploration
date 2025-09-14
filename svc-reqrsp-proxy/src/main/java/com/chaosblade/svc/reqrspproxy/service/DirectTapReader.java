package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.dto.RecordedEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 直接从 Pod 文件系统读取 Tap 数据的服务
 */
@Service
public class DirectTapReader {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectTapReader.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 直接从 Pod 读取 tap 数据
     */
    public List<RecordedEntry> readTapDataDirectly(String namespace, String serviceName, 
                                                  int offset, int limit) {
        logger.info("Reading tap data directly from {}/{}", namespace, serviceName);
        
        try {
            List<Pod> pods = getPodsByService(namespace, serviceName);
            if (pods.isEmpty()) {
                logger.warn("No pods found for service {}/{}", namespace, serviceName);
                return Collections.emptyList();
            }

            List<RecordedEntry> allEntries = new ArrayList<>();

            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                logger.debug("Reading tap files from pod {}", podName);

                List<RecordedEntry> podEntries = readTapFilesFromPod(namespace, podName);
                allEntries.addAll(podEntries);
            }
            
            // 按时间戳排序
            allEntries.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            
            // 应用分页
            int start = Math.min(offset, allEntries.size());
            int end = Math.min(offset + limit, allEntries.size());
            
            return allEntries.subList(start, end);
            
        } catch (Exception e) {
            logger.error("Failed to read tap data directly: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read tap data: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取 tap 信息统计
     */
    public Map<String, Object> getTapInfo(String namespace, String serviceName) {
        try {
            List<Pod> pods = getPodsByService(namespace, serviceName);
            Map<String, Object> info = new HashMap<>();

            info.put("namespace", namespace);
            info.put("serviceName", serviceName);
            info.put("podCount", pods.size());

            List<Map<String, Object>> podInfos = new ArrayList<>();
            int totalFiles = 0;

            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                List<String> tapFiles = listTapFiles(namespace, podName);

                Map<String, Object> podInfo = new HashMap<>();
                podInfo.put("podName", podName);
                podInfo.put("tapFileCount", tapFiles.size());
                podInfo.put("tapFiles", tapFiles);

                podInfos.add(podInfo);
                totalFiles += tapFiles.size();
            }
            
            info.put("totalTapFiles", totalFiles);
            info.put("pods", podInfos);
            
            return info;
            
        } catch (Exception e) {
            logger.error("Failed to get tap info: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get tap info: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取服务对应的 Pod 列表
     */
    private List<Pod> getPodsByService(String namespace, String serviceName) throws Exception {
        String labelSelector = "app=" + serviceName;
        PodList podList = kubernetesClient.pods()
            .inNamespace(namespace)
            .withLabelSelector(labelSelector)
            .list();

        return podList.getItems().stream()
            .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
            .collect(Collectors.toList());
    }
    
    /**
     * 列出 Pod 中的 tap 文件
     */
    private List<String> listTapFiles(String namespace, String podName) {
        try {
            String[] command = {"find", "/var/log/envoy/taps", "-name", "*.json", "-type", "f"};
            String output = execCommand(namespace, podName, "envoy", command);
            
            return Arrays.stream(output.split("\n"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("Failed to list tap files from pod {}: {}", podName, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 从 Pod 读取所有 tap 文件
     */
    private List<RecordedEntry> readTapFilesFromPod(String namespace, String podName) {
        List<String> tapFiles = listTapFiles(namespace, podName);
        List<RecordedEntry> entries = new ArrayList<>();
        
        for (String filePath : tapFiles) {
            try {
                String content = readTapFile(namespace, podName, filePath);
                RecordedEntry entry = parseTapContent(content, podName, filePath);
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (Exception e) {
                logger.warn("Failed to read tap file {}: {}", filePath, e.getMessage());
            }
        }
        
        return entries;
    }
    
    /**
     * 读取单个 tap 文件内容（带重试）
     */
    private String readTapFile(String namespace, String podName, String filePath) throws Exception {
        String[] command = {"cat", filePath};
        return execCommandWithRetry(namespace, podName, "envoy", command, 5, 300);
    }
    
    /**
     * 解析 tap 文件内容为 RecordedEntry
     */
    private RecordedEntry parseTapContent(String content, String podName, String filePath) {
        try {
            JsonNode tapData = objectMapper.readTree(content);
            JsonNode trace = tapData.get("http_buffered_trace");
            
            if (trace == null) {
                return null;
            }
            
            RecordedEntry entry = new RecordedEntry();
            entry.setRecordingId(extractFileId(filePath));
            entry.setTimestamp(LocalDateTime.now());
            entry.setPod(podName);

            // 解析请求
            JsonNode request = trace.get("request");
            if (request != null) {
                parseRequestInfo(entry, request);
            }

            // 解析响应
            JsonNode response = trace.get("response");
            if (response != null) {
                parseResponseInfo(entry, response);
            }
            
            return entry;
            
        } catch (Exception e) {
            logger.error("Failed to parse tap content: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析请求信息到 RecordedEntry
     */
    private void parseRequestInfo(RecordedEntry entry, JsonNode request) {
        Map<String, String> headers = new HashMap<>();

        JsonNode headersNode = request.get("headers");
        if (headersNode != null && headersNode.isArray()) {
            for (JsonNode header : headersNode) {
                String key = header.get("key").asText();
                String value = header.get("value").asText();
                headers.put(key, value);
            }
        }

        entry.setMethod(headers.get(":method"));
        entry.setPath(headers.get(":path"));
        entry.setRequestHeaders(headers);

        JsonNode body = request.get("body");
        if (body != null && body.has("as_string")) {
            entry.setRequestBody(body.get("as_string").asText());
        }

        // 提取 x-request-id 和 traceparent
        entry.setxRequestId(headers.get("x-request-id"));
        entry.setTraceparent(headers.get("traceparent"));
    }

    /**
     * 解析响应信息到 RecordedEntry
     */
    private void parseResponseInfo(RecordedEntry entry, JsonNode response) {
        Map<String, String> headers = new HashMap<>();

        JsonNode headersNode = response.get("headers");
        if (headersNode != null && headersNode.isArray()) {
            for (JsonNode header : headersNode) {
                String key = header.get("key").asText();
                String value = header.get("value").asText();
                headers.put(key, value);
            }
        }

        String status = headers.get(":status");
        if (status != null) {
            try {
                entry.setStatus(Integer.parseInt(status));
            } catch (NumberFormatException e) {
                entry.setStatus(0);
            }
        }

        entry.setResponseHeaders(headers);

        JsonNode body = response.get("body");
        if (body != null && body.has("as_string")) {
            entry.setResponseBody(body.get("as_string").asText());
        }
    }
    
    /**
     * 从文件路径提取文件 ID
     */
    private String extractFileId(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        return fileName.replace(".json", "");
    }
    
    /**
     * 执行 kubectl exec 命令
     */
    private String execCommand(String namespace, String podName, String container, String[] command) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (ExecWatch execWatch = kubernetesClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .writingOutput(stdout)
                .writingError(stderr)
                .exec(command)) {

            // 等待命令完成，使用简单的睡眠等待
            Thread.sleep(5000); // 等待5秒让命令执行完成

            String error = stderr.toString();
            if (!error.isEmpty()) {
                logger.warn("Command stderr: {}", error);
            }

            return stdout.toString();
        }
    }

    private String execCommandWithRetry(String namespace, String podName, String container, String[] command,
                                        int maxRetries, long sleepMillis) throws Exception {
        for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
            try {
                return execCommand(namespace, podName, container, command);
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (attempt >= maxRetries) throw ex;
                long backoff = sleepMillis * attempt;
                logger.warn("Exec failed in pod {} (attempt {}/{}), will retry after {}ms: {}",
                        podName, attempt, maxRetries, backoff, msg);
                Thread.sleep(backoff);
            }
        }
        return "";
    }
}
