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
import java.util.function.Consumer;
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
        return execute(namespace, null);
    }

    /**
     * 执行完整的风险分析Pipeline（带状态回调）
     *
     * @param namespace 命名空间
     * @param statusCallback 状态回调（可选），用于实时更新执行状态
     * @return Pipeline执行结果
     */
    public PipelineResult execute(String namespace, PipelineExecutionStatus status) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting risk analysis pipeline for namespace: {}", namespace);

        PipelineResult result = new PipelineResult(namespace);

        try {
            // Phase 1: 规则扫描
            updatePhaseStatus(status, 1, ExecutionState.RUNNING, "开始规则扫描...");
            logger.info("Phase 1: Starting rule-based scanning...");
            Map<String, ServiceRiskProfile> phase1Results = executePhase1(namespace);
            result.setPhase1Results(phase1Results);
            result.setPhase1TimeMs(System.currentTimeMillis() - startTime);
            updatePhaseStatus(status, 1, ExecutionState.COMPLETED,
                String.format("完成: 扫描了%d个服务", phase1Results.size()));
            logger.info("Phase 1 completed: {} services scanned", phase1Results.size());

            // 获取服务拓扑（使用最近1小时的数据，按命名空间过滤）
            long toMs = System.currentTimeMillis();
            long fromMs = toMs - 3600 * 1000; // 1小时前
            ServiceMapData serviceMap = serviceMapService.getServiceMap(namespace, fromMs, toMs);
            logger.info("Fetched service topology for namespace '{}': {} nodes, {} edges",
                namespace, serviceMap.getNodes().size(), serviceMap.getEdges().size());

            // Phase 2: 拓扑LLM分析
            updatePhaseStatus(status, 2, ExecutionState.RUNNING, "开始拓扑LLM分析...");
            long phase2Start = System.currentTimeMillis();
            logger.info("Phase 2: Starting topology LLM analysis...");
            TopologyRiskResult phase2Result = executePhase2(namespace, serviceMap, phase1Results);
            result.setPhase2Result(phase2Result);
            result.setPhase2TimeMs(System.currentTimeMillis() - phase2Start);
            updatePhaseStatus(status, 2, ExecutionState.COMPLETED,
                String.format("完成: 发现%d个拓扑风险", phase2Result.getRisks().size()));
            logger.info("Phase 2 completed: {} topology risks found", phase2Result.getRisks().size());

            // Phase 3: RiskRank计算
            updatePhaseStatus(status, 3, ExecutionState.RUNNING, "开始RiskRank计算...");
            long phase3Start = System.currentTimeMillis();
            logger.info("Phase 3: Starting RiskRank calculation...");
            RiskRankResult phase3Result = executePhase3(phase1Results, phase2Result, serviceMap);
            result.setPhase3Result(phase3Result);
            result.setPhase3TimeMs(System.currentTimeMillis() - phase3Start);
            updatePhaseStatus(status, 3, ExecutionState.COMPLETED,
                String.format("完成: 排名了%d个服务", phase3Result.getRankedServices().size()));
            logger.info("Phase 3 completed: {} services ranked", phase3Result.getRankedServices().size());

            // Phase 4: Trace深度分析（针对Top N高风险服务）
            updatePhaseStatus(status, 4, ExecutionState.RUNNING,
                String.format("开始Trace分析(Top %d服务)...", topNServicesForTrace));
            long phase4Start = System.currentTimeMillis();
            logger.info("Phase 4: Starting trace analysis for top {} services...", topNServicesForTrace);
            Map<String, TraceAnalysisResult> phase4Results = executePhase4(phase3Result);
            result.setPhase4Results(phase4Results);
            result.setPhase4TimeMs(System.currentTimeMillis() - phase4Start);
            updatePhaseStatus(status, 4, ExecutionState.COMPLETED,
                String.format("完成: 分析了%d个服务的Trace", phase4Results.size()));
            logger.info("Phase 4 completed: {} services analyzed", phase4Results.size());

            // Phase 5: 综合分析与故障场景生成
            updatePhaseStatus(status, 5, ExecutionState.RUNNING, "开始综合分析与故障场景生成...");
            long phase5Start = System.currentTimeMillis();
            logger.info("Phase 5: Starting comprehensive analysis and chaos scenario generation...");
            ComprehensiveAnalysisResult phase5Result = executePhase5(
                namespace, phase1Results, phase2Result, phase3Result, phase4Results);
            result.setPhase5Result(phase5Result);
            result.setPhase5TimeMs(System.currentTimeMillis() - phase5Start);
            int scenarioCount = phase5Result.getGlobalSummary() != null ?
                phase5Result.getGlobalSummary().getTotalScenariosGenerated() : 0;
            updatePhaseStatus(status, 5, ExecutionState.COMPLETED,
                String.format("完成: 生成了%d个故障场景", scenarioCount));
            logger.info("Phase 5 completed: {} services comprehensively analyzed, {} chaos scenarios generated",
                phase5Result.getServiceAnalyses().size(), scenarioCount);

            // Phase 6: 为每个ChaosScenario生成ExperimentConfig
            updatePhaseStatus(status, 6, ExecutionState.RUNNING, "开始生成实验配置...");
            long phase6Start = System.currentTimeMillis();
            logger.info("Phase 6: Starting ExperimentConfig generation for chaos scenarios...");
            int configsGenerated = executePhase6(namespace, phase5Result);
            result.setPhase6TimeMs(System.currentTimeMillis() - phase6Start);
            updatePhaseStatus(status, 6, ExecutionState.COMPLETED,
                String.format("完成: 生成了%d个实验配置", configsGenerated));
            logger.info("Phase 6 completed: {} ExperimentConfigs generated", configsGenerated);

            // 生成最终摘要
            generateSummary(result);

            result.setSuccess(true);

        } catch (Exception e) {
            logger.error("Pipeline execution failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());

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
     * Phase 4: Trace深度分析
     * 对Top N高风险服务进行详细的Trace分析
     */
    private Map<String, TraceAnalysisResult> executePhase4(RiskRankResult phase3Result) {
        Map<String, TraceAnalysisResult> results = new HashMap<>();

        // 获取Top N服务
        List<RiskRankResult.RankedService> topServices = phase3Result.getRankedServices()
            .stream()
            .limit(topNServicesForTrace)
            .collect(Collectors.toList());

        for (RiskRankResult.RankedService rankedService : topServices) {
            String serviceName = rankedService.getServiceName();
            logger.info("Phase 4: Analyzing traces for service: {} (rank={})",
                serviceName, rankedService.getRank());

            try {
                TraceAnalysisResult traceResult = traceAnalysisService.analyzeServiceTraces(serviceName);
                results.put(serviceName, traceResult);

                logger.info("Trace analysis for {}: {} APIs, {} selected traces",
                    serviceName,
                    traceResult.getApiSummaries().size(),
                    traceResult.getSelectedTraces().size());
            } catch (Exception e) {
                logger.error("Failed to analyze traces for service {}: {}", serviceName, e.getMessage(), e);
                // 继续处理下一个服务，不中断整个流程
            }
        }

        return results;
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

    /**
     * Phase 6: 为每个ChaosScenario生成ExperimentConfig
     *
     * 遍历Phase 5生成的所有ChaosScenario，调用ExperimentConfigService生成可执行的配置
     *
     * @param namespace 命名空间
     * @param phase5Result Phase 5的综合分析结果
     * @return 成功生成的配置数量
     */
    private int executePhase6(String namespace, ComprehensiveAnalysisResult phase5Result) {
        int successCount = 0;
        int totalCount = 0;

        // 遍历每个服务的分析结果
        for (Map.Entry<String, ServiceComprehensiveAnalysis> entry : phase5Result.getServiceAnalyses().entrySet()) {
            String serviceName = entry.getKey();
            ServiceComprehensiveAnalysis serviceAnalysis = entry.getValue();

            // 遍历该服务的每个ChaosScenario
            for (ChaosScenario scenario : serviceAnalysis.getChaosScenarios()) {
                totalCount++;

                try {
                    // 检查scenario是否有必要的code字段
                    if (scenario.getCode() == null || scenario.getCode().isEmpty()) {
                        logger.warn("Scenario {} has no fault code, skipping config generation",
                            scenario.getScenarioId());
                        scenario.setConfigGenerated(false);
                        scenario.setConfigError("Missing fault code (app_code)");
                        continue;
                    }

                    // 调用ExperimentConfigService生成配置
                    ExperimentConfig config = experimentConfigService.buildFromScenario(
                        scenario,
                        serviceName,
                        namespace,
                        scenario.getFaultParams()
                    );

                    // 将生成的配置存储到scenario中
                    scenario.setExperimentConfig(config);
                    scenario.setConfigGenerated(true);
                    successCount++;

                    logger.info("Generated ExperimentConfig for scenario: {} -> service: {}",
                        scenario.getName(), serviceName);

                } catch (Exception e) {
                    logger.error("Failed to generate ExperimentConfig for scenario {} (service={}): {}",
                        scenario.getScenarioId(), serviceName, e.getMessage());
                    scenario.setConfigGenerated(false);
                    scenario.setConfigError(e.getMessage());
                }
            }
        }

        logger.info("Phase 6 summary: {}/{} ExperimentConfigs generated successfully", successCount, totalCount);
        return successCount;
    }
}

