package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 命名空间风险扫描结果
 * 
 * Phase 1的完整输出：命名空间下所有服务的风险画像汇总
 */
public class NamespaceScanResult {
    
    /** 命名空间 */
    private String namespace;
    
    /** 各服务的风险画像 */
    private Map<String, ServiceRiskProfile> serviceProfiles;
    
    /** 按风险分数排序的服务列表 */
    private List<ServiceRiskSummary> rankedServices;
    
    /** 汇总统计 */
    private ScanSummary summary;
    
    /** 扫描时间 */
    private String scanTime;
    
    /** 扫描耗时(ms) */
    private long scanDurationMs;
    
    public NamespaceScanResult() {
        this.serviceProfiles = new LinkedHashMap<>();
        this.rankedServices = new ArrayList<>();
        this.summary = new ScanSummary();
        this.scanTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public NamespaceScanResult(String namespace) {
        this();
        this.namespace = namespace;
    }
    
    /**
     * 添加服务风险画像
     */
    public void addServiceProfile(ServiceRiskProfile profile) {
        this.serviceProfiles.put(profile.getServiceName(), profile);
    }
    
    /**
     * 完成扫描后计算汇总信息
     */
    public void finalize() {
        // 按分数排序
        this.rankedServices = new ArrayList<>();
        for (ServiceRiskProfile profile : serviceProfiles.values()) {
            ServiceRiskSummary s = new ServiceRiskSummary();
            s.setServiceName(profile.getServiceName());
            s.setTotalScore(profile.getTotalScore());
            s.setRiskLevel(profile.getRiskLevel());
            s.setTriggeredRulesCount(profile.getTriggeredRules().size());
            s.setCriticalInfra(profile.isCriticalInfra());
            rankedServices.add(s);
        }
        rankedServices.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
        
        // 计算汇总
        summary.setTotalServices(serviceProfiles.size());
        int critical = 0, high = 0, medium = 0, low = 0;
        int totalRules = 0;
        
        for (ServiceRiskProfile profile : serviceProfiles.values()) {
            switch (profile.getRiskLevel()) {
                case "CRITICAL": critical++; break;
                case "HIGH": high++; break;
                case "MEDIUM": medium++; break;
                case "LOW": low++; break;
            }
            totalRules += profile.getTriggeredRules().size();
        }
        
        summary.setCriticalCount(critical);
        summary.setHighCount(high);
        summary.setMediumCount(medium);
        summary.setLowCount(low);
        summary.setTotalTriggeredRules(totalRules);
    }
    
    // Getters and Setters
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public Map<String, ServiceRiskProfile> getServiceProfiles() { return serviceProfiles; }
    public void setServiceProfiles(Map<String, ServiceRiskProfile> serviceProfiles) { this.serviceProfiles = serviceProfiles; }
    
    public List<ServiceRiskSummary> getRankedServices() { return rankedServices; }
    public void setRankedServices(List<ServiceRiskSummary> rankedServices) { this.rankedServices = rankedServices; }
    
    public ScanSummary getSummary() { return summary; }
    public void setSummary(ScanSummary summary) { this.summary = summary; }
    
    public String getScanTime() { return scanTime; }
    public void setScanTime(String scanTime) { this.scanTime = scanTime; }
    
    public long getScanDurationMs() { return scanDurationMs; }
    public void setScanDurationMs(long scanDurationMs) { this.scanDurationMs = scanDurationMs; }
    
    /**
     * 服务风险摘要（用于排名显示）
     */
    public static class ServiceRiskSummary {
        private String serviceName;
        private int totalScore;
        private String riskLevel;
        private int triggeredRulesCount;
        private boolean criticalInfra;
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public int getTotalScore() { return totalScore; }
        public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public int getTriggeredRulesCount() { return triggeredRulesCount; }
        public void setTriggeredRulesCount(int triggeredRulesCount) { this.triggeredRulesCount = triggeredRulesCount; }
        public boolean isCriticalInfra() { return criticalInfra; }
        public void setCriticalInfra(boolean criticalInfra) { this.criticalInfra = criticalInfra; }
    }
    
    /**
     * 扫描汇总
     */
    public static class ScanSummary {
        private int totalServices;
        private int criticalCount;
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private int totalTriggeredRules;
        
        public int getTotalServices() { return totalServices; }
        public void setTotalServices(int totalServices) { this.totalServices = totalServices; }
        public int getCriticalCount() { return criticalCount; }
        public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
        public int getHighCount() { return highCount; }
        public void setHighCount(int highCount) { this.highCount = highCount; }
        public int getMediumCount() { return mediumCount; }
        public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }
        public int getLowCount() { return lowCount; }
        public void setLowCount(int lowCount) { this.lowCount = lowCount; }
        public int getTotalTriggeredRules() { return totalTriggeredRules; }
        public void setTotalTriggeredRules(int totalTriggeredRules) { this.totalTriggeredRules = totalTriggeredRules; }
    }
}

