package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskexecutor.client.ProxyClient;

import com.chaosblade.svc.taskexecutor.dto.EnhancedFaultTargetDTO;
import com.chaosblade.svc.taskexecutor.dto.EnhancedSimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import com.chaosblade.svc.taskexecutor.entity.BaggageMap;
import com.chaosblade.svc.taskexecutor.entity.InterceptReplayResult;
import com.chaosblade.svc.taskexecutor.entity.TestResult;
import com.chaosblade.svc.taskexecutor.entity.HttpReqDef;
import com.chaosblade.svc.taskexecutor.entity.TaskExecutionLog;
import com.chaosblade.svc.taskexecutor.repository.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;



import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ExecutionOrchestrator {
    private final DetectionTaskRepository detectionTaskRepository;
    private final SystemRepository systemRepository;
    private final ProxyClient proxyClient;
    private final FaultConfigQueryService faultConfigQueryService;
    private final HttpReqDefRepository httpReqDefRepository;
    private final KubernetesService kubernetesService;
    private final TaskExecutionRepository taskExecutionRepository;
    private final BaggageMapRepository baggageMapRepository;
    private final InterceptReplayResultRepository interceptReplayResultRepository;
    private final TestResultRepository testResultRepository;
    private final TestCaseRepository testCaseRepository;
    private final TaskExecutionLogService taskExecutionLogService;
    private final SummaryService summaryService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutionOrchestrator.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final String faultBaseUrl = "http://1.94.151.57:8103";

    public ExecutionOrchestrator(DetectionTaskRepository detectionTaskRepository,
                                 SystemRepository systemRepository,
                                 ProxyClient proxyClient,
                                 FaultConfigQueryService faultConfigQueryService,
                                 HttpReqDefRepository httpReqDefRepository,
                                 KubernetesService kubernetesService,
                                 TaskExecutionRepository taskExecutionRepository,
                                 BaggageMapRepository baggageMapRepository,
                                 InterceptReplayResultRepository interceptReplayResultRepository,
                                 TestResultRepository testResultRepository,
                                 TestCaseRepository testCaseRepository,
                                 TaskExecutionLogService taskExecutionLogService,
                                 SummaryService summaryService) {
        this.detectionTaskRepository = detectionTaskRepository;
        this.systemRepository = systemRepository;
        this.proxyClient = proxyClient;
        this.faultConfigQueryService = faultConfigQueryService;
        this.httpReqDefRepository = httpReqDefRepository;
        this.kubernetesService = kubernetesService;
        this.taskExecutionRepository = taskExecutionRepository;
        this.baggageMapRepository = baggageMapRepository;
        this.interceptReplayResultRepository = interceptReplayResultRepository;
        this.testResultRepository = testResultRepository;
        this.testCaseRepository = testCaseRepository;
        this.taskExecutionLogService = taskExecutionLogService;
        this.summaryService = summaryService;
    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public TaskExecution createOrFailIfRunning(Long taskId, boolean force) {
        var running = taskExecutionRepository.findRunningByTaskId(taskId);
        if (!running.isEmpty() && !force) {
            throw new BusinessException("EXECUTION_ALREADY_RUNNING","已有执行在进行中，taskId="+taskId);
        }
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND","任务不存在: "+taskId));
        com.chaosblade.svc.taskexecutor.entity.System sys = systemRepository.findById(task.getSystemId())
                .orElseThrow(() -> new BusinessException("SYSTEM_NOT_FOUND","系统不存在: "+task.getSystemId()));
        TaskExecution te = new TaskExecution();
        te.setTaskId(taskId);
        te.setNamespace(sys.getSystemKey());
        Long reqDefId = (task.getApiDefinitionId()!=null) ? task.getApiDefinitionId().longValue() : task.getApiId();
        te.setReqDefId(reqDefId);
        // DetectionTask 实体未暴露 request_num 字段，这里默认 1；如需精确可扩展实体
        // 优先沿用任务配置的 request_num；若未配置则回退为 1
        Integer reqNum = null;
        try { reqNum = task.getRequestNum(); } catch (Exception ignore) {}
        te.setRequestNum(reqNum != null && reqNum > 0 ? reqNum : 1);
        te.setStatus("GENERATING_CASES");
        return taskExecutionRepository.save(te);
    }

    public void run(Long executionId, OrchestrateOptions options) {
        TaskExecution te = taskExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException("TASK_EXECUTION_NOT_FOUND","执行不存在: "+executionId));
        try {
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Orchestrator] Begin execution: status="+te.getStatus()+", namespace="+te.getNamespace()+", reqDefId="+te.getReqDefId());
            // 阶段1：触发分析（不提前生成用例，避免 Pod 名称过期）
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO, "[Stage1] Start analyzing patterns");
            List<String> svcListForAnalyze = faultConfigQueryService.getFaultConfigsByTaskId(te.getTaskId())
                    .stream().map(ServiceFaultConfig::getServiceName).distinct().toList();
            te.setStatus("ANALYZING_PATTERNS");
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage1] Status -> ANALYZING_PATTERNS; analyzeServices="+svcListForAnalyze.size());

            // 阶段2：触发分析 + 轮询（增加 executionId）
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage2] Analyze request: reqDefId="+te.getReqDefId()+", ns="+te.getNamespace()+", services="+svcListForAnalyze.size()+", durationSec=600, reqCount=1");
            Map<String,Object> analyzeResp = proxyClient.analyze(new LinkedHashMap<>(Map.of(
                    "reqDefId", te.getReqDefId(),
                    "namespace", te.getNamespace(),
                    "serviceList", svcListForAnalyze,
                    "durationSec", 600,
                    "autoTriggerRequest", true,
                    "requestDelaySeconds", 30,
                    "requestCount", 1,
                    "requestTimeoutSeconds", 120,
                    "excution_id", executionId
            )));
            String analyzeTaskId = asString(((Map<?,?>)analyzeResp.getOrDefault("data", analyzeResp)).get("taskId"));
            Long recordId = asLong(((Map<?,?>)analyzeResp.getOrDefault("data", analyzeResp)).get("recordId"));
            te.setAnalyzeTaskId(analyzeTaskId);
            te.setRecordId(recordId);
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage2] Analyze task created: taskId="+analyzeTaskId+", recordId="+recordId);

            // 轮询任务状态直到 COMPLETED 或失败/超时
            long deadline = System.currentTimeMillis() + 1000L * options.waitAnalyzeTimeoutSec;
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage2] Poll analyze task: taskId="+analyzeTaskId+", timeoutSec="+options.waitAnalyzeTimeoutSec);
            while (System.currentTimeMillis() < deadline) {
                Map<String,Object> st = proxyClient.getAnalyzeTask(analyzeTaskId);
                String s = asString(((Map<?,?>)st.getOrDefault("data", st)).get("status"));
                if ("COMPLETED".equalsIgnoreCase(s)) break;
                if ("FAILED".equalsIgnoreCase(s) || "ERROR".equalsIgnoreCase(s)) {
                    throw new BusinessException("ANALYZE_FAILED","Proxy 分析失败");
                }
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            // 超时检查
            Map<String,Object> stFinal = proxyClient.getAnalyzeTask(analyzeTaskId);
            String sFinal = asString(((Map<?,?>)stFinal.getOrDefault("data", stFinal)).get("status"));
            if (!"COMPLETED".equalsIgnoreCase(sFinal)) {
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.ERROR, "[Stage2] Analyze timeout");
                throw new BusinessException("ANALYZE_TIMEOUT","Proxy 分析超时");
            }
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO, "[Stage2] Analyze completed");
                // 阶段2完成后：并行等待所有相关服务恢复稳定（所有 pod 正常）
                try {
                    List<String> svcList = svcListForAnalyze;
                    ExecutorService wpool = Executors.newFixedThreadPool(Math.min(Math.max(1, svcList.size()), 8));
                    List<Future<Boolean>> wf = new ArrayList<>();
                    for (String svc : svcList) {
                        wf.add(wpool.submit(() -> {
                            boolean ok = kubernetesService.waitForServiceStable(te.getNamespace(), svc, 120_000L);
                            if (ok) log.info("[Post-Stage2] Service stable: {}", svc);
                            else log.warn("[Post-Stage2] Service not stable (timeout): {}", svc);
                            return ok;
                        }));
                    }
                    int okCount = 0; int total = wf.size();
                    wpool.shutdown();
                    for (Future<Boolean> f : wf) {

                        try { if (Boolean.TRUE.equals(f.get())) okCount++; }
                        catch (Exception e) { log.warn("[Post-Stage2] Wait task error: {}", e.getMessage()); }
                    }
                    taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                            "[Post-Stage2] Services stable summary: ok="+okCount+"/"+total);
                } catch (Exception ignore) { /* 保守等待，不影响后续流程 */ }

            // 阶段3：移除录制阶段（不再调用 startRecording）
            // 阶段3：在服务稳定之后再生成用例，避免 Pod 名称过期
            var cases = generateAllServiceCases(te.getTaskId());
            var caseIdMap = persistGeneratedCases(te.getTaskId(), executionId, cases);
            writeBaggageMap(te.getTaskId(), executionId, cases);
            {
                int totalCases = (cases==null?0:cases.size());
                int baseCnt = 0, singleCnt = 0, dualCnt = 0;
                if (cases != null) {
                    for (EnhancedSimplifiedTestCaseDTO c : cases) {
                        int cnt = (c.getFaults()==null)? 0 : c.getFaults().size();
                        if (cnt==0) baseCnt++; else if (cnt==1) singleCnt++; else dualCnt++;
                    }
                }
                int baggageCnt = baggageMapRepository.findByExecutionId(executionId).size();
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                        "[Stage3] Cases generated: total="+totalCases+", baseline="+baseCnt+", single="+singleCnt+", dual="+dualCnt+", baggageMap.size="+baggageCnt);
            }

            // 阶段4：按“每个唯一服务注入一次”并行执行注入 + 回放校验
            te.setStatus("INJECTING_AND_REPLAYING");
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO, "[Stage4] Start injecting & replaying");
            // 收集所有用例中涉及到的唯一服务
            Map<String, EnhancedFaultTargetDTO> serviceTargets = new LinkedHashMap<>();
            for (EnhancedSimplifiedTestCaseDTO c : cases) {
                if (c.getFaults()==null) continue;
                for (EnhancedFaultTargetDTO ft : c.getFaults()) {
                    serviceTargets.putIfAbsent(ft.getServiceName(), ft);
                }
            }
            List<String> services = new ArrayList<>(serviceTargets.keySet());
            log.info("[Stage4] Unique services to inject: {}", services.size());

            ExecutorService pool = Executors.newFixedThreadPool(Math.min(Math.max(1, services.size()), 8));
            List<Future<Map.Entry<String,String>>> futures = new ArrayList<>(); // 返回 <service, bladeName>
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage4] Services to inject: count="+services.size()+", names="+services);

            for (String svc : services) {
                EnhancedFaultTargetDTO ft = serviceTargets.get(svc);
                futures.add(pool.submit(() -> {
                    try {
                        // 1) 注入故障（仅一次/服务）
                        @SuppressWarnings("unchecked")
                        Map<String,Object> full = (Map<String,Object>) (Map<?,?>) ft.getFaultDefinition();
                        Object specObj = full.get("spec");
                        Map<String,Object> payload = (specObj instanceof Map)
                                ? new LinkedHashMap<>(Map.of("spec", specObj))
                                : full;
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
                        String bladeName = null;
                        if (respBody != null) {
                            Object data = respBody.get("data");
                            if (data instanceof Map) {
                                Object bn = ((Map<?,?>) data).get("bladeName");
                                if (bn!=null) bladeName = String.valueOf(bn);
                            }
                        }
                        log.info("[Stage4] Injected fault for service={}, bladeName={}", svc, bladeName);
                        taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                                "[Stage4] Injected fault: service="+svc+", bladeName="+bladeName);

                        // 等待故障真正生效：轮询 phase=Running，超时30秒，间隔500ms
                        if (bladeName != null && !bladeName.isBlank()) {
                            long waitDeadline = System.currentTimeMillis() + 30_000L;
                            while (System.currentTimeMillis() < waitDeadline) {
                                try {
                                    ResponseEntity<Map<String,Object>> stResp = restTemplate.exchange(
                                            faultBaseUrl + "/api/faults/"+bladeName+"/status",
                                            HttpMethod.GET,
                                            HttpEntity.EMPTY,
                                            new ParameterizedTypeReference<Map<String,Object>>() {}
                                    );
                                    Map<String,Object> stBody = stResp.getBody();
                                    Object d = (stBody!=null)? stBody.get("data") : null;
                                    String phase = null;
                                    if (d instanceof Map) {
                                        Object ph = ((Map<?,?>) d).get("phase");
                                        if (ph!=null) phase = String.valueOf(ph);
                                    }
                                    if ("Running".equalsIgnoreCase(phase)) {
                                        log.info("[Stage4] Fault is running for service={}, bladeName={}", svc, bladeName);
                                        break;
                                    }
                                } catch (Exception ignore) {}
                                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                            }
                        }

                        // 2) 回放校验 → 收集响应模板供拦截器使用（不再依赖 baggage 拦截）
                        Map<String,String> headers = new LinkedHashMap<>();
                        Map<String,Object> replay = proxyClient.replay(executionId, te.getNamespace(), svc, headers);
                        Map<String,Object> data;
                        Object dataObj = replay != null ? replay.getOrDefault("data", replay) : null;
                        if (dataObj instanceof java.util.Map) {
                            data = (Map<String,Object>) dataObj;
                        } else if (dataObj instanceof java.util.List) {
                            java.util.List<?> lst = (java.util.List<?>) dataObj;
                            if (!lst.isEmpty() && lst.get(0) instanceof java.util.Map) {
                                data = (Map<String,Object>) lst.get(0);
                            } else {
                                data = new LinkedHashMap<>();
                            }
                        } else {
                            data = new LinkedHashMap<>();
                        }
                        InterceptReplayResult rr = new InterceptReplayResult();
                        rr.setTaskId(te.getTaskId());
                        rr.setExecutionId(executionId);
                        rr.setServiceName(svc);
                        rr.setFaultType("remove");
                        rr.setRequestUrl(asString(data.get("url")));
                        rr.setRequestMethod(asString(data.get("method")));
                        rr.setRequestHeaders(toJson(headers));
                        rr.setRequestBody(null);
                        rr.setResponseStatus(asInt(data.get("statusCode")));
                        rr.setResponseHeaders(toJson(data.get("responseHeaders")));
                        rr.setResponseBody(asString(data.get("responseBody")));
                        interceptReplayResultRepository.save(rr);
                        log.info("[Stage4] Replay verified for service={}, status={}", svc, rr.getResponseStatus());
                        taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                                "[Stage4] Replay verified: service="+svc+", status="+rr.getResponseStatus());

                        return Map.entry(svc, bladeName);
                    } catch (Exception e) {
                        log.error("[Stage4] Fault inject/replay failed for service {}: {}", svc, e.getMessage());
                        taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.ERROR,
                                "[Stage4] Inject/replay error: service="+svc+", err="+e.getMessage());
                        throw e;
                    }
                }));
            }
            pool.shutdown();
            Map<String,String> bladeNames = new LinkedHashMap<>();
            for (Future<Map.Entry<String,String>> f : futures) {
                try { Map.Entry<String,String> en = f.get(); if (en!=null) bladeNames.put(en.getKey(), en.getValue()); }
                catch (Exception ex) { log.error("[Stage4] Future join error: {}", ex.getMessage()); }
            }
            // 恢复全部故障并等待服务恢复
            for (Map.Entry<String,String> en : bladeNames.entrySet()) {
                String bn = en.getValue(); String svc = en.getKey();
                try {
                    if (bn!=null) {
                        restTemplate.exchange(faultBaseUrl+"/api/faults/"+bn, HttpMethod.DELETE, HttpEntity.EMPTY,
                                new ParameterizedTypeReference<Map<String,Object>>() {});
                        log.info("[Stage4] Recovered fault for service={}, bladeName={}", svc, bn);
                    }
                } catch (Exception ex) { log.warn("[Stage4] Recover fault failed for service {}: {}", svc, ex.getMessage()); }
            }
            // 并行等待所有服务恢复稳定，避免串行阻塞过久
            {
                ExecutorService wpool = Executors.newFixedThreadPool(Math.min(Math.max(1, services.size()), 8));
                List<Future<Boolean>> wf = new ArrayList<>();
                for (String svc : services) {
                    wf.add(wpool.submit(() -> {
                        boolean ok = kubernetesService.waitForServiceStable(te.getNamespace(), svc, 120_000L);
                        if (ok) log.info("[Stage4] Service stable: {}", svc);
                        else log.warn("[Stage4] Service not stable (timeout): {}", svc);
                        return ok;
                    }));
                }
                wpool.shutdown();
                for (Future<Boolean> f : wf) { try { f.get(); } catch (Exception e) { log.warn("[Stage4] Wait task error: {}", e.getMessage()); } }
            }

            // 阶段5：下发拦截器并就绪校验（recordId 改为 executionId 字符串）
            String recordIdStr = String.valueOf(executionId);
            List<Map<String,Object>> items = buildInterceptorItems(executionId);
            proxyClient.interceptorsUpsert(te.getNamespace(), recordIdStr, options.ttlSecForInterceptors, items);
            te.setInterceptRecordId(recordIdStr);
            te.setStatus("RULES_READY");
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage5] Build interceptors: count="+items.size());

            taskExecutionRepository.save(te);
            log.info("[Stage5] Interceptors upserted, recordId={}, items={}.", recordIdStr, items);

            long readyDeadline = System.currentTimeMillis() + 1000L * options.waitInterceptorReadySec;
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage5] Waiting interceptors ready: timeoutSec="+options.waitInterceptorReadySec);

            while (System.currentTimeMillis() < readyDeadline) {
                Map<String,Object> st = proxyClient.getInterceptorStatus(recordIdStr);
                boolean exists = Boolean.TRUE.equals(((Map<?,?>)st.getOrDefault("data", st)).get("exists"));
                if (exists) break;
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            // 不存在则失败
            {
                Map<String,Object> st = proxyClient.getInterceptorStatus(recordIdStr);
                boolean exists = Boolean.TRUE.equals(((Map<?,?>)st.getOrDefault("data", st)).get("exists"));
                if (!exists) {
                    taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.ERROR,
                            "[Stage5] Interceptors not ready");
                    throw new BusinessException("INTERCEPTOR_NOT_READY","拦截器未就绪");
                }
                log.info("[Stage5] Interceptors are ready.");
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                        "[Stage5] Interceptors ready");
            }
            // 阶段5就绪后：并行等待所有目标服务 Pod 再次稳定（拦截器下发可能触发滚动）
            {
                ExecutorService wpool = Executors.newFixedThreadPool(Math.min(Math.max(1, services.size()), 8));
                List<Future<Boolean>> wf = new ArrayList<>();
                for (String svc : services) {
                    wf.add(wpool.submit(() -> {
                        boolean ok = kubernetesService.waitForServiceStable(te.getNamespace(), svc, 120_000L);
                        if (ok) log.info("[Stage5] Service stable after fixtures upsert: {}", svc);
                        else log.warn("[Stage5] Service not stable after fixtures upsert (timeout): {}", svc);
                        return ok;
                    }));
                }
                int okCount = 0; int total = wf.size();
                wpool.shutdown();
                for (Future<Boolean> f : wf) { try { if (Boolean.TRUE.equals(f.get())) okCount++; } catch (Exception e) { log.warn("[Stage5] Wait task error: {}", e.getMessage()); } }
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                        "[Stage5] Services stable summary after interceptors: ok="+okCount+"/"+total);
            }


            // 阶段6：执行所有用例（baseline/单/双），每个用例内请求并发，按批次执行
            te.setStatus("LOAD_TEST_BASELINE");
            taskExecutionRepository.save(te);

            DetectionTask task = detectionTaskRepository.findById(te.getTaskId())
                    .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND","任务不存在: "+te.getTaskId()));
            Long httpReqDefId = (task.getApiDefinitionId()!=null) ? task.getApiDefinitionId().longValue() : task.getApiId();
            HttpReqDef def = httpReqDefRepository.findById(httpReqDefId)
                    .orElseThrow(() -> new BusinessException("HTTP_REQ_DEF_NOT_FOUND","未找到 http_req_def: id="+httpReqDefId));

            int n = Optional.ofNullable(te.getRequestNum()).orElse(1);
            int batchSize = 18; // 默认批次大小，可后续做配置
            int perCaseConcurrency = Math.min(8, Math.max(1, n));
            log.info("[Stage6] Begin executing all cases. totalCases={}, batchSize={}, perCaseConcurrency={}, requestsPerCase={}",
                    cases.size(), batchSize, perCaseConcurrency, n);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage6] Begin: totalCases="+cases.size()+", batchSize="+batchSize+
                            ", perCaseConcurrency="+perCaseConcurrency+", requestsPerCase="+n);

            List<List<EnhancedSimplifiedTestCaseDTO>> batches = new ArrayList<>();
            for (int i=0;i<cases.size();i+=batchSize) {
                batches.add(cases.subList(i, Math.min(i+batchSize, cases.size())));
            }
            int batchNo = 0;
            for (List<EnhancedSimplifiedTestCaseDTO> batch : batches) {
                batchNo++;
                log.info("[Stage6] Executing batch {}/{} ({} cases)", batchNo, batches.size(), batch.size());
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                        "[Stage6] Batch start: "+batchNo+"/"+batches.size()+", cases="+batch.size());

                for (EnhancedSimplifiedTestCaseDTO c : batch) {
                    String caseId = buildCaseId(c);
                    String baggageHeader = buildBaggageHeader(c, executionId);
                    log.info("[Stage6] Case start: id={}, baggage={}", caseId, baggageHeader);
                    TestResult tr = executeCase(def, baggageHeader, n, perCaseConcurrency);
                    tr.setExecutionId(executionId);
                    Long mappedId = (caseIdMap != null) ? caseIdMap.get(caseId) : null;
                    tr.setTestCaseId(mappedId != null ? mappedId : 0L);
                    tr.setRequestUrl(def.getUrlTemplate());
                    tr.setRequestMethod(def.getMethod().name());
                    // response_code / response_body 不做处理
                    testResultRepository.save(tr);
                    log.info("[Stage6] Case done: id={}, p50={}, p95={}, p99={}, errRate={}",
                            caseId, tr.getP50(), tr.getP95(), tr.getP99(), tr.getErrRate());
                }
                taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                        "[Stage6] Batch done: "+batchNo+"/"+batches.size());

            }

            te.setStatus("DONE");
            te.setFinishedAt(LocalDateTime.now());
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.INFO,
                    "[Stage6] Completed: status="+te.getStatus()+", finishedAt="+te.getFinishedAt());
            // 异步触发大模型总结
            // try { summaryService.summarizeAsync(executionId); } catch (Exception ignore) {
            //     log.warn("[Summary] Failed to trigger summarize: {}", ignore.getMessage());
            // }

        } catch (BusinessException be) {
            te.setStatus("FAILED");
            te.setErrorCode(be.getErrorCode());
            te.setErrorMsg(be.getMessage());
            te.setFinishedAt(LocalDateTime.now());
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.ERROR,
                    "[Run] Failed: code="+te.getErrorCode()+", msg="+te.getErrorMsg());

            throw be;
        } catch (Exception ex) {
            te.setStatus("FAILED");
            te.setErrorCode("UNCAUGHT");
            te.setErrorMsg(ex.getMessage());
            te.setFinishedAt(LocalDateTime.now());
            taskExecutionRepository.save(te);
            taskExecutionLogService.append(executionId, TaskExecutionLog.LogLevel.ERROR,
                    "[Run] Uncaught error: "+ex.getMessage());

            throw new BusinessException("UNCAUGHT", ex.getMessage());
        }
    }

    // 生成全服务用例：baseline + 单故障(每服务一次) + 双故障(全组合)
    private List<EnhancedSimplifiedTestCaseDTO> generateAllServiceCases(Long taskId) {
        List<ServiceFaultConfig> svcList = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        Map<String, ServiceFaultConfig> svcMap = new LinkedHashMap<>();
        for (ServiceFaultConfig sfc : svcList) svcMap.put(sfc.getServiceName(), sfc);
        java.util.function.Function<ServiceFaultConfig, EnhancedFaultTargetDTO> buildOne = (sfc) -> {
            String ns = sfc.getNamespace();
            String svc = sfc.getServiceName();
            java.util.List<String> containerValues = (sfc.getContainerNames()!=null)? sfc.getContainerNames() : java.util.List.of();
            java.util.List<String> podValues = (sfc.getNames()!=null)? sfc.getNames() : java.util.List.of();
            java.util.Map<String, Object> def = new java.util.LinkedHashMap<>();
            java.util.Map<String, Object> exp = new java.util.LinkedHashMap<>();
            exp.put("scope", "container"); exp.put("target", "container"); exp.put("action", "remove");
            java.util.List<java.util.Map<String,Object>> matchers = new java.util.ArrayList<>();
            if (!podValues.isEmpty()) matchers.add(java.util.Map.of("name","names","value", podValues));
            matchers.add(java.util.Map.of("name","namespace","value", java.util.List.of(ns)));
            if (!containerValues.isEmpty()) matchers.add(java.util.Map.of("name","container-names","value", containerValues));
            matchers.add(java.util.Map.of("name","force","value", java.util.List.of("true")));
            java.util.Map<String, Object> expObj = new java.util.LinkedHashMap<>();
            expObj.putAll(exp); expObj.put("matchers", matchers);
            java.util.Map<String, Object> spec = new java.util.LinkedHashMap<>();
            spec.put("experiments", java.util.List.of(expObj));
            def.put("spec", spec);
            return new EnhancedFaultTargetDTO(ns, svc, def);
        };
        List<String> services = new ArrayList<>(svcMap.keySet());
        List<EnhancedSimplifiedTestCaseDTO> out = new ArrayList<>();
        out.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of())); // baseline
        for (String s : services) out.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of(buildOne.apply(svcMap.get(s)))));
        for (int i=0;i<services.size();i++) {
            for (int j=i+1;j<services.size();j++) {
                out.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of(
                        buildOne.apply(svcMap.get(services.get(i))),
                        buildOne.apply(svcMap.get(services.get(j)))
                )));
            }
        }
        return out;
    }


    private String buildCaseId(EnhancedSimplifiedTestCaseDTO c) {
        if (c.getFaults()==null || c.getFaults().isEmpty()) return "baseline";
        List<String> svcs = c.getFaults().stream().map(EnhancedFaultTargetDTO::getServiceName).sorted().toList();
        return String.join("+", svcs);
    }

    private String buildBaggageHeader(EnhancedSimplifiedTestCaseDTO c, Long executionId) {
        if (c.getFaults()==null || c.getFaults().isEmpty()) return null;
        // 从 baggage_map 获取每个服务的 token 值并拼接
        List<String> services = c.getFaults().stream().map(EnhancedFaultTargetDTO::getServiceName).distinct().toList();
        List<BaggageMap> maps = baggageMapRepository.findByExecutionId(executionId);
        Map<String,String> svcToken = new LinkedHashMap<>();
        for (BaggageMap bm : maps) svcToken.put(bm.getServiceName(), bm.getValue());
        List<String> toks = new ArrayList<>();
        for (String svc : services) {
            String v = svcToken.get(svc);
            if (v == null || v.isBlank()) continue;
            // value 可能为逗号分隔的多个 token；这里全部加入
            for (String t : v.split(",")) if (t!=null && !t.isBlank()) toks.add(t.trim());
        }
        return toks.isEmpty()? null : String.join(",", toks);
    }

    private TestResult executeCase(HttpReqDef def, String baggageHeader, int requestCount, int concurrency) {
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        final int[] errors = new int[]{0};
        String requestUrl = def.getUrlTemplate();
        String requestMethod = def.getMethod().name();

        final Map<String, String> headerMap;
        Map<String, String> tmp = new LinkedHashMap<>();
        try {
            if (def.getHeaders()!=null) tmp = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(def.getHeaders(), new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){});
        } catch (Exception ignore) {}
        headerMap = java.util.Collections.unmodifiableMap(tmp);

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        List<Future<Void>> futures = new ArrayList<>();
        for (int i=0; i<requestCount; i++) {
            futures.add(pool.submit(() -> {
                HttpHeaders rh = new HttpHeaders();
                for (Map.Entry<String,String> e : headerMap.entrySet()) rh.add(e.getKey(), e.getValue());
                // baggage 头替换逻辑：若存在则替换，否则按需添加
                if (baggageHeader != null && !baggageHeader.isBlank()) {
                    if (rh.containsKey("baggage")) rh.set("baggage", baggageHeader);
                    else rh.add("baggage", baggageHeader);
                } else {
                    // baseline：确保不携带旧 baggage
                    rh.remove("baggage");
                }
                Object body = null;
                HttpMethod method = HttpMethod.valueOf(def.getMethod().name());
                HttpEntity<?> reqEntity;
                if (def.getBodyMode()== HttpReqDef.BodyMode.JSON && def.getBodyTemplate()!=null) {
                    rh.setContentType(MediaType.APPLICATION_JSON);
                    body = def.getBodyTemplate();
                    reqEntity = new HttpEntity<>(body, rh);
                } else if (def.getBodyMode()== HttpReqDef.BodyMode.RAW && def.getRawBody()!=null) {
                    MediaType ct = MediaType.parseMediaType(Optional.ofNullable(def.getContentType()).orElse("text/plain"));
                    rh.setContentType(ct);
                    body = def.getRawBody();
                    reqEntity = new HttpEntity<>(body, rh);
                } else {
                    reqEntity = new HttpEntity<>(rh);
                }
                long begin = System.nanoTime();
                int statusCode = Integer.MIN_VALUE;
                try {
                    ResponseEntity<String> entity = restTemplate.exchange(java.net.URI.create(requestUrl), method, reqEntity, String.class);
                    statusCode = entity.getStatusCode().value();
                } catch (HttpStatusCodeException ex) {
                    statusCode = ex.getStatusCode().value();
                } catch (Exception ex) {
                    //  e9 9d 9e HTTP  e7 8a b6 e6 80 81 e7 b1 bb e5 bc 82 e5 b8 b8 e4 b9 9f e8 ae a1 e4 b8 ba e9 94 99 e8 af af
                    statusCode = -1;
                } finally {
                    long durMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
                    durations.add(durMs);
                    if (statusCode < 200 || statusCode >= 400) {
                        synchronized (errors) { errors[0]++; }
                    }
                }
                return null;
            }));
        }
        pool.shutdown();
        for (Future<Void> f : futures) {
            try { f.get(); } catch (Exception ex) { log.warn("[Stage6] Single request failed: {}", ex.getMessage()); }
        }
        Collections.sort(durations);
        long p50 = percentile(durations, 0.50);
        long p95 = percentile(durations, 0.95);
        long p99 = percentile(durations, 0.99);
        java.math.BigDecimal errRate = new java.math.BigDecimal(errors[0] * 100.0 / Math.max(1, requestCount)).setScale(2, java.math.RoundingMode.HALF_UP);

        TestResult tr = new TestResult();
        tr.setRequestUrl(requestUrl);
        tr.setRequestMethod(requestMethod);
        tr.setP50((int)p50); tr.setP95((int)p95); tr.setP99((int)p99);
        tr.setErrRate(errRate);
        return tr;
    }


    private Long computeCaseIdAsLong(String caseIdStr) {
        // 将 'svcA+svcB' 等字符串转为稳定的 long（哈希）
        // 避免与 baseline(0) 冲突
        return (long) Math.abs(caseIdStr.hashCode());
    }

    private long percentile(List<Long> sortedDurations, double p) {
        if (sortedDurations==null || sortedDurations.isEmpty()) return 0L;
        int idx = (int)Math.ceil(p * sortedDurations.size()) - 1;
        if (idx < 0) idx = 0; if (idx >= sortedDurations.size()) idx = sortedDurations.size()-1;
        return sortedDurations.get(idx);
    }

    @Transactional
    private void writeBaggageMap(Long taskId, Long executionId, List<EnhancedSimplifiedTestCaseDTO> cases) {
        // 基于该任务的故障配置，按服务生成基于故障类型的 token 列表
        // 规则：每个服务的 token = join(",", ["chaos."+svc+"-"+type for type in unique types])
        // 若该服务未配置 type，则不为其生成特定后缀（可存空字符串）
        List<String> services = extractAllServices(cases);
        // 查询服务→故障类型集合
        Map<String, java.util.Set<String>> svcTypes = new LinkedHashMap<>();
        List<ServiceFaultConfig> cfgs = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        for (ServiceFaultConfig sc : cfgs) {
            java.util.Set<String> types = svcTypes.computeIfAbsent(sc.getServiceName(), k -> new LinkedHashSet<>());
            if (sc.getFaultConfig()!=null) {
                for (ServiceFaultConfig.FaultEntry fe : sc.getFaultConfig()) {
                    if (fe.getType()!=null && !fe.getType().isBlank()) types.add(fe.getType());
                }
            }
        }
        for (String svc : services) {
            java.util.Set<String> types = svcTypes.getOrDefault(svc, java.util.Collections.emptySet());
            List<String> tokens = new ArrayList<>();
            for (String t : types) tokens.add("chaos."+svc+"-"+t);
            String value = String.join(",", tokens);
            baggageMapRepository.upsert(executionId, svc, value);
        }
    }

    private List<String> extractAllServices(List<EnhancedSimplifiedTestCaseDTO> cases) {
        return cases.stream()
                .flatMap(c -> c.getFaults().stream())
                .map(EnhancedFaultTargetDTO::getServiceName)
                .distinct()
                .collect(Collectors.toList());
    }

    private String asString(Object o) { return (o==null)? null : String.valueOf(o); }
    private Long asLong(Object o) { try { return (o==null)? null : Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; } }
    private Integer asInt(Object o) { try { return (o==null)? null : Integer.valueOf(String.valueOf(o)); } catch (Exception e) { return null; } }

    private String toJson(Object o) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception ex) { return String.valueOf(o); }
    }

    private List<Map<String,Object>> buildInterceptorItems(Long executionId) {
        List<BaggageMap> bms = baggageMapRepository.findByExecutionId(executionId).stream().toList();
        List<InterceptReplayResult> rrs = interceptReplayResultRepository.findByExecutionId(executionId).stream().toList();
        Map<String, String> svcBaggage = new LinkedHashMap<>();
        for (BaggageMap bm : bms) svcBaggage.put(bm.getServiceName(), bm.getValue());
        Map<String, Map<String,Object>> svcTemplate = new LinkedHashMap<>();
        for (InterceptReplayResult r : rrs) {
            svcTemplate.put(r.getServiceName(), new LinkedHashMap<>(Map.of(
                    "path", safePath(r.getRequestUrl()),
                    "method", r.getRequestMethod(),
                    "status", r.getResponseStatus(),
                    "headers", tryParseJson(r.getResponseHeaders()),
                    "body", r.getResponseBody()
            )));
        }
        List<Map<String,Object>> items = new ArrayList<>();
        for (Map.Entry<String, Map<String,Object>> e : svcTemplate.entrySet()) {
            String svc = e.getKey();
            String tokens = svcBaggage.getOrDefault(svc, "");
            List<String> baggageTokens = new ArrayList<>();
            for (String tok : tokens.split(",")) {
                if (tok == null || tok.isBlank()) continue;
                baggageTokens.add(tok);
            }
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("serviceName", svc);
            item.put("method", e.getValue().get("method"));
            item.put("path", e.getValue().get("path"));
            item.put("baggageTokens", baggageTokens);
            item.put("respStatus", e.getValue().get("status"));
            item.put("respHeaders", normalizeHeaders(e.getValue().get("headers")));
            item.put("respBody", e.getValue().get("body"));
            items.add(item);
        }
        return items;
    }

    @Transactional
    protected java.util.Map<String, Long> persistGeneratedCases(Long taskId, Long executionId, List<EnhancedSimplifiedTestCaseDTO> cases) {
        java.util.Map<String, Long> mapping = new java.util.LinkedHashMap<>();
        if (cases==null || cases.isEmpty()) return mapping;
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        for (EnhancedSimplifiedTestCaseDTO c : cases) {
            int cnt = (c.getFaults()==null)? 0 : c.getFaults().size();
            com.chaosblade.svc.taskexecutor.entity.TestCase tc = new com.chaosblade.svc.taskexecutor.entity.TestCase();
            tc.setTaskId(taskId);
            tc.setExecutionId(executionId);
            switch (cnt) {
                case 0 -> tc.setCaseType(com.chaosblade.svc.taskexecutor.entity.TestCase.CaseType.BASELINE);
                case 1 -> tc.setCaseType(com.chaosblade.svc.taskexecutor.entity.TestCase.CaseType.SINGLE);
                default -> tc.setCaseType(com.chaosblade.svc.taskexecutor.entity.TestCase.CaseType.DUAL);
            }
            tc.setTargetCount(cnt);
            try {
                String json = om.writeValueAsString(c.getFaults()==null? java.util.List.of() : c.getFaults());
                tc.setFaultsJson(json);
            } catch (Exception ex) {
                throw new BusinessException("JSON_WRITE_ERROR","faults_json 序列化失败", ex);
            }
            testCaseRepository.save(tc);
            String caseId = buildCaseId(c);
            mapping.put(caseId, tc.getId());
        }
        return mapping;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> normalizeHeaders(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) return (Map<String,Object>) obj;
        if (obj instanceof String s) {
            Object parsed = tryParseJson(s);
            if (parsed instanceof Map) return (Map<String,Object>) parsed;
        }
        return null;
    }
    private Object tryParseJson(String str) {
        if (str == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(str, Object.class); }
        catch (Exception ex) { return null; }
    }

    private String safePath(String url) {
        if (url == null) return "/";
        try { java.net.URI u = java.net.URI.create(url); return (u.getPath()!=null && !u.getPath().isEmpty()) ? u.getPath() : "/"; }
        catch (Exception ex) { return url; }
    }

    public static class OrchestrateOptions {
        public int ttlSecForInterceptors;
        public int waitAnalyzeTimeoutSec;
        public int waitInterceptorReadySec;
    }

    private List<Map<String,Object>> defaultRules() {
        return List.of(
                Map.of("path", "/api", "method", "GET"),
                Map.of("path", "/api", "method", "POST"),
                Map.of("path", "/api", "method", "PUT"),
                Map.of("path", "/api", "method", "DELETE")
        );
    }

    public java.util.Optional<TaskExecution> getExecution(Long executionId) {
        return taskExecutionRepository.findById(executionId);
    }
}
