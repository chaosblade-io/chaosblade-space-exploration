package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RiskRank算法结果
 * 
 * Phase 3的输出：基于改良PageRank算法的服务风险排名
 */
public class RiskRankResult {
    
    /** 排名后的服务列表（按风险分数降序） */
    private List<RankedService> rankedServices;
    
    /** 每个服务的风险贡献分析 */
    private Map<String, RiskContribution> contributions;
    
    /** 算法参数 */
    private AlgorithmParams params;
    
    /** 迭代次数 */
    private int iterations;
    
    /** 是否收敛 */
    private boolean converged;
    
    /** 计算耗时(ms) */
    private long computeTimeMs;
    
    public RiskRankResult() {
        this.rankedServices = new ArrayList<>();
        this.contributions = new HashMap<>();
        this.params = new AlgorithmParams();
    }
    
    public void addRankedService(RankedService service) {
        this.rankedServices.add(service);
    }
    
    // Getters and Setters
    public List<RankedService> getRankedServices() { return rankedServices; }
    public void setRankedServices(List<RankedService> rankedServices) { this.rankedServices = rankedServices; }
    
    public Map<String, RiskContribution> getContributions() { return contributions; }
    public void setContributions(Map<String, RiskContribution> contributions) { this.contributions = contributions; }
    
    public AlgorithmParams getParams() { return params; }
    public void setParams(AlgorithmParams params) { this.params = params; }
    
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    
    public boolean isConverged() { return converged; }
    public void setConverged(boolean converged) { this.converged = converged; }
    
    public long getComputeTimeMs() { return computeTimeMs; }
    public void setComputeTimeMs(long computeTimeMs) { this.computeTimeMs = computeTimeMs; }
    
    /**
     * 排名后的服务
     */
    public static class RankedService {
        private int rank;
        private String serviceName;
        private double riskRankScore;
        private double inherentRiskScore;      // 固有风险分（Phase1+Phase2）
        private double propagatedRiskScore;    // 传播风险分
        private double inherentRiskRatio;      // 固有风险占比
        private double propagatedRiskRatio;    // 传播风险占比
        private List<String> topContributors;  // 主要风险贡献者（上游服务）
        private String riskLevel;              // CRITICAL/HIGH/MEDIUM/LOW
        
        public RankedService() {
            this.topContributors = new ArrayList<>();
        }
        
        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public double getRiskRankScore() { return riskRankScore; }
        public void setRiskRankScore(double riskRankScore) { this.riskRankScore = riskRankScore; }
        public double getInherentRiskScore() { return inherentRiskScore; }
        public void setInherentRiskScore(double inherentRiskScore) { this.inherentRiskScore = inherentRiskScore; }
        public double getPropagatedRiskScore() { return propagatedRiskScore; }
        public void setPropagatedRiskScore(double propagatedRiskScore) { this.propagatedRiskScore = propagatedRiskScore; }
        public double getInherentRiskRatio() { return inherentRiskRatio; }
        public void setInherentRiskRatio(double inherentRiskRatio) { this.inherentRiskRatio = inherentRiskRatio; }
        public double getPropagatedRiskRatio() { return propagatedRiskRatio; }
        public void setPropagatedRiskRatio(double propagatedRiskRatio) { this.propagatedRiskRatio = propagatedRiskRatio; }
        public List<String> getTopContributors() { return topContributors; }
        public void setTopContributors(List<String> topContributors) { this.topContributors = topContributors; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    }
    
    /**
     * 风险贡献分析
     */
    public static class RiskContribution {
        private String serviceName;
        private Map<String, Double> upstreamContributions;   // 上游服务 -> 贡献分
        private Map<String, Double> downstreamImpacts;       // 下游服务 -> 影响分
        private int inDegree;   // 入度（被多少服务依赖）
        private int outDegree;  // 出度（依赖多少服务）
        
        public RiskContribution() {
            this.upstreamContributions = new HashMap<>();
            this.downstreamImpacts = new HashMap<>();
        }
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public Map<String, Double> getUpstreamContributions() { return upstreamContributions; }
        public void setUpstreamContributions(Map<String, Double> upstreamContributions) { this.upstreamContributions = upstreamContributions; }
        public Map<String, Double> getDownstreamImpacts() { return downstreamImpacts; }
        public void setDownstreamImpacts(Map<String, Double> downstreamImpacts) { this.downstreamImpacts = downstreamImpacts; }
        public int getInDegree() { return inDegree; }
        public void setInDegree(int inDegree) { this.inDegree = inDegree; }
        public int getOutDegree() { return outDegree; }
        public void setOutDegree(int outDegree) { this.outDegree = outDegree; }
    }
    
    /**
     * 算法参数
     */
    public static class AlgorithmParams {
        private double dampingFactor = 0.85;
        private int maxIterations = 100;
        private double convergenceThreshold = 0.0001;
        
        public double getDampingFactor() { return dampingFactor; }
        public void setDampingFactor(double dampingFactor) { this.dampingFactor = dampingFactor; }
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public double getConvergenceThreshold() { return convergenceThreshold; }
        public void setConvergenceThreshold(double convergenceThreshold) { this.convergenceThreshold = convergenceThreshold; }
    }
}

