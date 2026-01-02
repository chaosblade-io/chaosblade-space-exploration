package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.RiskRankResult;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.RiskRankResult.*;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapData;
import com.chaosblade.svc.k8sgraph.domain.service.ServiceMapEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RiskRank算法服务
 * 
 * Phase 3: 基于改良PageRank的风险传播算法
 * 
 * 核心思想：
 * 1. 每个服务有固有风险分（来自Phase1和Phase2）
 * 2. 风险沿调用链向下游传播
 * 3. 高入度服务（被多个上游依赖）累积更多传播风险
 * 4. 最终得分 = 固有风险 + 传播风险
 */
@Service
public class RiskRankAlgorithm {
    
    private static final Logger logger = LoggerFactory.getLogger(RiskRankAlgorithm.class);
    
    /** 阻尼因子（控制传播衰减） */
    private double dampingFactor = 0.85;
    
    /** 最大迭代次数 */
    private int maxIterations = 100;
    
    /** 收敛阈值 */
    private double convergenceThreshold = 0.0001;
    
    /**
     * 计算RiskRank
     * 
     * @param inherentRiskScores 各服务的固有风险分（Phase1 + Phase2）
     * @param serviceMap 服务调用拓扑
     * @return RiskRank结果
     */
    public RiskRankResult calculate(Map<String, Double> inherentRiskScores, ServiceMapData serviceMap) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting RiskRank calculation with {} services", inherentRiskScores.size());
        
        RiskRankResult result = new RiskRankResult();
        result.getParams().setDampingFactor(dampingFactor);
        result.getParams().setMaxIterations(maxIterations);
        result.getParams().setConvergenceThreshold(convergenceThreshold);
        
        if (inherentRiskScores.isEmpty()) {
            logger.warn("No services to calculate RiskRank");
            result.setComputeTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        // 1. 构建邻接表
        Map<String, List<String>> outgoing = new HashMap<>();  // 服务 -> 调用的下游服务
        Map<String, List<String>> incoming = new HashMap<>();  // 服务 -> 被哪些上游调用
        Map<String, Map<String, Double>> edgeWeights = new HashMap<>();  // 边权重
        
        buildAdjacencyLists(serviceMap, inherentRiskScores.keySet(), outgoing, incoming, edgeWeights);
        
        // 2. 初始化传播风险分数
        Map<String, Double> propagatedScores = new HashMap<>();
        for (String service : inherentRiskScores.keySet()) {
            propagatedScores.put(service, 0.0);
        }
        
        // 3. 迭代计算
        int iteration = 0;
        boolean converged = false;
        
        while (iteration < maxIterations && !converged) {
            Map<String, Double> newPropagatedScores = new HashMap<>();
            double maxDelta = 0.0;
            
            for (String service : inherentRiskScores.keySet()) {
                double propagated = 0.0;
                
                // 累加上游服务传播过来的风险
                List<String> upstreams = incoming.getOrDefault(service, Collections.emptyList());
                for (String upstream : upstreams) {
                    double upstreamTotal = inherentRiskScores.getOrDefault(upstream, 0.0) 
                                         + propagatedScores.getOrDefault(upstream, 0.0);
                    int outDegree = outgoing.getOrDefault(upstream, Collections.emptyList()).size();
                    if (outDegree > 0) {
                        // 边权重（可基于调用量）
                        double weight = edgeWeights.getOrDefault(upstream, Collections.emptyMap())
                                                   .getOrDefault(service, 1.0);
                        propagated += dampingFactor * (upstreamTotal / outDegree) * weight;
                    }
                }
                
                newPropagatedScores.put(service, propagated);
                double delta = Math.abs(propagated - propagatedScores.getOrDefault(service, 0.0));
                maxDelta = Math.max(maxDelta, delta);
            }
            
            propagatedScores = newPropagatedScores;
            iteration++;
            
            if (maxDelta < convergenceThreshold) {
                converged = true;
            }
        }
        
        result.setIterations(iteration);
        result.setConverged(converged);
        
        // 4. 计算最终分数并排名
        List<RankedService> rankedList = new ArrayList<>();
        for (String service : inherentRiskScores.keySet()) {
            double inherent = inherentRiskScores.get(service);
            double propagated = propagatedScores.getOrDefault(service, 0.0);
            double total = inherent + propagated;

            RankedService ranked = new RankedService();
            ranked.setServiceName(service);
            // 四舍五入到两位小数
            ranked.setInherentRiskScore(roundToTwoDecimals(inherent));
            ranked.setPropagatedRiskScore(roundToTwoDecimals(propagated));
            ranked.setRiskRankScore(roundToTwoDecimals(total));

            if (total > 0) {
                ranked.setInherentRiskRatio(roundToTwoDecimals(inherent / total));
                ranked.setPropagatedRiskRatio(roundToTwoDecimals(propagated / total));
            }

            ranked.setRiskLevel(calculateRiskLevel(total));
            
            // 找出主要贡献者
            List<String> contributors = findTopContributors(service, incoming, inherentRiskScores, propagatedScores);
            ranked.setTopContributors(contributors);
            
            rankedList.add(ranked);
            
            // 构建贡献分析
            RiskContribution contribution = buildContribution(service, incoming, outgoing, 
                inherentRiskScores, propagatedScores);
            result.getContributions().put(service, contribution);
        }
        
        // 按分数降序排序
        rankedList.sort((a, b) -> Double.compare(b.getRiskRankScore(), a.getRiskRankScore()));
        
        // 设置排名
        for (int i = 0; i < rankedList.size(); i++) {
            rankedList.get(i).setRank(i + 1);
        }
        
        result.setRankedServices(rankedList);
        result.setComputeTimeMs(System.currentTimeMillis() - startTime);
        
        logger.info("RiskRank calculation completed: {} iterations, converged={}, {}ms",
            iteration, converged, result.getComputeTimeMs());
        
        return result;
    }
    /**
     * 构建邻接表
     */
    private void buildAdjacencyLists(ServiceMapData serviceMap, Set<String> services,
            Map<String, List<String>> outgoing, Map<String, List<String>> incoming,
            Map<String, Map<String, Double>> edgeWeights) {

        // 初始化
        for (String service : services) {
            outgoing.put(service, new ArrayList<>());
            incoming.put(service, new ArrayList<>());
            edgeWeights.put(service, new HashMap<>());
        }

        if (serviceMap == null || serviceMap.getEdges() == null) {
            return;
        }

        // 处理边
        for (ServiceMapEdge edge : serviceMap.getEdges()) {
            String source = edge.getSourceService();
            String target = edge.getTargetService();

            // 只处理在服务集合中的服务
            if (services.contains(source) && services.contains(target)) {
                outgoing.get(source).add(target);
                incoming.get(target).add(source);

                // 计算边权重（基于调用量）
                double weight = 1.0;
                if (edge.getCallCount() != null && edge.getCallCount() > 0) {
                    // 归一化权重，调用量越大权重越高
                    weight = Math.log10(edge.getCallCount() + 1) + 1;
                }
                edgeWeights.get(source).put(target, weight);
            }
        }
    }

    /**
     * 找出主要风险贡献者（上游服务）
     */
    private List<String> findTopContributors(String service, Map<String, List<String>> incoming,
            Map<String, Double> inherentScores, Map<String, Double> propagatedScores) {

        List<String> upstreams = incoming.getOrDefault(service, Collections.emptyList());

        // 按上游服务的总风险分排序
        List<Map.Entry<String, Double>> contributors = new ArrayList<>();
        for (String upstream : upstreams) {
            double total = inherentScores.getOrDefault(upstream, 0.0)
                         + propagatedScores.getOrDefault(upstream, 0.0);
            contributors.add(new AbstractMap.SimpleEntry<>(upstream, total));
        }

        contributors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // 返回前3个
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, contributors.size()); i++) {
            result.add(contributors.get(i).getKey());
        }
        return result;
    }

    /**
     * 构建风险贡献分析
     */
    private RiskContribution buildContribution(String service,
            Map<String, List<String>> incoming, Map<String, List<String>> outgoing,
            Map<String, Double> inherentScores, Map<String, Double> propagatedScores) {

        RiskContribution contribution = new RiskContribution();
        contribution.setServiceName(service);
        contribution.setInDegree(incoming.getOrDefault(service, Collections.emptyList()).size());
        contribution.setOutDegree(outgoing.getOrDefault(service, Collections.emptyList()).size());

        // 上游贡献
        for (String upstream : incoming.getOrDefault(service, Collections.emptyList())) {
            double total = inherentScores.getOrDefault(upstream, 0.0)
                         + propagatedScores.getOrDefault(upstream, 0.0);
            contribution.getUpstreamContributions().put(upstream, total);
        }

        // 下游影响
        double myTotal = inherentScores.getOrDefault(service, 0.0)
                       + propagatedScores.getOrDefault(service, 0.0);
        for (String downstream : outgoing.getOrDefault(service, Collections.emptyList())) {
            contribution.getDownstreamImpacts().put(downstream, myTotal);
        }

        return contribution;
    }

    /**
     * 计算风险等级
     */
    private String calculateRiskLevel(double score) {
        if (score >= 100) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        if (score > 0) return "LOW";
        return "NONE";
    }

    // Setters for algorithm parameters
    public void setDampingFactor(double dampingFactor) {
        this.dampingFactor = dampingFactor;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public void setConvergenceThreshold(double convergenceThreshold) {
        this.convergenceThreshold = convergenceThreshold;
    }

    /**
     * 四舍五入到两位小数
     */
    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

