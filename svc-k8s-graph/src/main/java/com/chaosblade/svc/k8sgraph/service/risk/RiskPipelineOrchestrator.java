package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.service.ServiceMapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 风险分析Pipeline编排服务
 *
 * 编排三阶段风险分析流程：
 * Phase 1: 规则扫描 -> ServiceRiskProfile
 * Phase 2: 拓扑LLM分析 -> TopologyRiskResult
 * Phase 3: RiskRank计算 -> RiskRankResult
 *
 * 最终输出: PipelineResult
 */
@Service
public class RiskPipelineOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RiskPipelineOrchestrator.class);

    @Autowired
    private RiskRuleEngineService riskRuleEngineService;

    @Autowired
    private TopologyRiskLLMService topologyRiskLLMService;

    @Autowired
    private RiskRankAlgorithm riskRankAlgorithm;

    @Autowired
    private ServiceMapService serviceMapService;
    
    /**
     * 执行完整的风险分析Pipeline
     * 
     * @param namespace 命名空间
     * @return Pipeline执行结果
     */
    public PipelineResult execute(String namespace) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting risk analysis pipeline for namespace: {}", namespace);
        
        PipelineResult result = new PipelineResult(namespace);
        
        try {
            // Phase 1: 规则扫描
            logger.info("Phase 1: Starting rule-based scanning...");
            Map<String, ServiceRiskProfile> phase1Results = executePhase1(namespace);
            result.setPhase1Results(phase1Results);
            result.setPhase1TimeMs(System.currentTimeMillis() - startTime);
            logger.info("Phase 1 completed: {} services scanned", phase1Results.size());
            
            // 获取服务拓扑（使用最近1小时的数据，按命名空间过滤）
            long toMs = System.currentTimeMillis();
            long fromMs = toMs - 3600 * 1000; // 1小时前
            ServiceMapData serviceMap = serviceMapService.getServiceMap(namespace, fromMs, toMs);
            logger.info("Fetched service topology for namespace '{}': {} nodes, {} edges",
                namespace, serviceMap.getNodes().size(), serviceMap.getEdges().size());
            
            // Phase 2: 拓扑LLM分析
            long phase2Start = System.currentTimeMillis();
            logger.info("Phase 2: Starting topology LLM analysis...");
            TopologyRiskResult phase2Result = executePhase2(namespace, serviceMap, phase1Results);
            result.setPhase2Result(phase2Result);
            result.setPhase2TimeMs(System.currentTimeMillis() - phase2Start);
            logger.info("Phase 2 completed: {} topology risks found", phase2Result.getRisks().size());
            
            // Phase 3: RiskRank计算
            long phase3Start = System.currentTimeMillis();
            logger.info("Phase 3: Starting RiskRank calculation...");
            RiskRankResult phase3Result = executePhase3(phase1Results, phase2Result, serviceMap);
            result.setPhase3Result(phase3Result);
            result.setPhase3TimeMs(System.currentTimeMillis() - phase3Start);
            logger.info("Phase 3 completed: {} services ranked", phase3Result.getRankedServices().size());
            
            // 生成最终摘要
            generateSummary(result);
            
            result.setSuccess(true);
            
        } catch (Exception e) {
            logger.error("Pipeline execution failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        result.setTotalTimeMs(System.currentTimeMillis() - startTime);
        logger.info("Pipeline completed: success={}, totalTime={}ms", result.isSuccess(), result.getTotalTimeMs());
        
        return result;
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
}

