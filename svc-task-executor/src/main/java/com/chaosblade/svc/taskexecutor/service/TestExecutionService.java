package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskexecutor.dto.EnhancedSimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.EnhancedFaultTargetDTO;
import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import com.chaosblade.svc.taskexecutor.entity.HttpReqDef;
import com.chaosblade.svc.taskexecutor.repository.DetectionTaskRepository;
import com.chaosblade.svc.taskexecutor.repository.HttpReqDefRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyNodeRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyEdgeRepository;
import com.chaosblade.svc.taskexecutor.repository.SystemRepository;
import com.chaosblade.svc.taskexecutor.entity.ApiTopology;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskexecutor.entity.System;
import com.chaosblade.svc.taskexecutor.config.RecordingProperties;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TestExecutionService {

    private final TestCaseGenerationService testCaseGenerationService;
    private final DetectionTaskRepository detectionTaskRepository;
    private final HttpReqDefRepository httpReqDefRepository;
    private final ApiTopologyRepository apiTopologyRepository;
    private final ApiTopologyNodeRepository apiTopologyNodeRepository;
    private final ApiTopologyEdgeRepository apiTopologyEdgeRepository;
    private final SystemRepository systemRepository;
    private final RecordingProperties recordingProperties;
    private final KubernetesService kubernetesService;
    private final AuthenticationService authenticationService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String faultBaseUrl = "http://1.94.151.57:8103";

    public static class ExecutionResult {
        public String executionId;
        public String status; // PENDING/RUNNING/COMPLETED/FAILED
        public String targetService; // 首个目标服务名（兼容旧字段）
        public java.util.List<String> targetServices; // 所有目标服务名
        public String requestMethod;
        public String requestUrl;
        public Long faultConfigId; // 可选
        public Map<String,Object> faultResponse; // 最后一个故障的返回（兼容旧字段）
        public java.util.List<Map<String,Object>> faultResponses; // 每个故障的创建返回（ApiResponse）
        public java.util.List<Map<String,Object>> faultSpecs; // 实际发送到调度器的 spec 负载（按顺序）
        public java.util.List<Map<String,Object>> recoveryResults; // 每个 bladeName 的恢复结果
        public EnhancedSimplifiedTestCaseDTO testCase; // 完整用例定义（含 faults/namespace/serviceName/faultDefinition）
        public Integer httpStatus;
        public Map<String,List<String>> responseHeaders;
        public String responseBodySnip;
        public String error;
    }

    public static class PatternAnalysisResult {
        public String executionId;
        public String status; // PENDING/RUNNING/COMPLETED/FAILED
        public Long taskId;
        public Long reqDefId;
        public String namespace;
        public java.util.List<String> serviceList;
        public Map<String,Object> proxyResponse; // svc-reqrsp-proxy 返回
        public String error;
    }
    public static class CombinedExecutionResult {
        public String executionId;
        public String status; // PENDING/RUNNING/COMPLETED/FAILED
        public Long taskId;
        public String namespace;
        public java.util.List<String> serviceList;
        public Map<String,Object> analysisResponse; // 来自 analyze
        public java.util.List<Map<String,Object>> recordingStartResults; // 每个服务启动录制的返回
        public TestExecutionService.ExecutionResult faultExecution; // 复用现有故障执行结果
        public java.util.Map<String, Object> recordedEntriesByService; // serviceName -> entries 响应
        public String error;
    }

    public static class BatchExecutionResult {
        public String batchId;
        public String status; // RUNNING/COMPLETED/FAILED/CANCELLED
        public Long taskId;
        public int totalCases;
        public int currentIndex; // 已完成的用例数量
        public java.util.List<CombinedExecutionResult> caseResults = new java.util.ArrayList<>();
        public java.util.List<Long> caseWaitMillis = new java.util.ArrayList<>();
        public String error;
    }

    private final Map<String, CombinedExecutionResult> combinedStore = new ConcurrentHashMap<>();

    private final Map<String, BatchExecutionResult> batchStore = new ConcurrentHashMap<>();


    private final Map<String, ExecutionResult> store = new ConcurrentHashMap<>();
    private final Map<String, PatternAnalysisResult> patternStore = new ConcurrentHashMap<>();

    public TestExecutionService(TestCaseGenerationService testCaseGenerationService,
                                DetectionTaskRepository detectionTaskRepository,
                                HttpReqDefRepository httpReqDefRepository,
                                ApiTopologyRepository apiTopologyRepository,
                                ApiTopologyNodeRepository apiTopologyNodeRepository,
                                ApiTopologyEdgeRepository apiTopologyEdgeRepository,
                                SystemRepository systemRepository,
                                RecordingProperties recordingProperties,
                                KubernetesService kubernetesService,
                                AuthenticationService authenticationService) {
        this.testCaseGenerationService = testCaseGenerationService;
        this.detectionTaskRepository = detectionTaskRepository;
        this.httpReqDefRepository = httpReqDefRepository;
        this.apiTopologyRepository = apiTopologyRepository;
        this.apiTopologyNodeRepository = apiTopologyNodeRepository;
        this.apiTopologyEdgeRepository = apiTopologyEdgeRepository;
        this.systemRepository = systemRepository;
        this.recordingProperties = recordingProperties;
        this.kubernetesService = kubernetesService;
        this.authenticationService = authenticationService;
    }

    // 启动请求模式分析：选择叶子节点，组装 payload 并调用 svc-reqrsp-proxy
    public PatternAnalysisResult startPatternAnalysis(Long taskId) {
        var task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND","任务不存在: "+taskId));
        // 获取拓扑
        ApiTopology topo = apiTopologyRepository.findBySystemIdAndApiId(task.getSystemId(), task.getApiId())
                .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND","未找到拓扑: system="+task.getSystemId()+", api="+task.getApiId()));
        var nodes = apiTopologyNodeRepository.findByTopologyId(topo.getId());
        var edges = apiTopologyEdgeRepository.findByTopologyId(topo.getId());
        if (nodes.isEmpty()) throw new BusinessException("NO_TOPOLOGY_NODES","拓扑无节点");
        // 选叶子：无出边
        java.util.Set<Long> fromSet = new java.util.HashSet<>();
        for (ApiTopologyEdge e : edges) fromSet.add(e.getFromNodeId());
        ApiTopologyNode leaf = null;
        for (ApiTopologyNode n : nodes) { if (!fromSet.contains(n.getId())) { leaf = n; break; } }
        if (leaf==null) leaf = nodes.get(0);
        // namespace
        System sys = systemRepository.findById(task.getSystemId())
                .orElseThrow(() -> new BusinessException("SYSTEM_NOT_FOUND","系统不存在: "+task.getSystemId()));
        String namespace = sys.getSystemKey();
        // reqDefId 使用 detection_tasks.api_definition_id
        Long reqDefId = (task.getApiDefinitionId()!=null) ? task.getApiDefinitionId().longValue() : task.getApiId();
        // serviceList
        java.util.List<String> serviceList = nodes.stream().map(ApiTopologyNode::getName).toList();
        // 构造 payload
        java.util.Map<String,Object> payload = new java.util.LinkedHashMap<>();
        payload.put("reqDefId", reqDefId);
        payload.put("namespace", namespace);
        
        payload.put("serviceList", serviceList);
        payload.put("durationSec", 600);
        payload.put("autoTriggerRequest", true);
        payload.put("requestDelaySeconds", 30);
        payload.put("requestCount", 1);
        payload.put("requestTimeoutSeconds", 120);

        // 调用 svc-reqrsp-proxy（带认证）
        PatternAnalysisResult r = new PatternAnalysisResult();
        r.executionId = java.util.UUID.randomUUID().toString();
        r.status = "INITIALIZING"; // 初始状态改为 INITIALIZING
        r.taskId = taskId; r.reqDefId = reqDefId; r.namespace = namespace; r.serviceList = serviceList;
        try {
            // 获取带认证的请求头
            HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, headers);

            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                    "http://1.94.151.57:8105/api/request-patterns/analyze",
                    HttpMethod.POST,
                    req,
                    new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            r.proxyResponse = resp.getBody();
            // 根据 proxy 返回的 data.status/phase 等映射到细粒度状态；若未知则保持 INITIALIZING 或推断中间态
            r.status = mapProxyAnalyzeStatus(resp.getBody());
        } catch (HttpStatusCodeException ex) {
            // 如果是401未授权错误，清除token缓存并重试一次
            if (ex.getStatusCode().value() == 401) {
                try {
                    authenticationService.clearToken();
                    HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                    HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, headers);

                    ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                            "http://1.94.151.57:8105/api/request-patterns/analyze",
                            HttpMethod.POST,
                            req,
                            new ParameterizedTypeReference<Map<String,Object>>() {}
                    );
                    r.proxyResponse = resp.getBody();
                    r.status = "COMPLETED";
                } catch (Exception retryEx) {
                    r.proxyResponse = java.util.Map.of(
                            "status", ex.getStatusCode().value(),
                            "body", ex.getResponseBodyAsString()
                    );
                    r.status = "FAILED";
                    r.error = "Authentication failed after retry: " + retryEx.getMessage();
                }
            } else {
                r.proxyResponse = java.util.Map.of(
                        "status", ex.getStatusCode().value(),
                        "body", ex.getResponseBodyAsString()
                );
                r.status = "FAILED";
                r.error = ex.getMessage();
            }
        } catch (Exception ex) {
            r.status = "FAILED"; r.error = ex.getMessage();
        }
        patternStore.put(r.executionId, r);
        return r;
    }

    private String mapProxyAnalyzeStatus(Map<String,Object> body) {
        try {
            String status = null;
            if (body != null) {
                Object data = body.get("data");
                if (data instanceof Map) {
                    Object s = ((Map<?,?>) data).get("status");
                    if (s == null) s = ((Map<?,?>) data).get("phase");
                    if (s != null) status = String.valueOf(s);
                }
                if (status == null) {
                    Object s = body.get("status");
                    if (s != null && !(s instanceof Number)) status = String.valueOf(s);
                }
            }
            return mapToDetailedStatus(status);
        } catch (Exception ex) {
            return "INITIALIZING";
        }
    }

    private String mapToDetailedStatus(String proxyStatus) {
        if (proxyStatus == null) return "INITIALIZING";
        String s = proxyStatus.trim().toUpperCase(java.util.Locale.ROOT);
        switch (s) {
            case "INITIALIZING":
            case "APPLYING_RULES":
            case "ROLLING_UPDATE":
            case "TRIGGERING_REQUESTS":
            case "COLLECTING_DATA":
            case "ANALYZING_PATTERNS":
            case "COMPLETED":
            case "FAILED":
                return s;
            case "PROCESSING":
            case "PENDING":
            case "RUNNING":
                return "INITIALIZING";
            default:
                return "INITIALIZING";
        }
    }


    public PatternAnalysisResult getPatternAnalysis(String executionId) {
        PatternAnalysisResult r = patternStore.get(executionId);
        if (r==null) throw new BusinessException("PATTERN_EXECUTION_NOT_FOUND","分析执行不存在: "+executionId);
        return r;
    }

    public ExecutionResult startExecution(Long taskId) {
        // 1) 选用例（非基线）
        List<EnhancedSimplifiedTestCaseDTO> cases = testCaseGenerationService.generateEnhancedSimpleCases(taskId);
        List<EnhancedSimplifiedTestCaseDTO> nonBaseline = new ArrayList<>();
        for (EnhancedSimplifiedTestCaseDTO c : cases) {
            if (c.getFaults()!=null && !c.getFaults().isEmpty()) nonBaseline.add(c);
        }

        if (nonBaseline.isEmpty()) throw new BusinessException("NO_FAULT_CASE","未找到可注入故障的用例");
        EnhancedSimplifiedTestCaseDTO picked = nonBaseline.get(new Random().nextInt(nonBaseline.size()));

        // 2) 注入故障（将 faults 中每个 EnhancedFaultTargetDTO 的 faultDefinition 作为 body，使用 /api/faults/execute）
        Map<String,Object> lastFaultResp = null;
        java.util.List<Map<String,Object>> faultResponses = new ArrayList<>();
        java.util.List<Map<String,Object>> faultSpecs = new ArrayList<>();
        java.util.List<String> bladeNames = new ArrayList<>();
        for (EnhancedFaultTargetDTO ft : picked.getFaults()) {
            @SuppressWarnings("unchecked")
            Map<String,Object> full = (Map<String,Object>) (Map<?,?>) ft.getFaultDefinition();
            Object specObj = full.get("spec");
            Map<String,Object> payload;
            if (specObj instanceof Map) {
                payload = new java.util.LinkedHashMap<>();
                payload.put("spec", specObj);
                faultSpecs.add((Map<String,Object>) specObj);
            } else {
                payload = full; // 兼容：若无spec，直接传完整定义
                faultSpecs.add(full);
            }
            HttpHeaders fh = new HttpHeaders();
            fh.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String,Object>> fReq = new HttpEntity<>(payload, fh);
            ResponseEntity<Map<String,Object>> fResp = restTemplate.exchange(
                    faultBaseUrl + "/api/faults/execute",
                    HttpMethod.POST,
                    fReq,
                    new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            Map<String,Object> respBody = fResp.getBody();
            lastFaultResp = respBody;
            faultResponses.add(respBody);
            // 提取 bladeName: data.bladeName
            String bladeName = null;
            if (respBody != null) {
                Object data = respBody.get("data");
                if (data instanceof Map) {
                    Object bn = ((Map<?,?>) data).get("bladeName");
                    if (bn!=null) bladeName = String.valueOf(bn);
                }
            }
            if (bladeName!=null) bladeNames.add(bladeName);
        }

        // 3) 读取 DetectionTask，按 apiDefinitionId → http_req_def
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND","任务不存在: "+taskId));
        Long httpReqDefId = (task.getApiDefinitionId()!=null) ? task.getApiDefinitionId().longValue() : task.getApiId();
        HttpReqDef def = httpReqDefRepository.findById(httpReqDefId)
                .orElseThrow(() -> new BusinessException("HTTP_REQ_DEF_NOT_FOUND","未找到 http_req_def: id="+httpReqDefId));

        String url = def.getUrlTemplate();
        HttpMethod method = HttpMethod.valueOf(def.getMethod().name());

        // 简化：headers 不解析模板，直接透传
        Map<String, String> headers = new HashMap<>();
        try {
            if (def.getHeaders()!=null) {
                headers = new ObjectMapper().readValue(def.getHeaders(), new TypeReference<Map<String,String>>(){});
            }
        } catch (Exception ignore) {}

        HttpHeaders rh = new HttpHeaders();
        for (Map.Entry<String,String> e : headers.entrySet()) rh.add(e.getKey(), e.getValue());

        HttpEntity<?> reqEntity;
        if (def.getBodyMode()== HttpReqDef.BodyMode.JSON && def.getBodyTemplate()!=null) {
            rh.setContentType(MediaType.APPLICATION_JSON);
            reqEntity = new HttpEntity<>(def.getBodyTemplate(), rh);
        } else if (def.getBodyMode()== HttpReqDef.BodyMode.RAW && def.getRawBody()!=null) {
            MediaType ct = MediaType.parseMediaType(Optional.ofNullable(def.getContentType()).orElse("text/plain"));
            rh.setContentType(ct);
            reqEntity = new HttpEntity<>(def.getRawBody(), rh);
        } else {
            reqEntity = new HttpEntity<>(rh);
        }

        int statusCode;
        org.springframework.http.HttpHeaders respHeaders;
        String respBody;
        try {
            ResponseEntity<String> entity = restTemplate.exchange(java.net.URI.create(url), method, reqEntity, String.class);
            statusCode = entity.getStatusCode().value();
            respHeaders = entity.getHeaders();
            respBody = entity.getBody();
        } catch (HttpStatusCodeException ex) {
            // 按你的要求：目标服务的 4xx/5xx 视为正常结果的一部分
            statusCode = ex.getStatusCode().value();
            respHeaders = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new org.springframework.http.HttpHeaders();
            respBody = ex.getResponseBodyAsString();
        }

        // 4) 恢复/清理故障（逐个 bladeName 调用 DELETE /api/faults/{bladeName}）
        java.util.List<Map<String,Object>> recoveryResults = new ArrayList<>();
        for (String bn : bladeNames) {
            try {
                ResponseEntity<Map<String,Object>> delResp = restTemplate.exchange(
                        faultBaseUrl + "/api/faults/" + bn,
                        HttpMethod.DELETE,
                        HttpEntity.EMPTY,
                        new ParameterizedTypeReference<Map<String,Object>>() {}
                );
                recoveryResults.add(delResp.getBody());
            } catch (Exception ex) {
                Map<String,Object> err = new LinkedHashMap<>();
                err.put("bladeName", bn);
                err.put("error", ex.getMessage());
                recoveryResults.add(err);
            }
        }

        // 5) 组装返回（包含完整用例、故障创建返回、发送的spec、恢复结果、HTTP响应等）
        ExecutionResult r = new ExecutionResult();
        r.executionId = UUID.randomUUID().toString();
        r.status = "COMPLETED";
        r.testCase = picked;
        r.targetService = picked.getFaults().isEmpty()? null : picked.getFaults().get(0).getServiceName();
        r.targetServices = picked.getFaults().stream().map(EnhancedFaultTargetDTO::getServiceName).toList();
        r.requestMethod = def.getMethod().name();
        r.requestUrl = url;
        r.faultResponse = lastFaultResp;
        r.faultResponses = faultResponses;
        r.faultSpecs = faultSpecs;
        r.recoveryResults = recoveryResults;
        r.httpStatus = statusCode;
        Map<String,List<String>> hdrMap = new LinkedHashMap<>();
        if (respHeaders == null) respHeaders = new org.springframework.http.HttpHeaders();
        for (Map.Entry<String, List<String>> he : respHeaders.entrySet()) {
            hdrMap.put(he.getKey(), he.getValue());
        }
        r.responseHeaders = hdrMap;
        r.responseBodySnip = (respBody!=null ? respBody.substring(0, Math.min(1000, respBody.length())) : null);

        store.put(r.executionId, r);
        return r;
    }

    public ExecutionResult getExecution(String executionId) {
        ExecutionResult r = store.get(executionId);
        if (r==null) throw new BusinessException("EXECUTION_NOT_FOUND","执行不存在: "+executionId);
        return r;
    }

    // 从 analyze 响应中为某个服务提取规则；若无则返回空列表
    private java.util.List<java.util.Map<String,Object>> extractRulesFromAnalysis(Map<String,Object> analysisResponse, String serviceName) {
        java.util.List<java.util.Map<String,Object>> rules = new java.util.ArrayList<>();
        if (analysisResponse == null) return rules;
        Object data = analysisResponse.get("data");
        Object root = (data instanceof Map) ? data : analysisResponse;
        Object rps = (root instanceof Map) ? ((Map<?,?>) root).get("requestPatterns") : null;
        if (!(rps instanceof java.util.List)) return rules;
        for (Object rp : (java.util.List<?>) rps) {
            if (!(rp instanceof Map)) continue;
            Object sn = ((Map<?,?>) rp).get("serviceName");
            if (sn == null || !serviceName.equals(String.valueOf(sn))) continue;
            Object modes = ((Map<?,?>) rp).get("requestMode");
            if (!(modes instanceof java.util.List)) continue;
            java.util.Set<String> dedup = new java.util.HashSet<>();
            for (Object m : (java.util.List<?>) modes) {
                if (!(m instanceof Map)) continue;
                String method = String.valueOf(((Map<?,?>) m).get("method"));
                String url = String.valueOf(((Map<?,?>) m).get("url"));
                String path = normalizePath(url);
                String key = method + " " + path;
                if (dedup.add(key)) {
                    java.util.Map<String,Object> rule = new java.util.LinkedHashMap<>();
                    rule.put("path", path);
                    rule.put("method", method);
                    rules.add(rule);
                    // 同时加入路径前缀规则作为兜底（去除末段）以覆盖时间/数字片段变动
                    String prefix = path;
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash > 0) {
                        prefix = path.substring(0, lastSlash);
                        if (prefix.isEmpty()) prefix = "/";
                        String k2 = method + " " + prefix;
                        if (dedup.add(k2)) {
                            java.util.Map<String,Object> rule2 = new java.util.LinkedHashMap<>();
                            rule2.put("path", prefix);
                            rule2.put("method", method);
                            rules.add(rule2);
                        }
                    }
                }
            }
        }
        return rules;
    }

    // 归一化 URL 为 path；移除包含数字的时间/ID等片段，保证录制规则更泛化
    private String normalizePath(String url) {
        String path = url;
        try {
            java.net.URI u = java.net.URI.create(url);
            if (u.getPath() != null && !u.getPath().isEmpty()) path = u.getPath();
        } catch (Exception ignore) {}
        String[] segs = path.split("/");
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String s : segs) {
            if (s == null || s.isEmpty()) continue;
            // 去掉包含数字的片段（时间戳、日期、ID 等），并去掉 UUID 格式
            String ls = s.toLowerCase();
            boolean looksUuid = ls.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            boolean hasDigit = ls.matches(".*[0-9].*");
            if (looksUuid || hasDigit) continue;
            kept.add(s);
        }
        String out = "/" + String.join("/", kept);
        return out.equals("/") ? "/" : out;
    }

    // 串行批量执行：生成全部用例，逐个执行并等待恢复
    public BatchExecutionResult startBatchExecution(Long taskId) {
        String batchId = java.util.UUID.randomUUID().toString();
        BatchExecutionResult br = new BatchExecutionResult();
        br.batchId = batchId; br.status = "RUNNING"; br.taskId = taskId; br.currentIndex = 0;
        batchStore.put(batchId, br);

        new Thread(() -> {
            try {
                List<EnhancedSimplifiedTestCaseDTO> cases = testCaseGenerationService.generateEnhancedSimpleCases(taskId);
                br.totalCases = cases.size();
                for (int i = 0; i < cases.size(); i++) {
                    // 执行本用例（复用 startWithRecording 流程）
                    CombinedExecutionResult one = startWithRecording(taskId);
                    br.caseResults.add(one);
                    br.currentIndex = i + 1;

                    // 等待服务恢复：对本用例涉及服务，逐个等待就绪
                    long waited = 0L;
                    if (one != null && one.serviceList != null) {
                        for (String svc : one.serviceList) {
                            long begin = java.lang.System.currentTimeMillis();
                            boolean ok = kubernetesService.waitForServiceStable(one.namespace, svc, 120_000L);
                            waited += (java.lang.System.currentTimeMillis() - begin);
                            if (!ok) {
                                // 可选策略：记录但继续
                            }
                        }
                    }
                    br.caseWaitMillis.add(waited);
                }
                br.status = "COMPLETED";
            } catch (Exception ex) {
                br.status = "FAILED"; br.error = ex.getMessage();
            }
        }, "batch-exec-"+batchId).start();

        return br;
    }

    public BatchExecutionResult getBatchExecution(String batchId) {
        BatchExecutionResult r = batchStore.get(batchId);
        if (r == null) throw new BusinessException("BATCH_EXEC_NOT_FOUND","批量执行不存在: "+batchId);
        return r;
    }

    // 解析端口：优先配置映射，其次默认端口
    private int resolveAppPort(String namespace, String serviceName) {
        // 1) 优先使用 K8s 查询 Service 端口
        int k8sPort = -1;
        try { k8sPort = kubernetesService.getServicePort(namespace, serviceName); } catch (Exception ignore) {}
        if (k8sPort > 0) return k8sPort;
        // 2) 回退配置（如有）
        if (recordingProperties != null && recordingProperties.getServicePorts() != null) {
            Integer p = recordingProperties.getServicePorts().get(serviceName);
            if (p != null && p > 0) return p;
            if (recordingProperties.getDefaultPort() > 0) return recordingProperties.getDefaultPort();
        }
        return -1;
    }

    // 带录制的综合执行：分析 -> 启动录制 -> 故障执行 -> 拉取录制数据 -> 汇总返回
    public CombinedExecutionResult startWithRecording(Long taskId) {
        // 预先选择本次要执行的用例（仅对用例涉及的服务做录制）
        List<EnhancedSimplifiedTestCaseDTO> cases = testCaseGenerationService.generateEnhancedSimpleCases(taskId);
        List<EnhancedSimplifiedTestCaseDTO> nonBaseline = new ArrayList<>();
        for (EnhancedSimplifiedTestCaseDTO c : cases) {
            if (c.getFaults()!=null && !c.getFaults().isEmpty()) nonBaseline.add(c);
        }
        if (nonBaseline.isEmpty()) throw new BusinessException("NO_FAULT_CASE","未找到可注入故障的用例");
        EnhancedSimplifiedTestCaseDTO picked = nonBaseline.get(new Random().nextInt(nonBaseline.size()));
        java.util.List<String> svcList = picked.getFaults().stream()
                .map(EnhancedFaultTargetDTO::getServiceName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 通过 task->system 获取 namespace，使用 apiDefinitionId 作为 reqDefId
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND","任务不存在: "+taskId));
        System sys = systemRepository.findById(task.getSystemId())
                .orElseThrow(() -> new BusinessException("SYSTEM_NOT_FOUND","系统不存在: "+task.getSystemId()));
        String namespace = sys.getSystemKey();
        Long reqDefId = (task.getApiDefinitionId()!=null) ? task.getApiDefinitionId().longValue() : task.getApiId();

        CombinedExecutionResult cr = new CombinedExecutionResult();
        cr.executionId = java.util.UUID.randomUUID().toString();
        cr.status = "RUNNING";
        cr.taskId = taskId;
        cr.namespace = namespace;
        cr.serviceList = svcList;

        // 启动请求模式分析（仅针对用例涉及的服务）- 带认证
        Map<String,Object> analysisResponse = null;
        try {
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("reqDefId", reqDefId);
            payload.put("namespace", namespace);
            payload.put("serviceList", svcList);
            payload.put("durationSec", 600);
            payload.put("autoTriggerRequest", true);
            payload.put("requestDelaySeconds", 30);
            payload.put("requestCount", 1);
            payload.put("requestTimeoutSeconds", 120);

            // 获取带认证的请求头
            HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
            HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, headers);

            ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                    "http://1.94.151.57:8105/api/request-patterns/analyze",
                    HttpMethod.POST,
                    req,
                    new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            analysisResponse = resp.getBody();
        } catch (HttpStatusCodeException ex) {
            // 如果是401未授权错误，清除token缓存并重试一次
            if (ex.getStatusCode().value() == 401) {
                try {
                    authenticationService.clearToken();
                    Map<String,Object> payload = new LinkedHashMap<>();
                    payload.put("reqDefId", reqDefId);
                    payload.put("namespace", namespace);
                    payload.put("serviceList", svcList);
                    payload.put("durationSec", 600);
                    payload.put("autoTriggerRequest", true);
                    payload.put("requestDelaySeconds", 30);
                    payload.put("requestCount", 1);
                    payload.put("requestTimeoutSeconds", 120);

                    HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                    HttpEntity<Map<String,Object>> req = new HttpEntity<>(payload, headers);

                    ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                            "http://1.94.151.57:8105/api/request-patterns/analyze",
                            HttpMethod.POST,
                            req,
                            new ParameterizedTypeReference<Map<String,Object>>() {}
                    );
                    analysisResponse = resp.getBody();
                } catch (Exception retryEx) {
                    analysisResponse = new LinkedHashMap<>(Map.of(
                            "status", ex.getStatusCode().value(),
                            "body", ex.getResponseBodyAsString(),
                            "retryError", retryEx.getMessage()
                    ));
                }
            } else {
                analysisResponse = new LinkedHashMap<>(Map.of(
                        "status", ex.getStatusCode().value(),
                        "body", ex.getResponseBodyAsString()
                ));
            }
        } catch (Exception ex) {
            analysisResponse = new LinkedHashMap<>(Map.of("error", ex.getMessage()));
        }
        cr.analysisResponse = analysisResponse;

        java.util.List<Map<String,Object>> recordingStartResults = new ArrayList<>();
        java.util.Map<String,Object> recordedEntriesByService = new LinkedHashMap<>();

        // 若分析未返回具体规则，则使用 /api 前缀的通用规则兜底
        java.util.List<Map<String,Object>> defaultRules = java.util.List.of(
                java.util.Map.of("path","/api", "method","GET"),
                java.util.Map.of("path","/api", "method","POST"),
                java.util.Map.of("path","/api", "method","PUT"),
                java.util.Map.of("path","/api", "method","DELETE")
        );

        // 为每个服务启动录制
        for (String svcName : cr.serviceList) {
            Map<String,Object> recPayload = new LinkedHashMap<>();
            recPayload.put("namespace", cr.namespace);
            recPayload.put("serviceName", svcName);
            int port = resolveAppPort(cr.namespace, svcName);
            if (port > 0) {
                recPayload.put("appPort", port);
            }
            // 依据 analyze 返回提取具体规则；若无则使用兜底 defaultRules
            java.util.List<java.util.Map<String,Object>> rules = extractRulesFromAnalysis(analysisResponse, svcName);
            recPayload.put("rules", (rules != null && !rules.isEmpty()) ? rules : defaultRules);
            recPayload.put("durationSec", 600);
            try {
                // 获取带认证的请求头
                HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                HttpEntity<Map<String,Object>> req = new HttpEntity<>(recPayload, headers);

                ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                        "http://1.94.151.57:8105/api/recordings/start",
                        HttpMethod.POST,
                        req,
                        new ParameterizedTypeReference<Map<String,Object>>() {}
                );
                recordingStartResults.add(resp.getBody());
            } catch (HttpStatusCodeException ex) {
                // 如果是401未授权错误，清除token缓存并重试一次
                if (ex.getStatusCode().value() == 401) {
                    try {
                        authenticationService.clearToken();
                        HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                        HttpEntity<Map<String,Object>> req = new HttpEntity<>(recPayload, headers);

                        ResponseEntity<Map<String,Object>> resp = restTemplate.exchange(
                                "http://1.94.151.57:8105/api/recordings/start",
                                HttpMethod.POST,
                                req,
                                new ParameterizedTypeReference<Map<String,Object>>() {}
                        );
                        recordingStartResults.add(resp.getBody());
                    } catch (Exception retryEx) {
                        recordingStartResults.add(java.util.Map.of(
                                "service", svcName,
                                "status", ex.getStatusCode().value(),
                                "body", ex.getResponseBodyAsString(),
                                "retryError", retryEx.getMessage()
                        ));
                    }
                } else {
                    recordingStartResults.add(java.util.Map.of(
                            "service", svcName,
                            "status", ex.getStatusCode().value(),
                            "body", ex.getResponseBodyAsString()
                    ));
                }
            } catch (Exception ex) {
                recordingStartResults.add(java.util.Map.of("service", svcName, "error", ex.getMessage()));
            }
        }

        // 小等待，确保目标服务完成录制代理就绪
        try { Thread.sleep(2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        // 执行故障+HTTP
        ExecutionResult faultExec = startExecution(taskId);

        // 拉取录制数据（带认证）
        for (String svcName : cr.serviceList) {
            try {
                String url = String.format("http://1.94.151.57:8105/api/direct-tap/%s/%s/entries", cr.namespace, svcName);

                // 获取带认证的请求头
                HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                ResponseEntity<Map<String,Object>> tap = restTemplate.exchange(
                        url, HttpMethod.GET, requestEntity,
                        new ParameterizedTypeReference<Map<String,Object>>() {}
                );
                recordedEntriesByService.put(svcName, tap.getBody());
            } catch (HttpStatusCodeException ex) {
                // 如果是401未授权错误，清除token缓存并重试一次
                if (ex.getStatusCode().value() == 401) {
                    try {
                        authenticationService.clearToken();
                        String url = String.format("http://1.94.151.57:8105/api/direct-tap/%s/%s/entries", cr.namespace, svcName);

                        HttpHeaders headers = authenticationService.createAuthenticatedHeaders();
                        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                        ResponseEntity<Map<String,Object>> tap = restTemplate.exchange(
                                url, HttpMethod.GET, requestEntity,
                                new ParameterizedTypeReference<Map<String,Object>>() {}
                        );
                        recordedEntriesByService.put(svcName, tap.getBody());
                    } catch (Exception retryEx) {
                        recordedEntriesByService.put(svcName, new LinkedHashMap<>(java.util.Map.of(
                                "status", ex.getStatusCode().value(),
                                "body", ex.getResponseBodyAsString(),
                                "retryError", retryEx.getMessage()
                        )));
                    }
                } else {
                    recordedEntriesByService.put(svcName, new LinkedHashMap<>(java.util.Map.of(
                            "status", ex.getStatusCode().value(),
                            "body", ex.getResponseBodyAsString()
                    )));
                }
            } catch (Exception ex) {
                java.util.Map<String,Object> err = new LinkedHashMap<>();
                err.put("error", ex.getMessage());
                recordedEntriesByService.put(svcName, err);
            }
        }

        cr.recordingStartResults = recordingStartResults;
        cr.faultExecution = faultExec;
        cr.recordedEntriesByService = recordedEntriesByService;
        cr.status = "COMPLETED";
        combinedStore.put(cr.executionId, cr);
        return cr;
    }

    public CombinedExecutionResult getCombined(String executionId) {
        CombinedExecutionResult r = combinedStore.get(executionId);
        if (r==null) throw new BusinessException("COMBINED_EXECUTION_NOT_FOUND","综合执行不存在: "+executionId);
        return r;
    }

}