package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.domain.experiment.ExperimentConfig;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.ComprehensiveAnalysisResult.ChaosScenario;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.ComprehensiveAnalysisResult.ServiceComprehensiveAnalysis;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.PipelineExecutionStatus.ExecutionState;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.PipelineExecutionStatus.PhaseStatus;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.service.ServiceMapService;
import com.chaosblade.svc.k8sgraph.service.experiment.ExperimentConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险分析Pipeline编排服务
 *
 * 编排六阶段风险分析流程：
 * Phase 1: 规则扫描 -> ServiceRiskProfile
 * Phase 2: 拓扑LLM分析 -> TopologyRiskResult
 * Phase 3: RiskRank计算 -> RiskRankResult
 * Phase 4: Trace深度分析 -> TraceAnalysisResult
 * Phase 5: 综合分析与故障场景生成 -> ComprehensiveAnalysisResult (包含ChaosScenario)
 * Phase 6: 实验配置生成 -> 为每个ChaosScenario生成ExperimentConfig
 *
 * 最终输出: PipelineResult
 */
@Service
public class RiskPipelineOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RiskPipelineOrchestrator.class);

    /** Phase 4分析的Top N服务数量（可通过配置覆盖） */
    @org.springframework.beans.factory.annotation.Value("${risk.pipeline.top-n-services:3}")
    private int topNServicesForTrace;

    @Autowired
    private RiskRuleEngineService riskRuleEngineService;

    @Autowired
    private TopologyRiskLLMService topologyRiskLLMService;

    @Autowired
    private RiskRankAlgorithm riskRankAlgorithm;

    @Autowired
    private ServiceMapService serviceMapService;

    @Autowired
    private TraceAnalysisService traceAnalysisService;

    @Autowired
    private ComprehensiveAnalysisService comprehensiveAnalysisService;

    @Autowired
    private ExperimentConfigService experimentConfigService;

    /**
     * 执行完整的风险分析Pipeline（无状态回调）
     */
    public PipelineResult execute(String namespace) {
        return execute(namespace, null, PipelineLogCallback.NOOP);
    }

    /**
     * 执行完整的风险分析Pipeline（带状态回调）
     */
    public PipelineResult execute(String namespace, PipelineExecutionStatus status) {
        return execute(namespace, status, PipelineLogCallback.NOOP);
    }

    /**
     * 执行完整的风险分析Pipeline（带状态回调和日志回调）
     *
     * @param namespace 命名空间
     * @param status 状态回调（可选），用于实时更新执行状态
     * @param logCallback 日志回调（可选），用于记录详细日志到持久层
     * @return Pipeline执行结果
     */
    public PipelineResult execute(String namespace, PipelineExecutionStatus status, PipelineLogCallback logCallback) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting risk analysis pipeline for namespace: {}", namespace);

        if (logCallback == null) {
            logCallback = PipelineLogCallback.NOOP;
        }

        PipelineResult result = new PipelineResult(namespace);

        try {
            // ========== Phase 1: 规则扫描 ==========
            updatePhaseStatus(status, 1, ExecutionState.RUNNING, "开始规则扫描...");
            logCallback.info(1, "开始Phase1: 规则扫描");
            logger.info("Phase 1: Starting rule-based scanning...");

            long phase1Start = System.currentTimeMillis();
            Map<String, ServiceRiskProfile> phase1Results = executePhase1(namespace);
            result.setPhase1Results(phase1Results);
            long phase1Duration = System.currentTimeMillis() - phase1Start;
            result.setPhase1TimeMs(phase1Duration);

            // 记录Phase1详细日志
            int totalRulesTriggered = 0;
            int criticalCount = 0;
            int highCount = 0;
            for (ServiceRiskProfile profile : phase1Results.values()) {
                totalRulesTriggered += profile.getTriggeredRules().size();
                if ("CRITICAL".equals(profile.getRiskLevel())) criticalCount++;
                else if ("HIGH".equals(profile.getRiskLevel())) highCount++;
            }
            logCallback.info(1, String.format("扫描完成: 发现%d个服务, 触发%d条规则",
                phase1Results.size(), totalRulesTriggered), phase1Duration);
            if (criticalCount > 0 || highCount > 0) {
                logCallback.warn(1, String.format("发现高风险服务: CRITICAL=%d, HIGH=%d", criticalCount, highCount));
            }

            updatePhaseStatus(status, 1, ExecutionState.COMPLETED,
                String.format("完成: 扫描了%d个服务", phase1Results.size()));
            logger.info("Phase 1 completed: {} services scanned", phase1Results.size());

            // 获取服务拓扑
            logCallback.info(1, "获取服务拓扑数据...");
            long toMs = System.currentTimeMillis();
            long fromMs = toMs - 3600 * 1000;
            ServiceMapData serviceMap = serviceMapService.getServiceMap(namespace, fromMs, toMs);
            logCallback.info(1, String.format("拓扑数据: %d个节点, %d条边",
                serviceMap.getNodes().size(), serviceMap.getEdges().size()));
            logger.info("Fetched service topology for namespace '{}': {} nodes, {} edges",
                namespace, serviceMap.getNodes().size(), serviceMap.getEdges().size());

            // ========== Phase 2: 拓扑LLM分析 ==========
            updatePhaseStatus(status, 2, ExecutionState.RUNNING, "开始拓扑LLM分析...");
            logCallback.info(2, "开始Phase2: 拓扑风险分析");
            long phase2Start = System.currentTimeMillis();
            logger.info("Phase 2: Starting topology LLM analysis...");

            TopologyRiskResult phase2Result = executePhase2(namespace, serviceMap, phase1Results);
            result.setPhase2Result(phase2Result);
            long phase2Duration = System.currentTimeMillis() - phase2Start;
            result.setPhase2TimeMs(phase2Duration);

            // 记录Phase2详细日志
            logCallback.info(2, String.format("拓扑分析完成: 发现%d个拓扑风险",
                phase2Result.getRisks().size()), phase2Duration);
            for (TopologyRiskResult.TopologyRisk risk : phase2Result.getRisks()) {
                if ("HIGH".equals(risk.getSeverity()) || "CRITICAL".equals(risk.getSeverity())) {
                    logCallback.warn(2, String.format("[%s] %s: %s",
                        risk.getSeverity(), risk.getCategory(), risk.getName()));
                }
            }

            updatePhaseStatus(status, 2, ExecutionState.COMPLETED,
                String.format("完成: 发现%d个拓扑风险", phase2Result.getRisks().size()));
            logger.info("Phase 2 completed: {} topology risks found", phase2Result.getRisks().size());

            // ========== Phase 3: RiskRank计算 ==========
            updatePhaseStatus(status, 3, ExecutionState.RUNNING, "开始RiskRank计算...");
            logCallback.info(3, "开始Phase3: RiskRank综合排名计算");
            long phase3Start = System.currentTimeMillis();
            logger.info("Phase 3: Starting RiskRank calculation...");

            RiskRankResult phase3Result = executePhase3(phase1Results, phase2Result, serviceMap);
            result.setPhase3Result(phase3Result);
            long phase3Duration = System.currentTimeMillis() - phase3Start;
            result.setPhase3TimeMs(phase3Duration);

            // 记录Phase3详细日志 - Top 5高风险服务
            logCallback.info(3, String.format("排名计算完成: %d个服务参与排名",
                phase3Result.getRankedServices().size()), phase3Duration);
            int topCount = Math.min(5, phase3Result.getRankedServices().size());
            for (int i = 0; i < topCount; i++) {
                RiskRankResult.RankedService ranked = phase3Result.getRankedServices().get(i);
                logCallback.info(3, String.format("Top%d: %s (分数=%.2f, 级别=%s)",
                    i + 1, ranked.getServiceName(), ranked.getRiskRankScore(), ranked.getRiskLevel()));
            }

            updatePhaseStatus(status, 3, ExecutionState.COMPLETED,
                String.format("完成: 排名了%d个服务", phase3Result.getRankedServices().size()));
            logger.info("Phase 3 completed: {} services ranked", phase3Result.getRankedServices().size());

            // ========== Phase 4: Trace深度分析 ==========
            updatePhaseStatus(status, 4, ExecutionState.RUNNING,
                String.format("开始Trace分析(Top %d服务)...", topNServicesForTrace));
            logCallback.info(4, String.format("开始Phase4: Trace深度分析 (分析Top %d高风险服务)", topNServicesForTrace));
            long phase4Start = System.currentTimeMillis();
            logger.info("Phase 4: Starting trace analysis for top {} services...", topNServicesForTrace);

            Map<String, TraceAnalysisResult> phase4Results = executePhase4WithLogging(phase3Result, logCallback);
            result.setPhase4Results(phase4Results);
            long phase4Duration = System.currentTimeMillis() - phase4Start;
            result.setPhase4TimeMs(phase4Duration);

            logCallback.info(4, String.format("Trace分析完成: 分析了%d个服务", phase4Results.size()), phase4Duration);

            updatePhaseStatus(status, 4, ExecutionState.COMPLETED,
                String.format("完成: 分析了%d个服务的Trace", phase4Results.size()));
            logger.info("Phase 4 completed: {} services analyzed", phase4Results.size());

            // ========== Phase 5: 综合分析与故障场景生成 ==========
            updatePhaseStatus(status, 5, ExecutionState.RUNNING, "开始综合分析与故障场景生成...");
            logCallback.info(5, "开始Phase5: 综合分析与故障场景生成");
            long phase5Start = System.currentTimeMillis();
            logger.info("Phase 5: Starting comprehensive analysis and chaos scenario generation...");

            ComprehensiveAnalysisResult phase5Result = executePhase5(
                namespace, phase1Results, phase2Result, phase3Result, phase4Results);
            result.setPhase5Result(phase5Result);
            long phase5Duration = System.currentTimeMillis() - phase5Start;
            result.setPhase5TimeMs(phase5Duration);

            int scenarioCount = phase5Result.getGlobalSummary() != null ?
                phase5Result.getGlobalSummary().getTotalScenariosGenerated() : 0;

            // 记录Phase5详细日志
            logCallback.info(5, String.format("综合分析完成: 分析%d个服务, 生成%d个故障场景",
                phase5Result.getServiceAnalyses().size(), scenarioCount), phase5Duration);
            for (Map.Entry<String, ServiceComprehensiveAnalysis> entry : phase5Result.getServiceAnalyses().entrySet()) {
                ServiceComprehensiveAnalysis analysis = entry.getValue();
                if (!analysis.getChaosScenarios().isEmpty()) {
                    logCallback.info(5, String.format("服务[%s]: 风险分数=%.2f, 生成%d个演练场景",
                        entry.getKey(), analysis.getOverallRiskScore(), analysis.getChaosScenarios().size()));
                }
            }

            updatePhaseStatus(status, 5, ExecutionState.COMPLETED,
                String.format("完成: 生成了%d个故障场景", scenarioCount));
            logger.info("Phase 5 completed: {} services comprehensively analyzed, {} chaos scenarios generated",
                phase5Result.getServiceAnalyses().size(), scenarioCount);

            // ========== Phase 6: 实验配置生成 ==========
            updatePhaseStatus(status, 6, ExecutionState.RUNNING, "开始生成实验配置...");
            logCallback.info(6, "开始Phase6: ChaosBlade实验配置生成");
            long phase6Start = System.currentTimeMillis();
            logger.info("Phase 6: Starting ExperimentConfig generation for chaos scenarios...");

            int configsGenerated = executePhase6WithLogging(namespace, phase5Result, logCallback);
            long phase6Duration = System.currentTimeMillis() - phase6Start;
            result.setPhase6TimeMs(phase6Duration);

            logCallback.info(6, String.format("配置生成完成: 成功生成%d个实验配置", configsGenerated), phase6Duration);

            updatePhaseStatus(status, 6, ExecutionState.COMPLETED,
                String.format("完成: 生成了%d个实验配置", configsGenerated));
            logger.info("Phase 6 completed: {} ExperimentConfigs generated", configsGenerated);

            // 生成最终摘要
            generateSummary(result);

            result.setSuccess(true);

            // 记录总结日志
            long totalDuration = System.currentTimeMillis() - startTime;
            logCallback.info(6, String.format("Pipeline执行完成! 总耗时=%dms, 服务数=%d, 场景数=%d",
                totalDuration, phase1Results.size(), scenarioCount), totalDuration);

        } catch (Exception e) {
            logger.error("Pipeline execution failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());

            // 记录错误日志
            int failedPhase = status != null ? status.getCurrentPhase() : 0;
            logCallback.error(failedPhase, "Pipeline执行失败: " + e.getMessage());

            // 标记失败的Phase
            if (status != null) {
                status.setFailedPhase(status.getCurrentPhase());
                updatePhaseStatus(status, status.getCurrentPhase(), ExecutionState.FAILED, e.getMessage());
            }
        }

        result.setTotalTimeMs(System.currentTimeMillis() - startTime);
        logger.info("Pipeline completed: success={}, totalTime={}ms", result.isSuccess(), result.getTotalTimeMs());

        return result;
    }

    /**
     * Phase 4: Trace深度分析（带日志）
     */
    private Map<String, TraceAnalysisResult> executePhase4WithLogging(RiskRankResult phase3Result,
            PipelineLogCallback logCallback) {
        Map<String, TraceAnalysisResult> results = new HashMap<>();

        List<RiskRankResult.RankedService> topServices = phase3Result.getRankedServices()
            .stream()
            .limit(topNServicesForTrace)
            .collect(Collectors.toList());

        for (RiskRankResult.RankedService rankedService : topServices) {
            String serviceName = rankedService.getServiceName();
            logCallback.info(4, String.format("分析服务[%s]的Trace数据 (排名=%d, 分数=%.2f)",
                serviceName, rankedService.getRank(), rankedService.getRiskRankScore()));
            logger.info("Phase 4: Analyzing traces for service: {} (rank={})",
                serviceName, rankedService.getRank());

            try {
                long start = System.currentTimeMillis();
                TraceAnalysisResult traceResult = traceAnalysisService.analyzeServiceTraces(serviceName);
                results.put(serviceName, traceResult);
                long duration = System.currentTimeMillis() - start;

                logCallback.info(4, String.format("  -> 发现%d个API, 选取%d条代表性Trace",
                    traceResult.getApiSummaries().size(), traceResult.getSelectedTraces().size()), duration);
                logger.info("Trace analysis for {}: {} APIs, {} selected traces",
                    serviceName, traceResult.getApiSummaries().size(), traceResult.getSelectedTraces().size());
            } catch (Exception e) {
                logCallback.error(4, String.format("分析服务[%s]失败: %s", serviceName, e.getMessage()));
                logger.error("Failed to analyze traces for service {}: {}", serviceName, e.getMessage(), e);
            }
        }

        return results;
    }

    /**
     * Phase 6: 实验配置生成（带日志）
     */
    private int executePhase6WithLogging(String namespace, ComprehensiveAnalysisResult phase5Result,
            PipelineLogCallback logCallback) {
        int successCount = 0;
        int totalCount = 0;

        for (Map.Entry<String, ServiceComprehensiveAnalysis> entry : phase5Result.getServiceAnalyses().entrySet()) {
            String serviceName = entry.getKey();
            ServiceComprehensiveAnalysis serviceAnalysis = entry.getValue();

            for (ChaosScenario scenario : serviceAnalysis.getChaosScenarios()) {
                totalCount++;

                try {
                    if (scenario.getCode() == null || scenario.getCode().isEmpty()) {
                        logCallback.warn(6, String.format("场景[%s]缺少故障类型code, 跳过", scenario.getScenarioId()));
                        logger.warn("Scenario {} has no fault code, skipping config generation", scenario.getScenarioId());
                        scenario.setConfigGenerated(false);
                        scenario.setConfigError("Missing fault code (app_code)");
                        continue;
                    }

                    ExperimentConfig config = experimentConfigService.buildFromScenario(
                        scenario, serviceName, namespace, scenario.getFaultParams());

                    scenario.setExperimentConfig(config);
                    scenario.setConfigGenerated(true);
                    successCount++;

                    logCallback.info(6, String.format("生成配置: %s -> 服务[%s], 类型=%s",
                        scenario.getName(), serviceName, scenario.getCode()));
                    logger.info("Generated ExperimentConfig for scenario: {} -> service: {}",
                        scenario.getName(), serviceName);

                } catch (Exception e) {
                    logCallback.error(6, String.format("生成配置失败: %s (服务=%s): %s",
                        scenario.getScenarioId(), serviceName, e.getMessage()));
                    logger.error("Failed to generate ExperimentConfig for scenario {} (service={}): {}",
                        scenario.getScenarioId(), serviceName, e.getMessage());
                    scenario.setConfigGenerated(false);
                    scenario.setConfigError(e.getMessage());
                }
            }
        }

        logCallback.info(6, String.format("配置生成统计: 成功%d/%d", successCount, totalCount));
        logger.info("Phase 6 summary: {}/{} ExperimentConfigs generated successfully", successCount, totalCount);
        return successCount;
    }

    /**
     * 更新Phase执行状态
     */
    private void updatePhaseStatus(PipelineExecutionStatus status, int phase,
            ExecutionState state, String message) {
        if (status == null) return;

        status.setCurrentPhase(phase);

        if (phase >= 1 && phase <= status.getPhaseStatuses().size()) {
            PhaseStatus phaseStatus = status.getPhaseStatuses().get(phase - 1);
            phaseStatus.setState(state);
            phaseStatus.setMessage(message);

            if (state == ExecutionState.RUNNING) {
                phaseStatus.setStartTimeMs(System.currentTimeMillis());
            } else if (state == ExecutionState.COMPLETED || state == ExecutionState.FAILED) {
                if (phaseStatus.getStartTimeMs() > 0) {
                    phaseStatus.setDurationMs(System.currentTimeMillis() - phaseStatus.getStartTimeMs());
                }
            }
        }
    }
    
    /**
     * Phase 1: 规则扫描
     */
    private Map<String, ServiceRiskProfile> executePhase1(String namespace) {
        NamespaceScanResult scanResult = riskRuleEngineService.scanNamespace(namespace);
        return scanResult.getServiceProfiles();
    }
    
    /**
     * Phase 2: 拓扑LLM分析
     */
    private TopologyRiskResult executePhase2(String namespace, ServiceMapData serviceMap,
            Map<String, ServiceRiskProfile> phase1Results) {
        return topologyRiskLLMService.analyzeTopology(namespace, serviceMap, phase1Results);
    }
    
    /**
     * Phase 3: RiskRank计算
     */
    private RiskRankResult executePhase3(Map<String, ServiceRiskProfile> phase1Results,
            TopologyRiskResult phase2Result, ServiceMapData serviceMap) {
        
        // 合并Phase1和Phase2的分数作为固有风险分
        Map<String, Double> inherentScores = new HashMap<>();
        
        // Phase1分数
        for (Map.Entry<String, ServiceRiskProfile> entry : phase1Results.entrySet()) {
            inherentScores.put(entry.getKey(), (double) entry.getValue().getTotalScore());
        }
        
        // 加上Phase2的拓扑风险分数
        for (Map.Entry<String, Integer> entry : phase2Result.getServiceTopologyScores().entrySet()) {
            inherentScores.merge(entry.getKey(), entry.getValue().doubleValue(), Double::sum);
        }
        
        return riskRankAlgorithm.calculate(inherentScores, serviceMap);
    }
    
    /**
     * 生成最终摘要
     */
    private void generateSummary(PipelineResult result) {
        PipelineResult.PipelineSummary summary = result.getSummary();

        // 统计Phase1
        int totalRules = 0;
        int criticalCount = 0;
        for (ServiceRiskProfile profile : result.getPhase1Results().values()) {
            totalRules += profile.getTriggeredRules().size();
            if ("CRITICAL".equals(profile.getRiskLevel())) {
                criticalCount++;
            }
        }
        summary.setTotalRulesTriggered(totalRules);
        summary.setCriticalServicesCount(criticalCount);

        // 统计Phase2
        summary.setTopologyRisksCount(result.getPhase2Result().getRisks().size());

        // 统计Phase3并构建完整排名列表
        List<PipelineResult.ServiceRiskRanking> rankedList = new ArrayList<>();

        for (RiskRankResult.RankedService ranked : result.getPhase3Result().getRankedServices()) {
            PipelineResult.ServiceRiskRanking ranking = new PipelineResult.ServiceRiskRanking();
            ranking.setRank(ranked.getRank());
            ranking.setServiceName(ranked.getServiceName());
            ranking.setRiskRankScore(ranked.getRiskRankScore());
            ranking.setInherentScore(ranked.getInherentRiskScore());
            ranking.setPropagatedScore(ranked.getPropagatedRiskScore());
            ranking.setRiskLevel(ranked.getRiskLevel());

            // 从Phase1结果中获取额外信息
            ServiceRiskProfile phase1Profile = result.getPhase1Results().get(ranked.getServiceName());
            if (phase1Profile != null) {
                ranking.setRulesTriggered(phase1Profile.getTriggeredRules().size());
                ranking.setCriticalInfra(phase1Profile.isCriticalInfra());
            }

            rankedList.add(ranking);
        }

        summary.setRankedServices(rankedList);

        // 设置最高风险服务
        if (!rankedList.isEmpty()) {
            PipelineResult.ServiceRiskRanking top = rankedList.get(0);
            summary.setHighestRiskService(top.getServiceName());
            summary.setHighestRiskScore(top.getRiskRankScore());
        }

        summary.setTotalServicesAnalyzed(result.getPhase1Results().size());
    }

    /**
     * Phase 5: 综合分析与故障场景生成
     */
    private ComprehensiveAnalysisResult executePhase5(
            String namespace,
            Map<String, ServiceRiskProfile> phase1Results,
            TopologyRiskResult phase2Result,
            RiskRankResult phase3Result,
            Map<String, TraceAnalysisResult> phase4Results) {

        return comprehensiveAnalysisService.analyze(
            namespace, phase1Results, phase2Result, phase3Result, phase4Results);
    }
}

