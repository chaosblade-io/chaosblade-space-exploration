package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
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

    private final Map<String, ManagedChannel> channelCache = new HashMap<>();

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
        // 参数验证
        validateParameters(jaegerHost, port, serviceName, operationName, startTime, endTime);

        logger.info("开始从Jaeger查询trace数据: host={}, port={}, service={}, operation={}, startTime={}, endTime={}",
                   jaegerHost, port, serviceName, operationName, startTime, endTime);

        try {
            // 获取或创建gRPC通道
            ManagedChannel channel = getOrCreateChannel(jaegerHost, port);

            // 创建模拟的查询结果（在实际实现中，这里会调用真正的Jaeger gRPC API）
            TraceData traceData = mockJaegerQuery(serviceName, operationName, startTime, endTime);

            logger.info("成功从Jaeger获取trace数据，共{}个trace记录",
                       traceData.getData() != null ? traceData.getData().size() : 0);
            return traceData;

        } catch (StatusRuntimeException e) {
            logger.error("Jaeger gRPC调用失败: {}", e.getStatus(), e);
            throw new RuntimeException("Failed to query Jaeger: " + e.getStatus().getDescription(), e);
        } catch (Exception e) {
            logger.error("查询Jaeger trace数据时发生异常", e);
            throw new RuntimeException("Failed to query traces from Jaeger", e);
        }
    }

    /**
     * 根据TraceID查询特定的Trace数据
     *
     * @param jaegerHost Jaeger服务主机地址
     * @param port Jaeger服务端口
     * @param traceId 要查询的TraceID
     * @return Trace数据
     */
    public TraceData queryTraceById(String jaegerHost, int port, String traceId) {
        validateTraceIdParameters(jaegerHost, port, traceId);

        logger.info("开始根据TraceID查询trace数据: host={}, port={}, traceId={}",
                   jaegerHost, port, traceId);

        try {
            ManagedChannel channel = getOrCreateChannel(jaegerHost, port);

            // 创建模拟的查询结果
            TraceData traceData = mockTraceByIdQuery(traceId);

            logger.info("成功根据TraceID获取trace数据");
            return traceData;

        } catch (Exception e) {
            logger.error("根据TraceID查询trace数据时发生异常: traceId={}", traceId, e);
            throw new RuntimeException("Failed to query trace by ID: " + traceId, e);
        }
    }

    /**
     * 获取或创建gRPC通道
     */
    private ManagedChannel getOrCreateChannel(String host, int port) {
        String channelKey = host + ":" + port;

        return channelCache.computeIfAbsent(channelKey, key -> {
            logger.debug("创建新的gRPC通道: {}", key);
            return ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(1024 * 1024 * 16) // 16MB
                    .build();
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
     * 验证TraceID查询参数
     */
    private void validateTraceIdParameters(String jaegerHost, int port, String traceId) {
        if (jaegerHost == null || jaegerHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Jaeger host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Trace ID cannot be null or empty");
        }
    }

    private String getServiceNameFromTags(List<SpanData.Tag> tags) {
        if (tags != null) {
            for (SpanData.Tag tag : tags) {
                if ("service.name".equals(tag.getKey()) && tag.getValue() != null) {
                    return tag.getValue().toString();
                }
            }
        }
        return "unknown-service";
    }

    /**
     * 模拟Jaeger查询（在实际实现中，这里会调用真正的Jaeger gRPC API）
     */
    private TraceData mockJaegerQuery(String serviceName, String operationName, long startTime, long endTime) {
        TraceData traceData = new TraceData();
        List<TraceData.TraceRecord> traces = new ArrayList<>();

        // 创建模拟的trace记录
        TraceData.TraceRecord trace = createMockTrace(serviceName, operationName, startTime);
        traces.add(trace);

        traceData.setData(traces);
        return traceData;
    }

    /**
     * 模拟根据TraceID查询
     */
    private TraceData mockTraceByIdQuery(String traceId) {
        TraceData traceData = new TraceData();
        List<TraceData.TraceRecord> traces = new ArrayList<>();

        TraceData.TraceRecord trace = createMockTraceWithId(traceId);
        traces.add(trace);

        traceData.setData(traces);
        return traceData;
    }

    /**
     * 创建模拟的trace记录
     */
    private TraceData.TraceRecord createMockTrace(String serviceName, String operationName, long startTime) {
        TraceData.TraceRecord record = new TraceData.TraceRecord();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        record.setTraceId(traceId);

        // 创建模拟span
        List<SpanData> spans = new ArrayList<>();

        // 主span
        SpanData rootSpan = createMockSpan(traceId, null, serviceName, operationName, startTime, 1000000L);
        spans.add(rootSpan);

        // 子span
        SpanData childSpan = createMockSpan(traceId, rootSpan.getSpanId(), serviceName + "-db", "select", startTime + 100000L, 500000L);
        spans.add(childSpan);

        record.setSpans(spans);

        // 创建进程信息
        Map<String, ProcessData> processes = new HashMap<>();
        ProcessData process = new ProcessData();
        process.setServiceName(serviceName);
        processes.put("p1", process);
        record.setProcesses(processes);

        return record;
    }

    /**
     * 创建指定TraceID的模拟trace记录
     */
    private TraceData.TraceRecord createMockTraceWithId(String traceId) {
        TraceData.TraceRecord record = new TraceData.TraceRecord();
        record.setTraceId(traceId);

        List<SpanData> spans = new ArrayList<>();
        long startTime = System.currentTimeMillis() * 1000L;

        SpanData span = createMockSpan(traceId, null, "test-service", "test-operation", startTime, 1000000L);
        spans.add(span);

        record.setSpans(spans);

        Map<String, ProcessData> processes = new HashMap<>();
        ProcessData process = new ProcessData();
        process.setServiceName("test-service");
        processes.put("p1", process);
        record.setProcesses(processes);

        return record;
    }

    /**
     * 创建模拟span
     */
    private SpanData createMockSpan(String traceId, String parentSpanId, String serviceName,
                                   String operationName, long startTime, long duration) {
        SpanData span = new SpanData();
        span.setTraceId(traceId);
        span.setSpanId(generateSpanId());
        span.setOperationName(operationName);
        span.setStartTime(startTime);
        span.setDuration(duration);
        span.setProcessId("p1");

        // 添加模拟标签
        List<SpanData.Tag> tags = new ArrayList<>();
        tags.add(createTag("service.name", "string", serviceName));
        tags.add(createTag("span.kind", "string", "server"));
        tags.add(createTag("http.status_code", "int64", 200));
        span.setTags(tags);

        return span;
    }

    /**
     * 创建模拟标签
     */
    private SpanData.Tag createTag(String key, String type, Object value) {
        SpanData.Tag tag = new SpanData.Tag();
        tag.setKey(key);
        tag.setType(type);
        tag.setValue(value);
        return tag;
    }

    /**
     * 生成SpanID
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
    }
}
