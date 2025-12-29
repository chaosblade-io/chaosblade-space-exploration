package com.chaosblade.svc.k8sgraph.domain.risk;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 风险分析结果
 */
public class RiskAnalysisResult {
    
    /** 风险ID */
    private String id;
    
    /** 风险名称 */
    private String name;
    
    /** 风险描述 */
    private String description;
    
    /** 风险分类 */
    private RiskCategory category;
    
    /** 严重等级 */
    private RiskSeverity severity;
    
    /** 影响范围描述 */
    private String impactScope;
    
    /** 关联资源 */
    private String relatedResource;
    
    /** 关联命名空间 */
    private String namespace;
    
    /** 分析时间 */
    private String analysisTime;
    
    /** 分析类型（resource/topology/service-topology/trace） */
    private String analysisType;
    
    /** 推荐的故障注入建议列表 */
    private List<RecommendedFault> recommendedFaults;
    
    /** 原始 AI 响应（可选，用于调试） */
    private String rawAiResponse;
    
    public RiskAnalysisResult() {
        this.id = "risk-" + UUID.randomUUID().toString().substring(0, 8);
        this.analysisTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.recommendedFaults = new ArrayList<>();
    }
    
    public static RiskAnalysisResult create(String name, RiskCategory category, RiskSeverity severity) {
        RiskAnalysisResult result = new RiskAnalysisResult();
        result.setName(name);
        result.setCategory(category);
        result.setSeverity(severity);
        return result;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public RiskCategory getCategory() { return category; }
    public void setCategory(RiskCategory category) { this.category = category; }
    
    public RiskSeverity getSeverity() { return severity; }
    public void setSeverity(RiskSeverity severity) { this.severity = severity; }
    
    public String getImpactScope() { return impactScope; }
    public void setImpactScope(String impactScope) { this.impactScope = impactScope; }
    
    public String getRelatedResource() { return relatedResource; }
    public void setRelatedResource(String relatedResource) { this.relatedResource = relatedResource; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public String getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(String analysisTime) { this.analysisTime = analysisTime; }
    
    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
    
    public List<RecommendedFault> getRecommendedFaults() { return recommendedFaults; }
    public void setRecommendedFaults(List<RecommendedFault> recommendedFaults) { this.recommendedFaults = recommendedFaults; }
    
    public String getRawAiResponse() { return rawAiResponse; }
    public void setRawAiResponse(String rawAiResponse) { this.rawAiResponse = rawAiResponse; }
    
    public void addRecommendedFault(RecommendedFault fault) {
        this.recommendedFaults.add(fault);
    }
}

