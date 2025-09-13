package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.JaegerSource;
import com.chaosblade.svc.topo.model.trace.ProcessData;
import com.chaosblade.svc.topo.model.trace.SpanData;
import com.chaosblade.svc.topo.model.trace.TraceData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.jaegertracing.api_v2.QueryServiceGrpc;
import io.jaegertracing.api_v2.Query;
import io.jaegertracing.api_v2.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
     * 通过HTTP API查询Trace数据（不指定特定服务和操作）
     *
     * @param jaegerHost Jaeger服务主机地址
     * @param port Jaeger服务端口
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesHttp(String jaegerHost, int port, long startTime, long endTime) {
        return queryTracesHttp(jaegerHost, port, startTime, endTime, DEFAULT_TRACE_LIMIT);
    }

    /**
     * 通过HTTP API查询Trace数据（不指定特定服务和操作）
     *
     * @param jaegerHost Jaeger服务主机地址
     * @param port Jaeger服务端口
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @param limit 返回的最大Trace数量
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesHttp(String jaegerHost, int port, long startTime, long endTime, int limit) {
        // 参数验证
        if (jaegerHost == null || jaegerHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Jaeger host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        logger.info("开始从Jaeger通过HTTP API查询trace数据 (/api/traces): host={}, port={}, startTime={}, endTime={}, limit={}",
                   jaegerHost, port, startTime, endTime, limit);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 构建查询URL
                // 将微秒转换为毫秒
                long startMs = startTime / 1000;
                long endMs = endTime / 1000;

                // 构建查询参数
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("http://").append(jaegerHost).append(":").append(port)
                          .append("/api/traces?limit=").append(limit)
                          .append("&start=").append(startMs)
                          .append("&end=").append(endMs);

                String url = urlBuilder.toString();
                logger.debug("Jaeger HTTP查询URL: {}", url);

                // 创建HTTP GET请求
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("Accept", "application/json");

                // 执行HTTP请求
                ClassicHttpResponse response = httpClient.execute(httpGet);

                // 检查响应状态
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    String responseContent = EntityUtils.toString(response.getEntity());
                    logger.error("Jaeger HTTP API调用失败，状态码: {}, 响应内容: {}", statusCode, responseContent);
                    throw new RuntimeException("Jaeger HTTP API调用失败，状态码: " + statusCode);
                }

                // 解析响应内容
                String jsonResponse = EntityUtils.toString(response.getEntity());
                logger.debug("Jaeger HTTP API响应长度: {} 字符", jsonResponse.length());

                // 将JSON响应转换为TraceData对象
                TraceData traceData = convertHttpTraceResponse(jsonResponse);

                logger.info("成功从Jaeger通过HTTP API获取trace数据，共{}个trace记录",
                           traceData.getData() != null ? traceData.getData().size() : 0);
                return traceData;

            } catch (Exception e) {
                lastException = e;
                logger.warn("通过HTTP API查询Jaeger trace数据时发生异常 (尝试 {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
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

        logger.error("经过{}次尝试后仍无法从Jaeger通过HTTP API查询trace数据", MAX_RETRY_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to query Jaeger via HTTP API after " + MAX_RETRY_ATTEMPTS + " attempts: " + lastException.getMessage(), lastException);
    }

    /**
     * 根据服务名通过HTTP API查询Trace数据
     *
     * @param jaegerSource Jaeger数据源配置
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesByServiceHttp(JaegerSource jaegerSource,
                                             long startTime, long endTime) {
        return queryTracesByServiceHttp(jaegerSource, startTime, endTime, jaegerSource.getLimit() != null ? jaegerSource.getLimit() : DEFAULT_TRACE_LIMIT);
    }

    /**
     * 根据服务名通过HTTP API查询Trace数据
     *
     * @param jaegerSource Jaeger数据源配置
     * @param startTime 查询开始时间（微秒）
     * @param endTime 查询结束时间（微秒）
     * @param limit 返回的最大Trace数量
     * @return Trace数据
     * @throws IllegalArgumentException 当参数无效时
     * @throws RuntimeException 当连接或查询失败时
     */
    public TraceData queryTracesByServiceHttp(JaegerSource jaegerSource,
                                             long startTime, long endTime, int limit) {
        // 参数验证
        validateJaegerSource(jaegerSource, startTime, endTime);

        logger.info("开始从Jaeger通过HTTP API查询trace数据 (/api/traces): host={}, port={}, service={}, startTime={}, endTime={}, limit={}",
                   jaegerSource.getHost(), jaegerSource.getHttpPort(), jaegerSource.getEntryService(), startTime, endTime, limit);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 构建查询URL
                // 将微秒转换为毫秒
                long startMs = startTime / 1000;
                long endMs = endTime / 1000;

                // 构建查询参数
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("http://").append(jaegerSource.getHost()).append(":").append(jaegerSource.getHttpPort())
                          .append(jaegerSource.getBasePath() != null ? jaegerSource.getBasePath() : "/api/traces")
                          .append("?limit=").append(limit)
                          .append("&service=").append(jaegerSource.getEntryService())
                          .append("&start=").append(startMs)
                          .append("&end=").append(endMs);

                String url = urlBuilder.toString();
                logger.debug("Jaeger HTTP查询URL: {}", url);

                // 创建HTTP GET请求
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("Accept", "application/json");

                // 执行HTTP请求
                ClassicHttpResponse response = httpClient.execute(httpGet);

                // 检查响应状态
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    String responseContent = EntityUtils.toString(response.getEntity());
                    logger.error("Jaeger HTTP API调用失败，状态码: {}, 响应内容: {}", statusCode, responseContent);
                    throw new RuntimeException("Jaeger HTTP API调用失败，状态码: " + statusCode);
                }

                // 解析响应内容
                String jsonResponse = EntityUtils.toString(response.getEntity());
                logger.debug("Jaeger HTTP API响应长度: {} 字符", jsonResponse.length());

                // 将JSON响应转换为TraceData对象
                TraceData traceData = convertHttpTraceResponse(jsonResponse);

                logger.info("成功从Jaeger通过HTTP API获取trace数据，共{}个trace记录",
                           traceData.getData() != null ? traceData.getData().size() : 0);
                return traceData;

            } catch (Exception e) {
                lastException = e;
                logger.warn("通过HTTP API查询Jaeger trace数据时发生异常 (尝试 {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
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

        logger.error("经过{}次尝试后仍无法从Jaeger通过HTTP API查询trace数据", MAX_RETRY_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to query Jaeger via HTTP API after " + MAX_RETRY_ATTEMPTS + " attempts: " + lastException.getMessage(), lastException);
    }

    /**
     * 根据服务名和操作名通过HTTP API查询Trace数据
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
    public TraceData queryTracesByOperationHttp(String jaegerHost, int port, String serviceName,
                                               String operationName, long startTime, long endTime) {
        return queryTracesByOperationHttp(jaegerHost, port, serviceName, operationName, startTime, endTime, DEFAULT_TRACE_LIMIT);
    }

    /**
     * 根据服务名和操作名通过HTTP API查询Trace数据
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
    public TraceData queryTracesByOperationHttp(String jaegerHost, int port, String serviceName,
                                               String operationName, long startTime, long endTime, int limit) {
        // 参数验证
        validateParameters(jaegerHost, port, serviceName, operationName, startTime, endTime);

        logger.info("开始从Jaeger通过HTTP API查询trace数据 (/api/traces): host={}, port={}, service={}, operation={}, startTime={}, endTime={}, limit={}",
                   jaegerHost, port, serviceName, operationName, startTime, endTime, limit);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 构建查询URL
                // 将微秒转换为毫秒
                long startMs = startTime / 1000;
                long endMs = endTime / 1000;

                // 构建查询参数
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("http://").append(jaegerHost).append(":").append(port)
                          .append("/api/traces?limit=").append(limit)
                          .append("&service=").append(serviceName)
                          .append("&operation=").append(operationName)
                          .append("&start=").append(startMs)
                          .append("&end=").append(endMs);

                String url = urlBuilder.toString();
                logger.debug("Jaeger HTTP查询URL: {}", url);

                // 创建HTTP GET请求
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("Accept", "application/json");

                // 执行HTTP请求
                ClassicHttpResponse response = httpClient.execute(httpGet);

                // 检查响应状态
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    String responseContent = EntityUtils.toString(response.getEntity());
                    logger.error("Jaeger HTTP API调用失败，状态码: {}, 响应内容: {}", statusCode, responseContent);
                    throw new RuntimeException("Jaeger HTTP API调用失败，状态码: " + statusCode);
                }

                // 解析响应内容
                String jsonResponse = EntityUtils.toString(response.getEntity());
                logger.debug("Jaeger HTTP API响应长度: {} 字符", jsonResponse.length());

                // 将JSON响应转换为TraceData对象
                TraceData traceData = convertHttpTraceResponse(jsonResponse);

                logger.info("成功从Jaeger通过HTTP API获取trace数据，共{}个trace记录",
                           traceData.getData() != null ? traceData.getData().size() : 0);
                return traceData;

            } catch (Exception e) {
                lastException = e;
                logger.warn("通过HTTP API查询Jaeger trace数据时发生异常 (尝试 {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
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

        logger.error("经过{}次尝试后仍无法从Jaeger通过HTTP API查询trace数据", MAX_RETRY_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to query Jaeger via HTTP API after " + MAX_RETRY_ATTEMPTS + " attempts: " + lastException.getMessage(), lastException);
    }

    /**
     * 将HTTP API的JSON响应转换为TraceData对象
     *
     * @param jsonResponse HTTP API的JSON响应
     * @return TraceData对象
     */
    private TraceData convertHttpTraceResponse(String jsonResponse) {
        TraceData traceData = new TraceData();

        try {
            // 使用Jackson解析JSON响应
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // 获取data数组
            JsonNode dataArray = rootNode.get("data");
            if (dataArray != null && dataArray.isArray()) {
                List<TraceData.TraceRecord> traces = new ArrayList<>();

                // 遍历每个trace
                for (JsonNode traceNode : dataArray) {
                    TraceData.TraceRecord traceRecord = new TraceData.TraceRecord();

                    // 获取traceID
                    JsonNode traceIdNode = traceNode.get("traceID");
                    if (traceIdNode != null) {
                        traceRecord.setTraceId(traceIdNode.asText());
                    }

                    // 解析spans
                    JsonNode spansArray = traceNode.get("spans");
                    if (spansArray != null && spansArray.isArray()) {
                        List<SpanData> spans = new ArrayList<>();

                        // 遍历每个span
                        for (JsonNode spanNode : spansArray) {
                            SpanData spanData = convertSpanData(spanNode);
                            spans.add(spanData);
                        }

                        traceRecord.setSpans(spans);
                    }

                    // 解析processes（如果存在）
                    JsonNode processesNode = traceNode.get("processes");
                    if (processesNode != null && processesNode.isObject()) {
                        Map<String, ProcessData> processes = new HashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = processesNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String processId = entry.getKey();
                            JsonNode processNode = entry.getValue();
                            ProcessData processData = convertProcessData(processNode);
                            processes.put(processId, processData);
                        }
                        traceRecord.setProcesses(processes);
                    } else {
                        // 如果没有processes字段，创建空的Map
                        traceRecord.setProcesses(new HashMap<>());
                    }

                    traces.add(traceRecord);
                }

                traceData.setData(traces);
            }

        } catch (Exception e) {
            logger.error("解析Jaeger HTTP API响应时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("解析Jaeger HTTP API响应失败", e);
        }

        return traceData;
    }

    /**
     * 将JSON节点转换为SpanData对象
     */
    private SpanData convertSpanData(JsonNode spanNode) {
        SpanData spanData = new SpanData();

        // 解析基本属性
        spanData.setTraceId(getTextValue(spanNode, "traceID"));
        spanData.setSpanId(getTextValue(spanNode, "spanID"));
        spanData.setOperationName(getTextValue(spanNode, "operationName"));
        spanData.setProcessId(getTextValue(spanNode, "processID"));

        // 解析时间相关属性
        spanData.setStartTime(getLongValue(spanNode, "startTime"));
        spanData.setDuration(getLongValue(spanNode, "duration"));

        // 解析tags
        JsonNode tagsArray = spanNode.get("tags");
        if (tagsArray != null && tagsArray.isArray()) {
            List<SpanData.Tag> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsArray) {
                SpanData.Tag tag = new SpanData.Tag();
                tag.setKey(getTextValue(tagNode, "key"));
                tag.setType(getTextValue(tagNode, "type"));
                tag.setValue(getObjectValue(tagNode, "value"));
                tags.add(tag);
            }
            spanData.setTags(tags);
        }

        // 解析references
        JsonNode referencesArray = spanNode.get("references");
        if (referencesArray != null && referencesArray.isArray()) {
            List<SpanData.SpanReference> references = new ArrayList<>();
            for (JsonNode refNode : referencesArray) {
                SpanData.SpanReference reference = new SpanData.SpanReference();
                reference.setRefType(getTextValue(refNode, "refType"));
                reference.setTraceId(getTextValue(refNode, "traceID"));
                reference.setSpanId(getTextValue(refNode, "spanID"));
                references.add(reference);
            }
            spanData.setReferences(references);
        }

        // 解析logs
        JsonNode logsArray = spanNode.get("logs");
        if (logsArray != null && logsArray.isArray()) {
            List<SpanData.LogEntry> logs = new ArrayList<>();
            for (JsonNode logNode : logsArray) {
                SpanData.LogEntry logEntry = new SpanData.LogEntry();
                logEntry.setTimestamp(getLongValue(logNode, "timestamp"));

                // 解析log fields
                JsonNode fieldsArray = logNode.get("fields");
                if (fieldsArray != null && fieldsArray.isArray()) {
                    List<SpanData.Tag> fields = new ArrayList<>();
                    for (JsonNode fieldNode : fieldsArray) {
                        SpanData.Tag field = new SpanData.Tag();
                        field.setKey(getTextValue(fieldNode, "key"));
                        field.setType(getTextValue(fieldNode, "type"));
                        field.setValue(getObjectValue(fieldNode, "value"));
                        fields.add(field);
                    }
                    logEntry.setFields(fields);
                }
                logs.add(logEntry);
            }
            spanData.setLogs(logs);
        }

        // 解析warnings
        JsonNode warningsArray = spanNode.get("warnings");
        if (warningsArray != null && warningsArray.isArray()) {
            List<String> warnings = new ArrayList<>();
            for (JsonNode warningNode : warningsArray) {
                warnings.add(warningNode.asText());
            }
            spanData.setWarnings(warnings);
        }

        return spanData;
    }

    /**
     * 将JSON节点转换为ProcessData对象
     */
    private ProcessData convertProcessData(JsonNode processNode) {
        ProcessData processData = new ProcessData();

        // 解析serviceName
        processData.setServiceName(getTextValue(processNode, "serviceName"));

        // 解析tags
        JsonNode tagsArray = processNode.get("tags");
        if (tagsArray != null && tagsArray.isArray()) {
            List<SpanData.Tag> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsArray) {
                SpanData.Tag tag = new SpanData.Tag();
                tag.setKey(getTextValue(tagNode, "key"));
                tag.setType(getTextValue(tagNode, "type"));
                tag.setValue(getObjectValue(tagNode, "value"));
                tags.add(tag);
            }
            processData.setTags(tags);
        }

        return processData;
    }

    /**
     * 安全地获取JSON节点的文本值
     */
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null ? fieldNode.asText() : null;
    }

    /**
     * 安全地获取JSON节点的长整型值
     */
    private Long getLongValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null ? fieldNode.asLong() : null;
    }

    /**
     * 安全地获取JSON节点的对象值
     */
    private Object getObjectValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null) {
            return null;
        }

        switch (fieldNode.getNodeType()) {
            case STRING:
                return fieldNode.asText();
            case NUMBER:
                if (fieldNode.isInt()) {
                    return fieldNode.asInt();
                } else if (fieldNode.isLong()) {
                    return fieldNode.asLong();
                } else if (fieldNode.isDouble()) {
                    return fieldNode.asDouble();
                }
                break;
            case BOOLEAN:
                return fieldNode.asBoolean();
            default:
                return fieldNode.toString();
        }

        return fieldNode.toString();
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
     * 验证服务查询参数
     */
    private void validateServiceParameters(String jaegerHost, int port, String serviceName,
                                         long startTime, long endTime) {
        if (jaegerHost == null || jaegerHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Jaeger host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (endTime - startTime > Duration.ofDays(7).toNanos() / 1000) {
            throw new IllegalArgumentException("Time range cannot exceed 7 days");
        }
    }

    /**
     * 验证JaegerSource查询参数
     */
    private void validateJaegerSource(JaegerSource jaegerSource, long startTime, long endTime) {
        if (jaegerSource == null) {
            throw new IllegalArgumentException("JaegerSource cannot be null");
        }
        if (jaegerSource.getHost() == null || jaegerSource.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Jaeger host cannot be null or empty");
        }
        if (jaegerSource.getHttpPort() == null || jaegerSource.getHttpPort() <= 0 || jaegerSource.getHttpPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (jaegerSource.getEntryService() == null || jaegerSource.getEntryService().trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
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
