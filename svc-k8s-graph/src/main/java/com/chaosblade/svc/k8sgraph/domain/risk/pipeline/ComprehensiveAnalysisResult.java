package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Phase 5: 综合分析与故障场景生成结果
 * 
 * 将三层风险信息（资源层、拓扑层、链路层）综合分析，生成可执行的混沌工程故障场景
 */
public class ComprehensiveAnalysisResult {
    
    /** 命名空间 */
    private String namespace;
    
    /** 各服务的综合分析结果 */
    private Map<String, ServiceComprehensiveAnalysis> serviceAnalyses = new HashMap<>();
    
    /** 全局执行摘要 */
    private GlobalSummary globalSummary;
    
    /** 全局改进建议 */
    private List<Recommendation> globalRecommendations = new ArrayList<>();
    
    public ComprehensiveAnalysisResult() {}
    
    public ComprehensiveAnalysisResult(String namespace) {
        this.namespace = namespace;
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public Map<String, ServiceComprehensiveAnalysis> getServiceAnalyses() { return serviceAnalyses; }
    public void setServiceAnalyses(Map<String, ServiceComprehensiveAnalysis> serviceAnalyses) { this.serviceAnalyses = serviceAnalyses; }
    
    public GlobalSummary getGlobalSummary() { return globalSummary; }
    public void setGlobalSummary(GlobalSummary globalSummary) { this.globalSummary = globalSummary; }
    
    public List<Recommendation> getGlobalRecommendations() { return globalRecommendations; }
    public void setGlobalRecommendations(List<Recommendation> globalRecommendations) { this.globalRecommendations = globalRecommendations; }
    
    // ==================== 内部类定义 ====================
    
    /**
     * 单个服务的综合分析结果
     */
    public static class ServiceComprehensiveAnalysis {
        /** 服务名称 */
        private String serviceName;
        
        /** 综合风险评级 */
        private RiskLevel overallRiskLevel;
        
        /** 综合风险分数 (0-100) */
        private double overallRiskScore;
        
        /** 主要风险清单（Top 3）及其因果关系 */
        private List<RiskWithCausality> topRisks = new ArrayList<>();
        
        /** 风险因果链分析 */
        private List<RiskCausalChain> causalChains = new ArrayList<>();
        
        /** 推荐的故障场景 */
        private List<ChaosScenario> chaosScenarios = new ArrayList<>();
        
        /** 改进建议列表（按优先级排序） */
        private List<Recommendation> recommendations = new ArrayList<>();
        
        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public RiskLevel getOverallRiskLevel() { return overallRiskLevel; }
        public void setOverallRiskLevel(RiskLevel overallRiskLevel) { this.overallRiskLevel = overallRiskLevel; }
        
        public double getOverallRiskScore() { return overallRiskScore; }
        public void setOverallRiskScore(double overallRiskScore) { this.overallRiskScore = overallRiskScore; }
        
        public List<RiskWithCausality> getTopRisks() { return topRisks; }
        public void setTopRisks(List<RiskWithCausality> topRisks) { this.topRisks = topRisks; }
        
        public List<RiskCausalChain> getCausalChains() { return causalChains; }
        public void setCausalChains(List<RiskCausalChain> causalChains) { this.causalChains = causalChains; }
        
        public List<ChaosScenario> getChaosScenarios() { return chaosScenarios; }
        public void setChaosScenarios(List<ChaosScenario> chaosScenarios) { this.chaosScenarios = chaosScenarios; }
        
        public List<Recommendation> getRecommendations() { return recommendations; }
        public void setRecommendations(List<Recommendation> recommendations) { this.recommendations = recommendations; }
    }
    
    /**
     * 风险等级
     */
    public enum RiskLevel {
        CRITICAL,  // 严重
        HIGH,      // 高
        MEDIUM,    // 中
        LOW        // 低
    }
    
    /**
     * 带因果关系的风险项
     */
    public static class RiskWithCausality {
        /** 风险ID */
        private String riskId;
        
        /** 风险来源层级 */
        private RiskLayer layer;
        
        /** 风险名称 */
        private String name;
        
        /** 风险描述 */
        private String description;
        
        /** 严重程度 */
        private String severity;
        
        /** 分数 */
        private double score;
        
        /** 是否是根因 */
        private boolean isRootCause;
        
        /** 导致的衍生风险ID列表 */
        private List<String> causesRiskIds = new ArrayList<>();

        /** 由哪个风险导致（如果是衍生风险） */
        private String causedByRiskId;

        /** 关联的演练场景列表 */
        private List<ChaosScenario> relatedScenarios = new ArrayList<>();

        // Getters and Setters
        public String getRiskId() { return riskId; }
        public void setRiskId(String riskId) { this.riskId = riskId; }

        public RiskLayer getLayer() { return layer; }
        public void setLayer(RiskLayer layer) { this.layer = layer; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        public boolean isRootCause() { return isRootCause; }
        public void setRootCause(boolean rootCause) { isRootCause = rootCause; }

        public List<String> getCausesRiskIds() { return causesRiskIds; }
        public void setCausesRiskIds(List<String> causesRiskIds) { this.causesRiskIds = causesRiskIds; }

        public String getCausedByRiskId() { return causedByRiskId; }
        public void setCausedByRiskId(String causedByRiskId) { this.causedByRiskId = causedByRiskId; }

        public List<ChaosScenario> getRelatedScenarios() { return relatedScenarios; }
        public void setRelatedScenarios(List<ChaosScenario> relatedScenarios) { this.relatedScenarios = relatedScenarios; }
    }

    /**
     * 风险来源层级
     */
    public enum RiskLayer {
        RESOURCE,   // 资源层（Phase 1: K8s配置）
        TOPOLOGY,   // 拓扑层（Phase 2: 服务调用关系）
        TRACE       // 链路层（Phase 4: 运行时trace）
    }

    /**
     * 风险因果链
     */
    public static class RiskCausalChain {
        /** 链条ID */
        private String chainId;

        /** 链条描述 */
        private String description;

        /** 风险链条（按因果顺序） */
        private List<String> riskSequence = new ArrayList<>();

        /** 最终影响 */
        private String impact;

        // Getters and Setters
        public String getChainId() { return chainId; }
        public void setChainId(String chainId) { this.chainId = chainId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getRiskSequence() { return riskSequence; }
        public void setRiskSequence(List<String> riskSequence) { this.riskSequence = riskSequence; }

        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
    }

    /**
     * 混沌工程故障场景
     */
    public static class ChaosScenario {
        /** 场景ID */
        private String scenarioId;

        /** 场景名称 */
        private String name;

        /** 故障代码（ChaosBlade Box故障类型code，如：chaos.container-cpu.fullload） */
        private String code;

        /** 故障类型名称（如：容器内Cpu满载） */
        private String faultName;

        /** 针对的风险ID */
        private String targetRiskId;

        /** 场景类型 */
        private ScenarioType type;

        /** 场景目标 */
        private String objective;

        /** 故障强度（1-5） */
        private int intensity;

        /** 执行步骤 */
        private List<String> steps = new ArrayList<>();

        /** 预期影响 */
        private String expectedImpact;

        /** 成功标准 */
        private List<String> successCriteria = new ArrayList<>();

        /** 回滚方案 */
        private String rollbackPlan;

        /** ChaosBlade命令示例 */
        private String chaosBladeCommand;

        /** 建议的执行时长（秒） */
        private int durationSeconds;

        // Getters and Setters
        public String getScenarioId() { return scenarioId; }
        public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getFaultName() { return faultName; }
        public void setFaultName(String faultName) { this.faultName = faultName; }

        public String getTargetRiskId() { return targetRiskId; }
        public void setTargetRiskId(String targetRiskId) { this.targetRiskId = targetRiskId; }

        public ScenarioType getType() { return type; }
        public void setType(ScenarioType type) { this.type = type; }

        public String getObjective() { return objective; }
        public void setObjective(String objective) { this.objective = objective; }

        public int getIntensity() { return intensity; }
        public void setIntensity(int intensity) { this.intensity = intensity; }

        public List<String> getSteps() { return steps; }
        public void setSteps(List<String> steps) { this.steps = steps; }

        public String getExpectedImpact() { return expectedImpact; }
        public void setExpectedImpact(String expectedImpact) { this.expectedImpact = expectedImpact; }

        public List<String> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(List<String> successCriteria) { this.successCriteria = successCriteria; }

        public String getRollbackPlan() { return rollbackPlan; }
        public void setRollbackPlan(String rollbackPlan) { this.rollbackPlan = rollbackPlan; }

        public String getChaosBladeCommand() { return chaosBladeCommand; }
        public void setChaosBladeCommand(String chaosBladeCommand) { this.chaosBladeCommand = chaosBladeCommand; }

        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    }

    /**
     * 故障场景类型
     */
    public enum ScenarioType {
        POD_KILL,           // Pod杀死
        POD_FAILURE,        // Pod故障
        CPU_STRESS,         // CPU压力
        MEMORY_STRESS,      // 内存压力
        NETWORK_DELAY,      // 网络延迟
        NETWORK_LOSS,       // 网络丢包
        NETWORK_PARTITION,  // 网络分区
        DISK_FILL,          // 磁盘填充
        IO_DELAY,           // IO延迟
        JVM_EXCEPTION,      // JVM异常注入
        HTTP_DELAY,         // HTTP延迟
        HTTP_ERROR,         // HTTP错误
        DB_DELAY,           // 数据库延迟
        TRAFFIC_SPIKE       // 流量突增
    }

    /**
     * 改进建议
     */
    public static class Recommendation {
        /** 建议ID */
        private String id;

        /** 建议标题 */
        private String title;

        /** 优先级（1最高） */
        private int priority;

        /** 关联的风险ID列表 */
        private List<String> relatedRiskIds = new ArrayList<>();

        /** 详细描述 */
        private String description;

        /** 具体行动项 */
        private List<String> actionItems = new ArrayList<>();

        /** 预期收益 */
        private String expectedBenefit;

        /** 实施成本（LOW/MEDIUM/HIGH） */
        private String implementationCost;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public List<String> getRelatedRiskIds() { return relatedRiskIds; }
        public void setRelatedRiskIds(List<String> relatedRiskIds) { this.relatedRiskIds = relatedRiskIds; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getActionItems() { return actionItems; }
        public void setActionItems(List<String> actionItems) { this.actionItems = actionItems; }

        public String getExpectedBenefit() { return expectedBenefit; }
        public void setExpectedBenefit(String expectedBenefit) { this.expectedBenefit = expectedBenefit; }

        public String getImplementationCost() { return implementationCost; }
        public void setImplementationCost(String implementationCost) { this.implementationCost = implementationCost; }
    }

    /**
     * 全局执行摘要
     */
    public static class GlobalSummary {
        /** 整体健康度评分 (0-100) */
        private double overallHealthScore;

        /** 高危服务数量 */
        private int criticalServiceCount;

        /** 高风险服务数量 */
        private int highRiskServiceCount;

        /** 中风险服务数量 */
        private int mediumRiskServiceCount;

        /** 低风险服务数量 */
        private int lowRiskServiceCount;

        /** 总分析服务数量 */
        private int totalServicesAnalyzed;

        /** 识别的风险总数 */
        private int totalRisksIdentified;

        /** 根因风险数量 */
        private int rootCauseRiskCount;

        /** 生成的故障场景总数 */
        private int totalScenariosGenerated;

        /** 关键发现 */
        private List<String> keyFindings = new ArrayList<>();

        // Getters and Setters
        public double getOverallHealthScore() { return overallHealthScore; }
        public void setOverallHealthScore(double overallHealthScore) { this.overallHealthScore = overallHealthScore; }

        public int getCriticalServiceCount() { return criticalServiceCount; }
        public void setCriticalServiceCount(int criticalServiceCount) { this.criticalServiceCount = criticalServiceCount; }

        public int getHighRiskServiceCount() { return highRiskServiceCount; }
        public void setHighRiskServiceCount(int highRiskServiceCount) { this.highRiskServiceCount = highRiskServiceCount; }

        public int getMediumRiskServiceCount() { return mediumRiskServiceCount; }
        public void setMediumRiskServiceCount(int mediumRiskServiceCount) { this.mediumRiskServiceCount = mediumRiskServiceCount; }

        public int getLowRiskServiceCount() { return lowRiskServiceCount; }
        public void setLowRiskServiceCount(int lowRiskServiceCount) { this.lowRiskServiceCount = lowRiskServiceCount; }

        public int getTotalServicesAnalyzed() { return totalServicesAnalyzed; }
        public void setTotalServicesAnalyzed(int totalServicesAnalyzed) { this.totalServicesAnalyzed = totalServicesAnalyzed; }

        public int getTotalRisksIdentified() { return totalRisksIdentified; }
        public void setTotalRisksIdentified(int totalRisksIdentified) { this.totalRisksIdentified = totalRisksIdentified; }

        public int getRootCauseRiskCount() { return rootCauseRiskCount; }
        public void setRootCauseRiskCount(int rootCauseRiskCount) { this.rootCauseRiskCount = rootCauseRiskCount; }

        public int getTotalScenariosGenerated() { return totalScenariosGenerated; }
        public void setTotalScenariosGenerated(int totalScenariosGenerated) { this.totalScenariosGenerated = totalScenariosGenerated; }

        public List<String> getKeyFindings() { return keyFindings; }
        public void setKeyFindings(List<String> keyFindings) { this.keyFindings = keyFindings; }
    }
}

