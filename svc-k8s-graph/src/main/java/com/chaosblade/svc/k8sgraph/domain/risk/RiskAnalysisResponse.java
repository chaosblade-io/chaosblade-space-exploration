package com.chaosblade.svc.k8sgraph.domain.risk;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险分析响应（包含多个风险结果）
 */
public class RiskAnalysisResponse {
    
    /** 分析类型 */
    private String analysisType;
    
    /** 分析目标（资源名称/命名空间/traceId等） */
    private String analysisTarget;
    
    /** 分析时间 */
    private String analysisTime;
    
    /** 分析耗时（毫秒） */
    private long analysisTimeMs;
    
    /** 风险数量统计 */
    private RiskSummary summary;
    
    /** 风险列表 */
    private List<RiskAnalysisResult> risks;

    /** 调试信息：发送给LLM的prompt（仅当配置开启时显示） */
    private String debugPrompt;

    /** 调试信息：系统提示词（仅当配置开启时显示） */
    private String debugSystemPrompt;

    public RiskAnalysisResponse() {
        this.analysisTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.risks = new ArrayList<>();
        this.summary = new RiskSummary();
    }
    
    public static RiskAnalysisResponse create(String analysisType, String analysisTarget) {
        RiskAnalysisResponse response = new RiskAnalysisResponse();
        response.setAnalysisType(analysisType);
        response.setAnalysisTarget(analysisTarget);
        return response;
    }
    
    public void calculateSummary() {
        int critical = 0, high = 0, medium = 0, low = 0;
        for (RiskAnalysisResult risk : risks) {
            if (risk.getSeverity() != null) {
                switch (risk.getSeverity()) {
                    case CRITICAL: critical++; break;
                    case HIGH: high++; break;
                    case MEDIUM: medium++; break;
                    case LOW: low++; break;
                }
            }
        }
        summary.setTotal(risks.size());
        summary.setCritical(critical);
        summary.setHigh(high);
        summary.setMedium(medium);
        summary.setLow(low);
    }
    
    // Getters and Setters
    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
    
    public String getAnalysisTarget() { return analysisTarget; }
    public void setAnalysisTarget(String analysisTarget) { this.analysisTarget = analysisTarget; }
    
    public String getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(String analysisTime) { this.analysisTime = analysisTime; }
    
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    
    public RiskSummary getSummary() { return summary; }
    public void setSummary(RiskSummary summary) { this.summary = summary; }
    
    public List<RiskAnalysisResult> getRisks() { return risks; }
    public void setRisks(List<RiskAnalysisResult> risks) { this.risks = risks; }

    public String getDebugPrompt() { return debugPrompt; }
    public void setDebugPrompt(String debugPrompt) { this.debugPrompt = debugPrompt; }

    public String getDebugSystemPrompt() { return debugSystemPrompt; }
    public void setDebugSystemPrompt(String debugSystemPrompt) { this.debugSystemPrompt = debugSystemPrompt; }

    public void addRisk(RiskAnalysisResult risk) { this.risks.add(risk); }
    
    /**
     * 风险统计摘要
     */
    public static class RiskSummary {
        private int total;
        private int critical;
        private int high;
        private int medium;
        private int low;
        
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getCritical() { return critical; }
        public void setCritical(int critical) { this.critical = critical; }
        public int getHigh() { return high; }
        public void setHigh(int high) { this.high = high; }
        public int getMedium() { return medium; }
        public void setMedium(int medium) { this.medium = medium; }
        public int getLow() { return low; }
        public void setLow(int low) { this.low = low; }
    }
}

