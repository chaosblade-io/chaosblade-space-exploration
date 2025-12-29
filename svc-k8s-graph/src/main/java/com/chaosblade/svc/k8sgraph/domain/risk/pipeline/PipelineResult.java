package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险分析Pipeline完整结果
 * 
 * 包含三个阶段的所有输出
 */
public class PipelineResult {
    
    /** 命名空间 */
    private String namespace;
    
    /** 执行是否成功 */
    private boolean success;
    
    /** 错误信息（如果失败） */
    private String errorMessage;
    
    /** Phase 1结果：各服务的规则扫描结果 */
    private Map<String, ServiceRiskProfile> phase1Results;
    
    /** Phase 2结果：拓扑风险分析 */
    private TopologyRiskResult phase2Result;
    
    /** Phase 3结果：RiskRank排名 */
    private RiskRankResult phase3Result;

    /** Phase 4结果：Trace分析（针对Top N服务） */
    private Map<String, TraceAnalysisResult> phase4Results;

    /** Phase 5结果：综合分析与故障场景生成 */
    private ComprehensiveAnalysisResult phase5Result;

    /** 执行摘要 */
    private PipelineSummary summary;

    /** Phase 1耗时(ms) */
    private long phase1TimeMs;

    /** Phase 2耗时(ms) */
    private long phase2TimeMs;

    /** Phase 3耗时(ms) */
    private long phase3TimeMs;

    /** Phase 4耗时(ms) */
    private long phase4TimeMs;

    /** Phase 5耗时(ms) */
    private long phase5TimeMs;

    /** 总耗时(ms) */
    private long totalTimeMs;
    
    /** 执行时间 */
    private String executionTime;
    
    public PipelineResult() {
        this.phase1Results = new HashMap<>();
        this.phase4Results = new HashMap<>();
        this.summary = new PipelineSummary();
        this.executionTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public PipelineResult(String namespace) {
        this();
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Map<String, ServiceRiskProfile> getPhase1Results() { return phase1Results; }
    public void setPhase1Results(Map<String, ServiceRiskProfile> phase1Results) { this.phase1Results = phase1Results; }
    
    public TopologyRiskResult getPhase2Result() { return phase2Result; }
    public void setPhase2Result(TopologyRiskResult phase2Result) { this.phase2Result = phase2Result; }
    
    public RiskRankResult getPhase3Result() { return phase3Result; }
    public void setPhase3Result(RiskRankResult phase3Result) { this.phase3Result = phase3Result; }

    public Map<String, TraceAnalysisResult> getPhase4Results() { return phase4Results; }
    public void setPhase4Results(Map<String, TraceAnalysisResult> phase4Results) { this.phase4Results = phase4Results; }

    public ComprehensiveAnalysisResult getPhase5Result() { return phase5Result; }
    public void setPhase5Result(ComprehensiveAnalysisResult phase5Result) { this.phase5Result = phase5Result; }

    public PipelineSummary getSummary() { return summary; }
    public void setSummary(PipelineSummary summary) { this.summary = summary; }

    public long getPhase1TimeMs() { return phase1TimeMs; }
    public void setPhase1TimeMs(long phase1TimeMs) { this.phase1TimeMs = phase1TimeMs; }

    public long getPhase2TimeMs() { return phase2TimeMs; }
    public void setPhase2TimeMs(long phase2TimeMs) { this.phase2TimeMs = phase2TimeMs; }

    public long getPhase3TimeMs() { return phase3TimeMs; }
    public void setPhase3TimeMs(long phase3TimeMs) { this.phase3TimeMs = phase3TimeMs; }

    public long getPhase4TimeMs() { return phase4TimeMs; }
    public void setPhase4TimeMs(long phase4TimeMs) { this.phase4TimeMs = phase4TimeMs; }

    public long getPhase5TimeMs() { return phase5TimeMs; }
    public void setPhase5TimeMs(long phase5TimeMs) { this.phase5TimeMs = phase5TimeMs; }

    public long getTotalTimeMs() { return totalTimeMs; }
    public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }
    
    public String getExecutionTime() { return executionTime; }
    public void setExecutionTime(String executionTime) { this.executionTime = executionTime; }
    
    /**
     * Pipeline执行摘要
     */
    public static class PipelineSummary {
        private int totalServicesAnalyzed;
        private int totalRulesTriggered;
        private int criticalServicesCount;
        private int topologyRisksCount;
        private String highestRiskService;
        private double highestRiskScore;

        /** 完整的服务风险排名列表（按RiskRank分数降序） */
        private List<ServiceRiskRanking> rankedServices;

        public PipelineSummary() {
            this.rankedServices = new ArrayList<>();
        }

        public int getTotalServicesAnalyzed() { return totalServicesAnalyzed; }
        public void setTotalServicesAnalyzed(int totalServicesAnalyzed) { this.totalServicesAnalyzed = totalServicesAnalyzed; }

        public int getTotalRulesTriggered() { return totalRulesTriggered; }
        public void setTotalRulesTriggered(int totalRulesTriggered) { this.totalRulesTriggered = totalRulesTriggered; }

        public int getCriticalServicesCount() { return criticalServicesCount; }
        public void setCriticalServicesCount(int criticalServicesCount) { this.criticalServicesCount = criticalServicesCount; }

        public int getTopologyRisksCount() { return topologyRisksCount; }
        public void setTopologyRisksCount(int topologyRisksCount) { this.topologyRisksCount = topologyRisksCount; }

        public String getHighestRiskService() { return highestRiskService; }
        public void setHighestRiskService(String highestRiskService) { this.highestRiskService = highestRiskService; }

        public double getHighestRiskScore() { return highestRiskScore; }
        public void setHighestRiskScore(double highestRiskScore) { this.highestRiskScore = highestRiskScore; }

        public List<ServiceRiskRanking> getRankedServices() { return rankedServices; }
        public void setRankedServices(List<ServiceRiskRanking> rankedServices) { this.rankedServices = rankedServices; }
    }

    /**
     * 服务风险排名条目（简化版，用于摘要展示）
     */
    public static class ServiceRiskRanking {
        private int rank;
        private String serviceName;
        private double riskRankScore;
        private double inherentScore;
        private double propagatedScore;
        private String riskLevel;
        private int rulesTriggered;
        private boolean criticalInfra;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public double getRiskRankScore() { return riskRankScore; }
        public void setRiskRankScore(double riskRankScore) { this.riskRankScore = riskRankScore; }

        public double getInherentScore() { return inherentScore; }
        public void setInherentScore(double inherentScore) { this.inherentScore = inherentScore; }

        public double getPropagatedScore() { return propagatedScore; }
        public void setPropagatedScore(double propagatedScore) { this.propagatedScore = propagatedScore; }

        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

        public int getRulesTriggered() { return rulesTriggered; }
        public void setRulesTriggered(int rulesTriggered) { this.rulesTriggered = rulesTriggered; }

        public boolean isCriticalInfra() { return criticalInfra; }
        public void setCriticalInfra(boolean criticalInfra) { this.criticalInfra = criticalInfra; }
    }
}

