package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑风险分析结果
 * 
 * Phase 2的输出：基于LLM的服务拓扑风险分析
 */
public class TopologyRiskResult {
    
    /** 命名空间 */
    private String namespace;
    
    /** 识别出的拓扑风险列表 */
    private List<TopologyRisk> risks;
    
    /** 各服务的拓扑风险分数（累加后的） */
    private Map<String, Integer> serviceTopologyScores;
    
    /** LLM分析耗时(ms) */
    private long analysisTimeMs;
    
    /** 分析时间 */
    private String analysisTime;
    
    /** 拓扑统计 */
    private TopologyStats stats;
    
    public TopologyRiskResult() {
        this.risks = new ArrayList<>();
        this.serviceTopologyScores = new HashMap<>();
        this.analysisTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.stats = new TopologyStats();
    }
    
    public TopologyRiskResult(String namespace) {
        this();
        this.namespace = namespace;
    }
    
    public void addRisk(TopologyRisk risk) {
        this.risks.add(risk);
        // 累加分数到受影响的服务
        for (String service : risk.getAffectedServices()) {
            serviceTopologyScores.merge(service, risk.getScore(), Integer::sum);
        }
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public List<TopologyRisk> getRisks() { return risks; }
    public void setRisks(List<TopologyRisk> risks) { this.risks = risks; }
    
    public Map<String, Integer> getServiceTopologyScores() { return serviceTopologyScores; }
    public void setServiceTopologyScores(Map<String, Integer> serviceTopologyScores) { this.serviceTopologyScores = serviceTopologyScores; }
    
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    
    public String getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(String analysisTime) { this.analysisTime = analysisTime; }
    
    public TopologyStats getStats() { return stats; }
    public void setStats(TopologyStats stats) { this.stats = stats; }
    
    /**
     * 单个拓扑风险
     */
    public static class TopologyRisk {
        private String riskId;
        private String name;
        private String description;
        private String category;        // SINGLE_POINT/CASCADING/BOTTLENECK/CIRCULAR_DEPENDENCY
        private int score;
        private List<String> affectedServices;
        private String evidence;
        private String severity;
        private String remediation;
        
        public TopologyRisk() {
            this.affectedServices = new ArrayList<>();
        }
        
        public String getRiskId() { return riskId; }
        public void setRiskId(String riskId) { this.riskId = riskId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public List<String> getAffectedServices() { return affectedServices; }
        public void setAffectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getRemediation() { return remediation; }
        public void setRemediation(String remediation) { this.remediation = remediation; }
    }
    
    /**
     * 拓扑统计信息
     */
    public static class TopologyStats {
        private int totalServices;
        private int totalEdges;
        private int maxInDegree;
        private int maxOutDegree;
        private String mostConnectedService;
        private int criticalPathLength;
        
        public int getTotalServices() { return totalServices; }
        public void setTotalServices(int totalServices) { this.totalServices = totalServices; }
        public int getTotalEdges() { return totalEdges; }
        public void setTotalEdges(int totalEdges) { this.totalEdges = totalEdges; }
        public int getMaxInDegree() { return maxInDegree; }
        public void setMaxInDegree(int maxInDegree) { this.maxInDegree = maxInDegree; }
        public int getMaxOutDegree() { return maxOutDegree; }
        public void setMaxOutDegree(int maxOutDegree) { this.maxOutDegree = maxOutDegree; }
        public String getMostConnectedService() { return mostConnectedService; }
        public void setMostConnectedService(String mostConnectedService) { this.mostConnectedService = mostConnectedService; }
        public int getCriticalPathLength() { return criticalPathLength; }
        public void setCriticalPathLength(int criticalPathLength) { this.criticalPathLength = criticalPathLength; }
    }
}

