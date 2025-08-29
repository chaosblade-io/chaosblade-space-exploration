package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.jaegertracing.api_v2.QueryServiceGrpc;
import io.jaegertracing.api_v2.Query;
import io.jaegertracing.api_v2.Model;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Jaeger查询服务
 *
 * 负责与Jaeger后端服务通信，获取Trace数据
 */
@Service
public class JaegerQueryService {

    private static final Logger logger = LoggerFactory.getLogger(JaegerQueryService.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_SPANS_PER_QUERY = 1000;
    private static final int DEFAULT_TRACE_LIMIT = 20;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final Map<String, ManagedChannel> channelCache = new HashMap<>();
    private final Map<String, QueryServiceGrpc.QueryServiceBlockingStub> stubCache = new HashMap<>();

    /**
     * 根据服务名和操作名查询Trace数据
     *
     * @param jaegerHost Jaeger服务主机地址
     * @param port Jaeger服务端口
     * @param serviceName 服务名称
     * @param operationName 操作名称
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesByOperation(String jaegerHost, int port, String serviceName,
                                           String operationName, long startTime, long endTime) {
        return queryTracesByOperation(jaegerHost, port, serviceName, operationName, startTime, endTime, DEFAULT_TRACE_LIMIT);
    }

    /**
     * 根据服务名和操作名查询Trace数据
     *
     * @param jaegerHost Jaeger服务主机地址
     * @param port Jaeger服务端口
     * @param serviceName 服务名称
     * @param operationName 操作名称
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @param limit 返回的最大Trace数量
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesByOperation(String jaegerHost, int port, String serviceName,
                                           String operationName, long startTime, long endTime, int limit) {
        // 参数验证
        validateParameters(jaegerHost, port, serviceName, operationName, startTime, endTime);

        logger.info("开始从Jaeger查询trace数据: host={}, port={}, service={}, operation={}, startTime={}, endTime={}, limit={}",
                   jaegerHost, port, serviceName, operationName, startTime, endTime, limit);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                // 获取或创建gRPC通道
                ManagedChannel channel = getOrCreateChannel(jaegerHost, port);
                
                // 获取或创建gRPC stub
                QueryServiceGrpc.QueryServiceBlockingStub stub = getOrCreateStub(channel, jaegerHost, port);

                // 构建查询参数
                Query.TraceQueryParameters queryParameters = Query.TraceQueryParameters.newBuilder()
                        .setServiceName(serviceName)
                        .setOperationName(operationName)
                        .setStartTimeMin(com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(startTime / 1_000_000)
                                .setNanos((int) ((startTime % 1_000_000) * 1000)))
                        .setStartTimeMax(com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(endTime / 1_000_000)
                                .setNanos((int) ((endTime % 1_000_000) * 1000)))
                        .setSearchDepth(MAX_SPANS_PER_QUERY)
                        .build();

                // 构建查询请求
                Query.FindTracesRequest request = Query.FindTracesRequest.newBuilder()
                        .setQuery(queryParameters)
                        .build();

                // 执行查询
                Iterator<Query.SpansResponseChunk> responseIterator = stub.findTraces(request);

                // 处理响应
                TraceData traceData = processFindTracesResponse(responseIterator, limit);

                logger.info("成功从Jaeger获取trace数据，共{}个trace记录",
                           traceData.getData() != null ? traceData.getData().size() : 0);
                return traceData;

            } catch (StatusRuntimeException e) {
                lastException = e;
                logger.warn("Jaeger gRPC调用失败 (尝试 {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getStatus(), e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("查询被中断", ie);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                logger.warn("查询Jaeger trace数据时发生异常 (尝试 {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("查询被中断", ie);
                    }
                }
            }
        }
        
        logger.error("经过{}次尝试后仍无法从Jaeger查询trace数据", MAX_RETRY_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to query Jaeger after " + MAX_RETRY_ATTEMPTS + " attempts: " + lastException.getMessage(), lastException);
    }

    /**
     * 获取或创建gRPC通道
     */
    private ManagedChannel getOrCreateChannel(String host, int port) {
        String channelKey = host + ":" + port;

        return channelCache.computeIfAbsent(channelKey, key -> {
            logger.debug("创建新的gRPC通道: {}", key);
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(1024 * 1024 * 16) // 16MB
                    .build();
            
            // 测试连接
            try {
                channel.getState(true);
            } catch (Exception e) {
                logger.warn("创建gRPC通道后测试连接失败: {}", e.getMessage());
            }
            
            return channel;
        });
    }

    /**
     * 获取或创建gRPC stub
     */
    private QueryServiceGrpc.QueryServiceBlockingStub getOrCreateStub(ManagedChannel channel, String host, int port) {
        String stubKey = host + ":" + port;

        return stubCache.computeIfAbsent(stubKey, key -> {
            logger.debug("创建新的gRPC stub: {}", key);
            return QueryServiceGrpc.newBlockingStub(channel);
        });
    }

    /**
     * 验证查询参数
     */
    private void validateParameters(String jaegerHost, int port, String serviceName,
                                   String operationName, long startTime, long endTime) {
        if (jaegerHost == null || jaegerHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Jaeger host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        if (operationName == null || operationName.trim().isEmpty()) {
            throw new IllegalArgumentException("Operation name cannot be null or empty");
        }
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (endTime - startTime > Duration.ofDays(7).toNanos() / 1000) {
            throw new IllegalArgumentException("Time range cannot exceed 7 days");
        }
    }

    /**
     * 处理FindTraces响应
     */
    private TraceData processFindTracesResponse(Iterator<Query.SpansResponseChunk> responseIterator, int limit) {
        TraceData traceData = new TraceData();
        List<TraceData.TraceRecord> traces = new ArrayList<>();
        
        // 用于存储所有span，按traceId分组
        Map<String, List<SpanData>> spansByTraceId = new HashMap<>();
        Map<String, Map<String, ProcessData>> processesByTraceId = new HashMap<>();
        
        // 处理响应流中的每个chunk
        while (responseIterator.hasNext() && traces.size() < limit) {
            Query.SpansResponseChunk chunk = responseIterator.next();
            
            // 处理chunk中的每个span
            for (Model.Span span : chunk.getSpansList()) {
                String traceId = bytesToHex(span.getTraceId().toByteArray());
                
                // 转换span
                SpanData spanData = convertSpan(span);
                
                // 按traceId分组存储span
                spansByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(spanData);
                
                // 处理进程信息
                if (span.hasProcess()) {
                    ProcessData processData = convertProcess(span.getProcess());
                    String processId = span.getProcessId();
                    
                    processesByTraceId.computeIfAbsent(traceId, k -> new HashMap<>())
                            .put(processId, processData);
                }
            }
        }
        
        // 构建trace记录，限制数量
        int count = 0;
        for (Map.Entry<String, List<SpanData>> entry : spansByTraceId.entrySet()) {
            if (count >= limit) {
                break;
            }
            
            String traceId = entry.getKey();
            List<SpanData> spanList = entry.getValue();
            
            TraceData.TraceRecord traceRecord = new TraceData.TraceRecord();
            traceRecord.setTraceId(traceId);
            traceRecord.setSpans(spanList);
            traceRecord.setProcesses(processesByTraceId.getOrDefault(traceId, new HashMap<>()));
            
            traces.add(traceRecord);
            count++;
        }
        
        traceData.setData(traces);
        return traceData;
    }

    /**
     * 转换Jaeger Span为项目中的SpanData
     */
    private SpanData convertSpan(Model.Span span) {
        SpanData spanData = new SpanData();
        
        spanData.setTraceId(bytesToHex(span.getTraceId().toByteArray()));
        spanData.setSpanId(bytesToHex(span.getSpanId().toByteArray()));
        spanData.setOperationName(span.getOperationName());
        spanData.setStartTime(span.getStartTime().getSeconds() * 1_000_000 + span.getStartTime().getNanos() / 1000);
        spanData.setDuration(span.getDuration().getSeconds() * 1_000_000 + span.getDuration().getNanos() / 1000);
        spanData.setProcessId(span.getProcessId());
        spanData.setWarnings(span.getWarningsList());
        
        // 转换标签
        List<SpanData.Tag> tags = new ArrayList<>();
        for (Model.KeyValue keyValue : span.getTagsList()) {
            SpanData.Tag tag = new SpanData.Tag();
            tag.setKey(keyValue.getKey());
            tag.setType(convertValueType(keyValue.getVType()));
            tag.setValue(getValueFromKeyValue(keyValue));
            tags.add(tag);
        }
        spanData.setTags(tags);
        
        // 转换引用关系
        List<SpanData.SpanReference> references = new ArrayList<>();
        for (Model.SpanRef spanRef : span.getReferencesList()) {
            SpanData.SpanReference reference = new SpanData.SpanReference();
            reference.setRefType(convertSpanRefType(spanRef.getRefType()));
            reference.setTraceId(bytesToHex(spanRef.getTraceId().toByteArray()));
            reference.setSpanId(bytesToHex(spanRef.getSpanId().toByteArray()));
            references.add(reference);
        }
        spanData.setReferences(references);
        
        // 转换日志
        List<SpanData.LogEntry> logs = new ArrayList<>();
        for (Model.Log log : span.getLogsList()) {
            SpanData.LogEntry logEntry = new SpanData.LogEntry();
            logEntry.setTimestamp(log.getTimestamp().getSeconds() * 1_000_000 + log.getTimestamp().getNanos() / 1000);
            
            List<SpanData.Tag> logFields = new ArrayList<>();
            for (Model.KeyValue keyValue : log.getFieldsList()) {
                SpanData.Tag tag = new SpanData.Tag();
                tag.setKey(keyValue.getKey());
                tag.setType(convertValueType(keyValue.getVType()));
                tag.setValue(getValueFromKeyValue(keyValue));
                logFields.add(tag);
            }
            logEntry.setFields(logFields);
            logs.add(logEntry);
        }
        spanData.setLogs(logs);
        
        return spanData;
    }

    /**
     * 转换Jaeger Process为项目中的ProcessData
     */
    private ProcessData convertProcess(Model.Process process) {
        ProcessData processData = new ProcessData();
        processData.setServiceName(process.getServiceName());
        
        // 转换标签
        List<SpanData.Tag> tags = new ArrayList<>();
        for (Model.KeyValue keyValue : process.getTagsList()) {
            SpanData.Tag tag = new SpanData.Tag();
            tag.setKey(keyValue.getKey());
            tag.setType(convertValueType(keyValue.getVType()));
            tag.setValue(getValueFromKeyValue(keyValue));
            tags.add(tag);
        }
        processData.setTags(tags);
        
        return processData;
    }

    /**
     * 转换ValueType为字符串表示
     */
    private String convertValueType(Model.ValueType valueType) {
        switch (valueType) {
            case STRING: return "string";
            case BOOL: return "bool";
            case INT64: return "int64";
            case FLOAT64: return "float64";
            case BINARY: return "binary";
            default: return "string";
        }
    }

    /**
     * 从KeyValue中获取值
     */
    private Object getValueFromKeyValue(Model.KeyValue keyValue) {
        switch (keyValue.getVType()) {
            case STRING: return keyValue.getVStr();
            case BOOL: return keyValue.getVBool();
            case INT64: return keyValue.getVInt64();
            case FLOAT64: return keyValue.getVFloat64();
            case BINARY: return keyValue.getVBinary().toByteArray();
            default: return keyValue.getVStr();
        }
    }

    /**
     * 转换SpanRefType为字符串表示
     */
    private String convertSpanRefType(Model.SpanRefType refType) {
        switch (refType) {
            case CHILD_OF: return "CHILD_OF";
            case FOLLOWS_FROM: return "FOLLOWS_FROM";
            default: return "CHILD_OF";
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 关闭所有gRPC通道
     */
    @PreDestroy
    public void shutdown() {
        logger.info("关闭 Jaeger gRPC 通道...");
        for (Map.Entry<String, ManagedChannel> entry : channelCache.entrySet()) {
            try {
                ManagedChannel channel = entry.getValue();
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("强制关闭 gRPC 通道: {}", entry.getKey());
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("关闭 gRPC 通道被中断: {}", entry.getKey());
                Thread.currentThread().interrupt();
            }
        }
        channelCache.clear();
        stubCache.clear();
    }
}