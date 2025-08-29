package com.chaosblade.svc.topo.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Trace文件解析服务
 *
 * 功能：
 * 1. 解析OpenTelemetry trace-*.json文件
 * 2. 提取服务拓扑信息
 * 3. 构建服务调用关系
 */
@Service
public class TraceParserService {

    private static final Logger logger = LoggerFactory.getLogger(TraceParserService.class);

    private final ObjectMapper objectMapper;

    public TraceParserService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    /**
     * 解析上传的trace文件
     */
    public TraceData parseTraceFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Trace file is empty");
        }

        logger.info("开始解析trace文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            TraceData traceData = objectMapper.readValue(inputStream, TraceData.class);

            logger.info("成功解析trace文件，包含 {} 个trace记录",
                       traceData.getData() != null ? traceData.getData().size() : 0);

            return traceData;
        } catch (Exception e) {
            logger.error("解析trace文件失败: {}", e.getMessage(), e);
            throw new IOException("Failed to parse trace file: " + e.getMessage(), e);
        }
    }

    /**
     * 合并多个TraceData
     */
    public TraceData mergeTraceData(List<TraceData> traceDataList) {
        if (traceDataList == null || traceDataList.isEmpty()) {
            throw new IllegalArgumentException("TraceData list is empty");
        }

        if (traceDataList.size() == 1) {
            return traceDataList.get(0);
        }

        logger.info("开始合并 {} 个TraceData", traceDataList.size());

        TraceData mergedData = new TraceData();
        List<TraceData.TraceRecord> mergedRecords = new ArrayList<>();

        int totalRecords = 0;
        for (TraceData traceData : traceDataList) {
            if (traceData.getData() != null) {
                mergedRecords.addAll(traceData.getData());
                totalRecords += traceData.getData().size();
            }
        }

        mergedData.setData(mergedRecords);

        logger.info("合并完成，总计 {} 个trace记录", totalRecords);
        return mergedData;
    }
    public TraceData parseTraceContent(String content) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Trace content is empty");
        }

        logger.info("开始解析trace内容，长度: {} 字符", content.length());

        try {
            TraceData traceData = objectMapper.readValue(content, TraceData.class);

            logger.info("成功解析trace内容，包含 {} 个trace记录",
                       traceData.getData() != null ? traceData.getData().size() : 0);

            return traceData;
        } catch (Exception e) {
            logger.error("解析trace内容失败: {}", e.getMessage(), e);
            throw new IOException("Failed to parse trace content: " + e.getMessage(), e);
        }
    }

    /**
     * 提取服务信息
     */
    public Set<String> extractServiceNames(TraceData traceData) {
        Set<String> serviceNames = new HashSet<>();

        if (traceData == null || traceData.getData() == null) {
            return serviceNames;
        }

        for (TraceData.TraceRecord record : traceData.getData()) {
            // 从processes中提取服务名
            if (record.getProcesses() != null) {
                for (ProcessData process : record.getProcesses().values()) {
                    if (process.getServiceName() != null) {
                        serviceNames.add(process.getServiceName());
                    }
                }
            }

            // 从spans中提取服务名
            if (record.getSpans() != null) {
                for (SpanData span : record.getSpans()) {
                    String serviceName = span.getServiceName();
                    if (serviceName != null) {
                        serviceNames.add(serviceName);
                    }
                }
            }
        }

        logger.info("提取到 {} 个服务: {}", serviceNames.size(), serviceNames);
        return serviceNames;
    }

    /**
     * 提取服务调用关系
     */
    public List<ServiceCall> extractServiceCalls(TraceData traceData) {
        List<ServiceCall> serviceCalls = new ArrayList<>();

        if (traceData == null || traceData.getData() == null) {
            return serviceCalls;
        }

        for (TraceData.TraceRecord record : traceData.getData()) {
            if (record.getSpans() == null || record.getProcesses() == null) {
                continue;
            }

            // 建立processId到serviceName的映射
            Map<String, String> processToService = record.getProcesses().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getServiceName(),
                            (existing, replacement) -> existing
                    ));

            // 建立spanId到span的映射
            Map<String, SpanData> spanMap = record.getSpans().stream()
                    .collect(Collectors.toMap(
                            SpanData::getSpanId,
                            span -> span,
                            (existing, replacement) -> existing
                    ));

            // 分析每个span的调用关系
            for (SpanData span : record.getSpans()) {
                String childService = processToService.get(span.getProcessId());
                if (childService == null) continue;

                String parentSpanId = span.getParentSpanId();
                if (parentSpanId != null) {
                    SpanData parentSpan = spanMap.get(parentSpanId);
                    if (parentSpan != null) {
                        String parentService = processToService.get(parentSpan.getProcessId());
                        if (parentService != null && !parentService.equals(childService)) {
                            ServiceCall call = new ServiceCall(
                                    parentService,
                                    childService,
                                    span.getOperationName(),
                                    span.getDuration(),
                                    span.isError()
                            );
                            serviceCalls.add(call);
                        }
                    }
                }
            }
        }

        logger.info("提取到 {} 个服务调用关系", serviceCalls.size());
        return serviceCalls;
    }

    /**
     * 提取RPC接口信息
     */
    public List<RpcInterface> extractRpcInterfaces(TraceData traceData) {
        List<RpcInterface> rpcInterfaces = new ArrayList<>();

        if (traceData == null || traceData.getData() == null) {
            return rpcInterfaces;
        }

        for (TraceData.TraceRecord record : traceData.getData()) {
            if (record.getSpans() == null || record.getProcesses() == null) {
                continue;
            }

            // 建立processId到serviceName的映射
            Map<String, String> processToService = record.getProcesses().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getServiceName(),
                            (existing, replacement) -> existing
                    ));

            for (SpanData span : record.getSpans()) {
                // 过滤掉span.kind等于'internal'的Span
                String spanKind = span.getSpanKind();
                if ("internal".equals(spanKind)) {
                    continue;
                }
                
                String serviceName = processToService.get(span.getProcessId());
                if (serviceName == null) continue;

                // 提取RPC信息
                String rpcService = span.getRpcService();
                String rpcMethod = span.getRpcMethod();

                if (rpcService != null && rpcMethod != null) {
                    RpcInterface rpcInterface = new RpcInterface(
                            serviceName,
                            rpcService + "/" + rpcMethod,
                            "grpc", // 基于示例数据，大多数是gRPC
                            span.getDuration(),
                            span.isError()
                    );
                    rpcInterfaces.add(rpcInterface);
                } else if (span.getHttpMethod() != null && span.getHttpUrl() != null) {
                    // HTTP接口
                    RpcInterface rpcInterface = new RpcInterface(
                            serviceName,
                            span.getHttpMethod() + " " + span.getHttpUrl(),
                            "http",
                            span.getDuration(),
                            span.isError()
                    );
                    rpcInterfaces.add(rpcInterface);
                } else if (span.getOperationName() != null) {
                    // 通用操作
                    RpcInterface rpcInterface = new RpcInterface(
                            serviceName,
                            span.getOperationName(),
                            "internal",
                            span.getDuration(),
                            span.isError()
                    );
                    rpcInterfaces.add(rpcInterface);
                }
            }
        }

        logger.info("提取到 {} 个RPC接口", rpcInterfaces.size());
        return rpcInterfaces;
    }

    /**
     * 验证trace文件格式
     */
    public boolean validateTraceFormat(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return content.contains("\"data\"") &&
                   content.contains("\"spans\"") &&
                   content.contains("\"traceID\"");
        } catch (Exception e) {
            logger.warn("验证trace文件格式失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 服务调用关系类
     */
    public static class ServiceCall {
        private final String fromService;
        private final String toService;
        private final String operation;
        private final Long duration;
        private final boolean isError;

        public ServiceCall(String fromService, String toService, String operation, Long duration, boolean isError) {
            this.fromService = fromService;
            this.toService = toService;
            this.operation = operation;
            this.duration = duration;
            this.isError = isError;
        }

        public String getFromService() { return fromService; }
        public String getToService() { return toService; }
        public String getOperation() { return operation; }
        public Long getDuration() { return duration; }
        public boolean isError() { return isError; }

        @Override
        public String toString() {
            return fromService + " -> " + toService + " (" + operation + ")";
        }
    }

    /**
     * RPC接口信息类
     */
    public static class RpcInterface {
        private final String serviceName;
        private final String interfaceName;
        private final String protocol;
        private final Long duration;
        private final boolean isError;

        public RpcInterface(String serviceName, String interfaceName, String protocol, Long duration, boolean isError) {
            this.serviceName = serviceName;
            // 对interfaceName进行正则匹配和转换
            // 例如: GET http://details:9080/details/0 转换成 GET /details/0
            if (interfaceName != null && protocol != null && "http".equals(protocol)) {
                // 匹配HTTP URL格式，提取方法和路径
                String httpPattern = "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+https?://[^/]+(/.*)?$";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(httpPattern);
                java.util.regex.Matcher matcher = pattern.matcher(interfaceName);

                if (matcher.matches()) {
                    String method = matcher.group(1);
                    String path = matcher.group(2);
                    if (path != null) {
                        this.interfaceName = method + " " + path;
                    } else {
                        this.interfaceName = method;
                    }
                } else {
                    this.interfaceName = interfaceName;
                }
            } else {
                this.interfaceName = interfaceName;
            }
            this.protocol = protocol;
            this.duration = duration;
            this.isError = isError;
        }

        public String getServiceName() { return serviceName; }
        public String getInterfaceName() { return interfaceName; }
        public String getProtocol() { return protocol; }
        public Long getDuration() { return duration; }
        public boolean isError() { return isError; }

        @Override
        public String toString() {
            return serviceName + ":" + interfaceName + " (" + protocol + ")";
        }
    }
}
