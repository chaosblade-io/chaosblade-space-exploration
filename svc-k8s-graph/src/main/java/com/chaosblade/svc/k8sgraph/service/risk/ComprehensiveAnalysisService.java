package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.client.LlmClient;
import com.chaosblade.svc.k8sgraph.client.chaosblade.ChaosBladeService;
import com.chaosblade.svc.k8sgraph.client.chaosblade.model.SceneFunction;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.ComprehensiveAnalysisResult.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Phase 5: 综合分析与故障场景生成服务
 *
 * 将三层风险信息（资源层、拓扑层、链路层）综合分析，生成可执行的混沌工程故障场景
 */
@Service
public class ComprehensiveAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveAnalysisService.class);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ChaosBladeService chaosBladeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存的故障类型列表（格式：故障名称:故障代码） */
    private List<String> cachedFaultTypes = new ArrayList<>();

    /** 缓存的故障类型Map（code -> name） */
    private Map<String, String> faultTypeMap = new HashMap<>();
    
    /**
     * 初始化时加载故障类型列表
     */
    @PostConstruct
    public void init() {
        loadFaultTypes();
    }

    /**
     * 加载ChaosBlade Box支持的故障类型
     */
    private void loadFaultTypes() {
        try {
            logger.info("Loading fault types from ChaosBlade Box...");
            List<SceneFunction> functions = chaosBladeService.getAllSceneFunctions();

            cachedFaultTypes.clear();
            faultTypeMap.clear();

            for (SceneFunction func : functions) {
                String code = func.getCode();
                String name = func.getName();
                if (code != null && !code.isEmpty() && name != null && !name.isEmpty()) {
                    cachedFaultTypes.add(name + ":" + code);
                    faultTypeMap.put(code, name);
                }
            }

            logger.info("Loaded {} fault types from ChaosBlade Box", cachedFaultTypes.size());
        } catch (Exception e) {
            logger.warn("Failed to load fault types from ChaosBlade Box: {}", e.getMessage());
            // 使用默认故障类型
            initDefaultFaultTypes();
        }
    }

    /**
     * 初始化默认故障类型（当无法从ChaosBlade Box获取时）
     */
    private void initDefaultFaultTypes() {
        cachedFaultTypes.clear();
        faultTypeMap.clear();

        // 容器CPU相关
        addFaultType("容器内Cpu满载", "chaos.container-cpu.fullload");
        addFaultType("容器内Cpu占用过高", "chaos.container-cpu.load");
        // 容器内存相关
        addFaultType("容器内Mem占用过高", "chaos.container-mem.load");
        addFaultType("容器内Mem使用量(oom)", "chaos.container-mem.oom");
        // 容器网络相关
        addFaultType("容器内网络延迟", "chaos.container-network.delay");
        addFaultType("容器内网络丢包", "chaos.container-network.loss");
        addFaultType("容器内网络DNS", "chaos.container-network.dns");
        // Pod相关
        addFaultType("Pod删除", "chaos.pod.delete");
        addFaultType("Pod故障", "chaos.pod.fail");
        // 进程相关
        addFaultType("容器内进程杀死", "chaos.container-process.kill");
        addFaultType("容器内进程停止", "chaos.container-process.stop");

        logger.info("Initialized {} default fault types", cachedFaultTypes.size());
    }

    private void addFaultType(String name, String code) {
        cachedFaultTypes.add(name + ":" + code);
        faultTypeMap.put(code, name);
    }

    /**
     * 执行综合分析
     *
     * @param namespace 命名空间
     * @param phase1Results Phase 1规则扫描结果
     * @param phase2Result Phase 2拓扑风险结果
     * @param phase3Result Phase 3风险排名结果
     * @param phase4Results Phase 4 Trace分析结果
     * @return 综合分析结果
     */
    public ComprehensiveAnalysisResult analyze(
            String namespace,
            Map<String, ServiceRiskProfile> phase1Results,
            TopologyRiskResult phase2Result,
            RiskRankResult phase3Result,
            Map<String, TraceAnalysisResult> phase4Results) {

        logger.info("Starting comprehensive analysis for namespace: {}", namespace);

        ComprehensiveAnalysisResult result = new ComprehensiveAnalysisResult(namespace);

        try {
            // 确保故障类型已加载
            if (cachedFaultTypes.isEmpty()) {
                loadFaultTypes();
            }

            // 构建包含故障类型的系统提示词
            String systemPrompt = buildSystemPromptWithFaultTypes();

            // 对每个Top N服务进行综合分析
            for (String serviceName : phase4Results.keySet()) {
                logger.info("Analyzing service: {}", serviceName);

                ServiceComprehensiveAnalysis serviceAnalysis = analyzeService(
                    namespace,
                    serviceName,
                    phase1Results.get(serviceName),
                    phase2Result,
                    phase3Result,
                    phase4Results.get(serviceName),
                    systemPrompt
                );

                result.getServiceAnalyses().put(serviceName, serviceAnalysis);
            }

            // 生成全局摘要
            generateGlobalSummary(result, phase1Results, phase3Result);

            // 生成全局改进建议
            generateGlobalRecommendations(result);

        } catch (Exception e) {
            logger.error("Comprehensive analysis failed: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 分析单个服务
     */
    private ServiceComprehensiveAnalysis analyzeService(
            String namespace,
            String serviceName,
            ServiceRiskProfile phase1Profile,
            TopologyRiskResult phase2Result,
            RiskRankResult phase3Result,
            TraceAnalysisResult phase4Result,
            String systemPrompt) {

        // 构建LLM提示词
        String userPrompt = buildServiceAnalysisPrompt(
            namespace, serviceName, phase1Profile, phase2Result, phase3Result, phase4Result);

        // 调用LLM
        String llmResponse = llmClient.chat(systemPrompt, userPrompt);

        // 解析响应
        return parseServiceAnalysis(serviceName, llmResponse, phase1Profile);
    }
    
    /**
     * 构建包含故障类型的系统提示词
     */
    private String buildSystemPromptWithFaultTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深的微服务架构师和混沌工程专家。\n\n");

        sb.append("## 你的任务\n");
        sb.append("基于三层风险信息（资源层、拓扑层、链路层），进行综合分析并生成可执行的混沌工程故障场景。\n\n");

        sb.append("## 分析要求\n");
        sb.append("1. **风险关联分析**：识别风险之间的因果关系，区分根因风险和衍生风险\n");
        sb.append("2. **因果链识别**：找出典型的风险传播模式，如：\n");
        sb.append("   - 单副本部署 → 扇入瓶颈 → 链路错误集中\n");
        sb.append("   - 无资源限制 → 延迟尖刺 → 上游超时\n");
        sb.append("3. **故障场景设计**：遵循以下原则\n");
        sb.append("   - 风险驱动：每个场景针对具体的已识别风险\n");
        sb.append("   - 渐进式：从轻微故障开始，逐步加大强度\n");
        sb.append("   - 可观测：明确的成功/失败判定标准\n");
        sb.append("   - 可回滚：有清晰的故障终止和恢复方案\n\n");

        // 添加ChaosBlade Box支持的故障类型列表
        sb.append("## 【重要】可用的故障类型列表\n");
        sb.append("以下是ChaosBlade Box平台支持的故障类型，格式为 `故障名称:故障代码`\n");
        sb.append("**你必须从以下列表中选择故障类型，不要使用列表之外的故障类型！**\n\n");
        sb.append("```\n");
        for (String faultType : cachedFaultTypes) {
            sb.append(faultType).append("\n");
        }
        sb.append("```\n\n");

        sb.append("## 风险与故障类型的推荐映射\n");
        sb.append("| 识别的风险 | 推荐的故障代码 |\n");
        sb.append("|-----------|---------------|\n");
        sb.append("| 单副本部署/可用性风险 | chaos.pod.delete, chaos.pod.fail |\n");
        sb.append("| 无资源限制/CPU相关 | chaos.container-cpu.fullload, chaos.container-cpu.load |\n");
        sb.append("| 无资源限制/内存相关 | chaos.container-mem.load, chaos.container-mem.oom |\n");
        sb.append("| 网络调用链长/网络依赖 | chaos.container-network.delay, chaos.container-network.loss |\n");
        sb.append("| 高错误率/进程问题 | chaos.container-process.kill, chaos.container-process.stop |\n\n");

        sb.append("## 严格按以下JSON格式输出\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"overallRiskLevel\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n");
        sb.append("  \"overallRiskScore\": 85,\n");
        sb.append("  \"topRisks\": [\n");
        sb.append("    {\n");
        sb.append("      \"riskId\": \"RISK_001\",\n");
        sb.append("      \"layer\": \"RESOURCE|TOPOLOGY|TRACE\",\n");
        sb.append("      \"name\": \"风险名称\",\n");
        sb.append("      \"description\": \"风险描述\",\n");
        sb.append("      \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n");
        sb.append("      \"score\": 30,\n");
        sb.append("      \"isRootCause\": true,\n");
        sb.append("      \"causesRiskIds\": [\"RISK_002\"],\n");
        sb.append("      \"causedByRiskId\": null,\n");
        sb.append("      \"relatedScenarioIds\": [\"CHAOS_001\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"causalChains\": [\n");
        sb.append("    {\n");
        sb.append("      \"chainId\": \"CHAIN_001\",\n");
        sb.append("      \"description\": \"因果链描述\",\n");
        sb.append("      \"riskSequence\": [\"RISK_001\", \"RISK_002\", \"RISK_003\"],\n");
        sb.append("      \"impact\": \"最终影响描述\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"chaosScenarios\": [\n");
        sb.append("    {\n");
        sb.append("      \"scenarioId\": \"CHAOS_001\",\n");
        sb.append("      \"name\": \"场景名称\",\n");
        sb.append("      \"code\": \"chaos.container-cpu.fullload\",\n");
        sb.append("      \"faultName\": \"容器内Cpu满载\",\n");
        sb.append("      \"targetRiskId\": \"RISK_001\",\n");
        sb.append("      \"type\": \"CPU_STRESS\",\n");
        sb.append("      \"objective\": \"验证目标\",\n");
        sb.append("      \"intensity\": 3,\n");
        sb.append("      \"steps\": [\"步骤1\", \"步骤2\"],\n");
        sb.append("      \"expectedImpact\": \"预期影响\",\n");
        sb.append("      \"successCriteria\": [\"成功标准1\"],\n");
        sb.append("      \"rollbackPlan\": \"回滚方案\",\n");
        sb.append("      \"durationSeconds\": 60\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"recommendations\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"REC_001\",\n");
        sb.append("      \"title\": \"建议标题\",\n");
        sb.append("      \"priority\": 1,\n");
        sb.append("      \"relatedRiskIds\": [\"RISK_001\"],\n");
        sb.append("      \"description\": \"详细描述\",\n");
        sb.append("      \"actionItems\": [\"行动项1\", \"行动项2\"],\n");
        sb.append("      \"expectedBenefit\": \"预期收益\",\n");
        sb.append("      \"implementationCost\": \"LOW|MEDIUM|HIGH\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");

        sb.append("## 注意事项\n");
        sb.append("1. **code字段必须填写**：每个chaosScenario必须包含从故障类型列表中选择的code值\n");
        sb.append("2. **faultName字段必须填写**：对应code的故障名称\n");
        sb.append("3. **relatedScenarioIds字段**：每个risk需要关联对应的场景ID列表\n");
        sb.append("4. **只使用列表中的故障代码**：不要编造不存在的故障类型\n");

        return sb.toString();
    }

    /**
     * 构建服务分析提示词
     */
    private String buildServiceAnalysisPrompt(
            String namespace,
            String serviceName,
            ServiceRiskProfile phase1Profile,
            TopologyRiskResult phase2Result,
            RiskRankResult phase3Result,
            TraceAnalysisResult phase4Result) {

        StringBuilder sb = new StringBuilder();
        sb.append("## 分析目标\n");
        sb.append("- 命名空间: ").append(namespace).append("\n");
        sb.append("- 服务名称: ").append(serviceName).append("\n\n");

        // Phase 1: 资源层风险
        sb.append("### 一、资源层风险（Phase 1: K8s配置扫描）\n");
        if (phase1Profile != null) {
            sb.append("总分: ").append(phase1Profile.getTotalScore()).append("\n");
            sb.append("风险等级: ").append(phase1Profile.getRiskLevel()).append("\n");
            sb.append("触发的规则:\n```json\n");
            sb.append(formatTriggeredRules(phase1Profile));
            sb.append("\n```\n\n");
        } else {
            sb.append("无Phase 1数据\n\n");
        }

        // Phase 2: 拓扑层风险
        sb.append("### 二、拓扑层风险（Phase 2: 服务调用拓扑分析）\n");
        if (phase2Result != null) {
            List<TopologyRiskResult.TopologyRisk> relatedRisks = findRelatedTopoRisks(serviceName, phase2Result);
            if (!relatedRisks.isEmpty()) {
                sb.append("```json\n");
                sb.append(formatTopoRisks(relatedRisks));
                sb.append("\n```\n\n");
            } else {
                sb.append("该服务无直接相关的拓扑风险\n\n");
            }
        } else {
            sb.append("无Phase 2数据\n\n");
        }

        // Phase 3: 风险来源分析
        sb.append("### 三、风险来源分析（Phase 3: RiskRank算法）\n");
        if (phase3Result != null) {
            RiskRankResult.RankedService rankedService = findRankedService(serviceName, phase3Result);
            if (rankedService != null) {
                sb.append("综合排名: ").append(rankedService.getRank()).append("\n");
                sb.append("RiskRank分数: ").append(String.format("%.2f", rankedService.getRiskRankScore())).append("\n");
                sb.append("分数来源:\n");
                sb.append("  - 固有风险分: ").append(String.format("%.2f", rankedService.getInherentRiskScore()));
                sb.append(" (占比: ").append(String.format("%.1f%%", rankedService.getInherentRiskRatio() * 100)).append(")\n");
                sb.append("  - 传播风险分: ").append(String.format("%.2f", rankedService.getPropagatedRiskScore()));
                sb.append(" (占比: ").append(String.format("%.1f%%", rankedService.getPropagatedRiskRatio() * 100)).append(")\n");
                if (rankedService.getTopContributors() != null && !rankedService.getTopContributors().isEmpty()) {
                    sb.append("  - 主要风险贡献者: ").append(String.join(", ", rankedService.getTopContributors())).append("\n");
                }
                sb.append("\n");
            }
        }

        // Phase 4: 链路层风险
        sb.append("### 四、链路层风险（Phase 4: Trace分析）\n");
        if (phase4Result != null) {
            sb.append("分析的API数量: ").append(phase4Result.getApiSummaries().size()).append("\n");
            sb.append("选中的Trace数量: ").append(phase4Result.getSelectedTraces().size()).append("\n");
            sb.append("发现的问题数量: ").append(phase4Result.getIssues().size()).append("\n");

            if (!phase4Result.getIssues().isEmpty()) {
                sb.append("问题详情:\n```json\n");
                sb.append(formatTraceIssues(phase4Result.getIssues()));
                sb.append("\n```\n\n");
            }

            if (!phase4Result.getApiSummaries().isEmpty()) {
                sb.append("API性能摘要:\n```json\n");
                sb.append(formatApiSummaries(phase4Result.getApiSummaries()));
                sb.append("\n```\n\n");
            }
        } else {
            sb.append("无Phase 4数据\n\n");
        }

        sb.append("## 请基于以上三层风险信息，进行综合分析并生成故障场景。\n");
        sb.append("要求：\n");
        sb.append("1. 识别Top 3主要风险及其因果关系\n");
        sb.append("2. 找出风险因果链\n");
        sb.append("3. 为每个主要风险设计1-2个混沌工程故障场景\n");
        sb.append("4. 给出按优先级排序的改进建议\n");
        sb.append("5. 严格按系统提示中的JSON格式输出\n");

        return sb.toString();
    }

    // ==================== 辅助格式化方法 ====================

    private String formatTriggeredRules(ServiceRiskProfile profile) {
        try {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (TriggeredRule rule : profile.getTriggeredRules()) {
                Map<String, Object> ruleMap = new LinkedHashMap<>();
                ruleMap.put("ruleId", rule.getRuleId());
                ruleMap.put("name", rule.getRuleName());
                ruleMap.put("category", rule.getCategory());
                ruleMap.put("score", rule.getScore());
                ruleMap.put("severity", rule.getSeverity());
                rules.add(ruleMap);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<TopologyRiskResult.TopologyRisk> findRelatedTopoRisks(String serviceName, TopologyRiskResult result) {
        List<TopologyRiskResult.TopologyRisk> related = new ArrayList<>();
        for (TopologyRiskResult.TopologyRisk risk : result.getRisks()) {
            if (risk.getAffectedServices() != null && risk.getAffectedServices().contains(serviceName)) {
                related.add(risk);
            }
        }
        return related;
    }

    private String formatTopoRisks(List<TopologyRiskResult.TopologyRisk> risks) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TopologyRiskResult.TopologyRisk risk : risks) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("riskId", risk.getRiskId());
                map.put("name", risk.getName());
                map.put("category", risk.getCategory());
                map.put("score", risk.getScore());
                map.put("severity", risk.getSeverity());
                map.put("description", risk.getDescription());
                list.add(map);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private RiskRankResult.RankedService findRankedService(String serviceName, RiskRankResult result) {
        for (RiskRankResult.RankedService rs : result.getRankedServices()) {
            if (serviceName.equals(rs.getServiceName())) {
                return rs;
            }
        }
        return null;
    }

    private String formatTraceIssues(List<TraceIssue> issues) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TraceIssue issue : issues) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("issueType", issue.getIssueType());
                map.put("severity", issue.getSeverity());
                map.put("description", issue.getDescription());
                map.put("spanName", issue.getSpanName());
                map.put("rootCause", issue.getRootCause());
                list.add(map);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String formatApiSummaries(List<TraceAnalysisResult.ApiTraceSummary> summaries) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TraceAnalysisResult.ApiTraceSummary summary : summaries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("spanName", summary.getSpanName());
                map.put("totalRps", summary.getTotalRps());
                map.put("failedRps", summary.getFailedRps());
                map.put("p50Latency", summary.getP50Latency());
                map.put("p99Latency", summary.getP99Latency());
                list.add(map);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ==================== LLM响应解析 ====================

    private ServiceComprehensiveAnalysis parseServiceAnalysis(String serviceName, String llmResponse,
            ServiceRiskProfile phase1Profile) {
        ServiceComprehensiveAnalysis analysis = new ServiceComprehensiveAnalysis();
        analysis.setServiceName(serviceName);

        try {
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            // 解析风险等级和分数
            String levelStr = root.path("overallRiskLevel").asText("MEDIUM");
            analysis.setOverallRiskLevel(RiskLevel.valueOf(levelStr));
            analysis.setOverallRiskScore(root.path("overallRiskScore").asDouble(50.0));

            // 解析Top风险（先收集场景ID到风险的映射，后续关联场景对象）
            Map<String, List<String>> riskToScenarioIds = new HashMap<>();
            JsonNode topRisksNode = root.path("topRisks");
            if (topRisksNode.isArray()) {
                for (JsonNode riskNode : topRisksNode) {
                    RiskWithCausality risk = new RiskWithCausality();
                    risk.setRiskId(riskNode.path("riskId").asText());
                    risk.setLayer(parseRiskLayer(riskNode.path("layer").asText()));
                    risk.setName(riskNode.path("name").asText());
                    risk.setDescription(riskNode.path("description").asText());
                    risk.setSeverity(riskNode.path("severity").asText());
                    risk.setScore(riskNode.path("score").asDouble());
                    risk.setRootCause(riskNode.path("isRootCause").asBoolean(false));
                    risk.setCausedByRiskId(riskNode.path("causedByRiskId").asText(null));

                    JsonNode causesNode = riskNode.path("causesRiskIds");
                    if (causesNode.isArray()) {
                        List<String> causes = new ArrayList<>();
                        for (JsonNode c : causesNode) {
                            causes.add(c.asText());
                        }
                        risk.setCausesRiskIds(causes);
                    }

                    // 解析关联的场景ID列表
                    JsonNode relatedScenarioIdsNode = riskNode.path("relatedScenarioIds");
                    if (relatedScenarioIdsNode.isArray()) {
                        List<String> scenarioIds = new ArrayList<>();
                        for (JsonNode s : relatedScenarioIdsNode) {
                            scenarioIds.add(s.asText());
                        }
                        riskToScenarioIds.put(risk.getRiskId(), scenarioIds);
                    }

                    analysis.getTopRisks().add(risk);
                }
            }

            // 解析因果链
            JsonNode chainsNode = root.path("causalChains");
            if (chainsNode.isArray()) {
                for (JsonNode chainNode : chainsNode) {
                    RiskCausalChain chain = new RiskCausalChain();
                    chain.setChainId(chainNode.path("chainId").asText());
                    chain.setDescription(chainNode.path("description").asText());
                    chain.setImpact(chainNode.path("impact").asText());

                    JsonNode seqNode = chainNode.path("riskSequence");
                    if (seqNode.isArray()) {
                        List<String> seq = new ArrayList<>();
                        for (JsonNode s : seqNode) {
                            seq.add(s.asText());
                        }
                        chain.setRiskSequence(seq);
                    }

                    analysis.getCausalChains().add(chain);
                }
            }

            // 解析故障场景，并构建场景ID到场景对象的映射
            Map<String, ChaosScenario> scenarioMap = new HashMap<>();
            JsonNode scenariosNode = root.path("chaosScenarios");
            if (scenariosNode.isArray()) {
                for (JsonNode scenarioNode : scenariosNode) {
                    ChaosScenario scenario = new ChaosScenario();
                    scenario.setScenarioId(scenarioNode.path("scenarioId").asText());
                    scenario.setName(scenarioNode.path("name").asText());

                    // 解析新增的code和faultName字段
                    String code = scenarioNode.path("code").asText(null);
                    scenario.setCode(code);
                    String faultName = scenarioNode.path("faultName").asText(null);
                    // 如果LLM没有返回faultName，尝试从缓存的映射中获取
                    if ((faultName == null || faultName.isEmpty()) && code != null) {
                        faultName = faultTypeMap.get(code);
                    }
                    scenario.setFaultName(faultName);

                    scenario.setTargetRiskId(scenarioNode.path("targetRiskId").asText());
                    scenario.setType(parseScenarioType(scenarioNode.path("type").asText()));
                    scenario.setObjective(scenarioNode.path("objective").asText());
                    scenario.setIntensity(scenarioNode.path("intensity").asInt(3));
                    scenario.setExpectedImpact(scenarioNode.path("expectedImpact").asText());
                    scenario.setRollbackPlan(scenarioNode.path("rollbackPlan").asText());
                    scenario.setChaosBladeCommand(scenarioNode.path("chaosBladeCommand").asText());
                    scenario.setDurationSeconds(scenarioNode.path("durationSeconds").asInt(60));

                    JsonNode stepsNode = scenarioNode.path("steps");
                    if (stepsNode.isArray()) {
                        List<String> steps = new ArrayList<>();
                        for (JsonNode s : stepsNode) {
                            steps.add(s.asText());
                        }
                        scenario.setSteps(steps);
                    }

                    JsonNode criteriaNode = scenarioNode.path("successCriteria");
                    if (criteriaNode.isArray()) {
                        List<String> criteria = new ArrayList<>();
                        for (JsonNode c : criteriaNode) {
                            criteria.add(c.asText());
                        }
                        scenario.setSuccessCriteria(criteria);
                    }

                    analysis.getChaosScenarios().add(scenario);
                    scenarioMap.put(scenario.getScenarioId(), scenario);
                }
            }

            // 将场景对象关联到对应的风险
            for (RiskWithCausality risk : analysis.getTopRisks()) {
                List<String> scenarioIds = riskToScenarioIds.get(risk.getRiskId());
                if (scenarioIds != null) {
                    List<ChaosScenario> relatedScenarios = new ArrayList<>();
                    for (String scenarioId : scenarioIds) {
                        ChaosScenario scenario = scenarioMap.get(scenarioId);
                        if (scenario != null) {
                            relatedScenarios.add(scenario);
                        }
                    }
                    risk.setRelatedScenarios(relatedScenarios);
                }
            }

            // 解析改进建议
            JsonNode recsNode = root.path("recommendations");
            if (recsNode.isArray()) {
                for (JsonNode recNode : recsNode) {
                    Recommendation rec = new Recommendation();
                    rec.setId(recNode.path("id").asText());
                    rec.setTitle(recNode.path("title").asText());
                    rec.setPriority(recNode.path("priority").asInt(5));
                    rec.setDescription(recNode.path("description").asText());
                    rec.setExpectedBenefit(recNode.path("expectedBenefit").asText());
                    rec.setImplementationCost(recNode.path("implementationCost").asText("MEDIUM"));

                    JsonNode riskIdsNode = recNode.path("relatedRiskIds");
                    if (riskIdsNode.isArray()) {
                        List<String> ids = new ArrayList<>();
                        for (JsonNode id : riskIdsNode) {
                            ids.add(id.asText());
                        }
                        rec.setRelatedRiskIds(ids);
                    }

                    JsonNode actionsNode = recNode.path("actionItems");
                    if (actionsNode.isArray()) {
                        List<String> actions = new ArrayList<>();
                        for (JsonNode a : actionsNode) {
                            actions.add(a.asText());
                        }
                        rec.setActionItems(actions);
                    }

                    analysis.getRecommendations().add(rec);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse LLM response for service {}: {}", serviceName, e.getMessage());
            // 设置默认值
            if (phase1Profile != null) {
                analysis.setOverallRiskLevel(parseRiskLevelFromString(phase1Profile.getRiskLevel()));
                analysis.setOverallRiskScore(phase1Profile.getTotalScore());
            } else {
                analysis.setOverallRiskLevel(RiskLevel.MEDIUM);
                analysis.setOverallRiskScore(50.0);
            }
        }

        return analysis;
    }

    private RiskLayer parseRiskLayer(String layer) {
        try {
            return RiskLayer.valueOf(layer);
        } catch (Exception e) {
            return RiskLayer.RESOURCE;
        }
    }

    private ScenarioType parseScenarioType(String type) {
        try {
            return ScenarioType.valueOf(type);
        } catch (Exception e) {
            return ScenarioType.POD_KILL;
        }
    }

    private RiskLevel parseRiskLevelFromString(String level) {
        try {
            return RiskLevel.valueOf(level);
        } catch (Exception e) {
            return RiskLevel.MEDIUM;
        }
    }

    private String extractJson(String response) {
        // 尝试提取```json ... ```块
        int start = response.indexOf("```json");
        if (start != -1) {
            start = response.indexOf("\n", start) + 1;
            int end = response.indexOf("```", start);
            if (end != -1) {
                return response.substring(start, end).trim();
            }
        }

        // 尝试提取{ ... }
        start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return response;
    }

    // ==================== 全局摘要生成 ====================

    private void generateGlobalSummary(ComprehensiveAnalysisResult result,
            Map<String, ServiceRiskProfile> phase1Results,
            RiskRankResult phase3Result) {

        GlobalSummary summary = new GlobalSummary();

        // 统计各风险等级的服务数量
        int critical = 0, high = 0, medium = 0, low = 0;
        int totalRisks = 0;
        int rootCauses = 0;
        int scenarios = 0;

        for (ServiceComprehensiveAnalysis serviceAnalysis : result.getServiceAnalyses().values()) {
            switch (serviceAnalysis.getOverallRiskLevel()) {
                case CRITICAL: critical++; break;
                case HIGH: high++; break;
                case MEDIUM: medium++; break;
                case LOW: low++; break;
            }

            totalRisks += serviceAnalysis.getTopRisks().size();
            scenarios += serviceAnalysis.getChaosScenarios().size();

            for (RiskWithCausality risk : serviceAnalysis.getTopRisks()) {
                if (risk.isRootCause()) {
                    rootCauses++;
                }
            }
        }

        summary.setCriticalServiceCount(critical);
        summary.setHighRiskServiceCount(high);
        summary.setMediumRiskServiceCount(medium);
        summary.setLowRiskServiceCount(low);
        summary.setTotalServicesAnalyzed(result.getServiceAnalyses().size());
        summary.setTotalRisksIdentified(totalRisks);
        summary.setRootCauseRiskCount(rootCauses);
        summary.setTotalScenariosGenerated(scenarios);

        // 计算整体健康度（100 - 加权风险占比）
        int totalServices = phase1Results != null ? phase1Results.size() : result.getServiceAnalyses().size();
        double riskRatio = (double)(critical * 4 + high * 3 + medium * 2 + low) / (totalServices * 4);
        summary.setOverallHealthScore(Math.max(0, 100 - riskRatio * 100));

        // 生成关键发现
        List<String> findings = new ArrayList<>();
        if (critical > 0) {
            findings.add(String.format("发现 %d 个严重风险服务，需要立即关注", critical));
        }
        if (high > 0) {
            findings.add(String.format("发现 %d 个高风险服务，建议优先处理", high));
        }
        if (rootCauses > 0) {
            findings.add(String.format("识别出 %d 个根因风险，修复这些风险可以减少衍生问题", rootCauses));
        }
        if (scenarios > 0) {
            findings.add(String.format("生成了 %d 个混沌工程故障场景，可用于验证系统弹性", scenarios));
        }
        summary.setKeyFindings(findings);

        result.setGlobalSummary(summary);
    }

    private void generateGlobalRecommendations(ComprehensiveAnalysisResult result) {
        // 聚合所有服务的建议，按优先级排序并去重
        Map<String, Recommendation> recMap = new LinkedHashMap<>();

        for (ServiceComprehensiveAnalysis serviceAnalysis : result.getServiceAnalyses().values()) {
            for (Recommendation rec : serviceAnalysis.getRecommendations()) {
                String key = rec.getTitle();
                if (!recMap.containsKey(key) || recMap.get(key).getPriority() > rec.getPriority()) {
                    recMap.put(key, rec);
                }
            }
        }

        // 按优先级排序
        List<Recommendation> sorted = new ArrayList<>(recMap.values());
        sorted.sort(Comparator.comparingInt(Recommendation::getPriority));

        // 取Top 10作为全局建议
        result.setGlobalRecommendations(sorted.subList(0, Math.min(10, sorted.size())));
    }
}

