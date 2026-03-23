package com.chaosblade.svc.k8sgraph.service.experiment;

import com.chaosblade.svc.k8sgraph.client.LlmClient;
import com.chaosblade.svc.k8sgraph.client.chaosblade.ChaosBladeService;
import com.chaosblade.svc.k8sgraph.client.chaosblade.model.ApplicationFullInfo;
import com.chaosblade.svc.k8sgraph.client.chaosblade.model.ScopeInfo;
import com.chaosblade.svc.k8sgraph.domain.experiment.*;
import com.chaosblade.svc.k8sgraph.domain.experiment.ExperimentConfig.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.ComprehensiveAnalysisResult.ChaosScenario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实验配置服务
 *
 * 负责将ChaosScenario（LLM生成的概念性场景）转换为ExperimentConfig（可执行的ChaosBlade Box配置）
 */
@Service
public class ExperimentConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentConfigService.class);

    @Autowired
    private ChaosBladeService chaosBladeService;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 将ChaosScenario转换为ExperimentConfig
     * 
     * @param scenario LLM生成的场景描述
     * @param serviceName 目标服务名称
     * @param namespace K8s命名空间
     * @param faultParams 故障参数（由LLM生成，如cpu-percent=80）
     * @return 可执行的实验配置
     */
    public ExperimentConfig buildFromScenario(
            ChaosScenario scenario,
            String serviceName,
            String namespace,
            Map<String, String> faultParams) {
        
        logger.info("Building ExperimentConfig for scenario: {}, service: {}", 
            scenario.getName(), serviceName);
        
        // 1. 获取应用信息
        ApplicationFullInfo appInfo = chaosBladeService.getApplicationFullInfo(serviceName);
        if (appInfo == null) {
            logger.error("Application not found: {}", serviceName);
            throw new RuntimeException("Application not found: " + serviceName);
        }
        
        // 2. 构建基本配置
        ExperimentConfig config = new ExperimentConfig();
        config.setName(scenario.getName());
        config.setDescription(scenario.getObjective());
        
        // 3. 构建Definition
        ExperimentDefinition definition = new ExperimentDefinition();
        definition.setDuration(scenario.getDurationSeconds());
        
        // 4. 构建FlowGroup
        FlowGroup flowGroup = buildFlowGroup(
            appInfo, 
            scenario, 
            namespace, 
            faultParams
        );
        definition.getFlowGroups().add(flowGroup);
        
        config.setDefinition(definition);
        
        logger.info("ExperimentConfig built successfully for scenario: {}", scenario.getName());
        return config;
    }
    
    /**
     * 构建FlowGroup
     */
    private FlowGroup buildFlowGroup(
            ApplicationFullInfo appInfo,
            ChaosScenario scenario,
            String namespace,
            Map<String, String> faultParams) {
        
        FlowGroup group = new FlowGroup();
        group.setAppName(appInfo.getApplication().getAppName());
        group.setAppId(appInfo.getApplication().getAppId());
        group.setAppGroups(appInfo.getGroups());
        group.setGroupName(appInfo.getGroups().isEmpty() ? "default" : appInfo.getGroups().get(0));
        
        // 构建Hosts
        for (ScopeInfo scope : appInfo.getScopes()) {
            // 过滤：只选择目标namespace的Pod
            if (namespace.equals(scope.getKubNamespace())) {
                ExperimentHost host = buildHost(appInfo, scope);
                group.getHosts().add(host);
            }
        }
        
        // 如果没有匹配的host，使用所有可用的
        if (group.getHosts().isEmpty() && !appInfo.getScopes().isEmpty()) {
            logger.warn("No hosts found in namespace {}, using all available hosts", namespace);
            for (ScopeInfo scope : appInfo.getScopes()) {
                ExperimentHost host = buildHost(appInfo, scope);
                group.getHosts().add(host);
            }
        }
        
        // 构建Flow
        Flow flow = buildFlow(scenario, namespace, faultParams, appInfo);
        group.getFlows().add(flow);
        
        return group;
    }
    
    /**
     * 构建Host
     */
    private ExperimentHost buildHost(ApplicationFullInfo appInfo, ScopeInfo scope) {
        ExperimentHost host = new ExperimentHost();
        host.setApp(appInfo.getApplication().getAppName());
        host.setAppId(appInfo.getApplication().getAppId());
        host.setAppConfigurationId(scope.getAppConfigurationId());
        host.setDeviceName(scope.getDeviceName());
        host.setDeviceId(scope.getDeviceId());
        host.setDeviceConfigurationId(scope.getDeviceConfigurationId());
        host.setIp(scope.getIp());
        host.setPrivateIp(scope.getPrivateIp());
        host.setTargetIp(scope.getIp());
        host.setKubNamespace(scope.getKubNamespace());
        host.setClusterName(scope.getClusterName());
        host.setClusterId(scope.getClusterId());
        host.setNodeGroup(scope.getNodeGroup());
        host.setPort(scope.getPort());
        host.setVpcId(scope.getVpcId());
        return host;
    }

    /**
     * 构建Flow（包含attack和recover）
     */
    private Flow buildFlow(
            ChaosScenario scenario,
            String namespace,
            Map<String, String> faultParams,
            ApplicationFullInfo appInfo) {

        Flow flow = new Flow();
        String flowId = UUID.randomUUID().toString();
        flow.setId(flowId);

        String faultCode = scenario.getCode();
        String faultName = scenario.getFaultName();

        // 构建Attack活动
        String attackId = UUID.randomUUID().toString();
        Activity attack = Activity.createAttackActivity(attackId, flowId, faultName, faultCode);

        // 获取并填充参数模版（如果faultParams为空，调用LLM生成）
        Map<String, String> finalFaultParams = faultParams;
        if (finalFaultParams == null || finalFaultParams.isEmpty()) {
            finalFaultParams = generateFaultParamsWithLLM(scenario, faultCode, appInfo.getGroups());
        }

        List<Object> arguments = buildFaultArguments(
            faultCode,
            namespace,
            finalFaultParams,
            appInfo
        );
        attack.setArguments(arguments);

        flow.getAttack().add(attack);

        // 构建Recover活动
        String recoverId = UUID.randomUUID().toString();
        Activity recover = Activity.createRecoverActivity(recoverId, flowId, faultName, faultCode);
        // recover的arguments为空
        recover.setArguments(new ArrayList<>());

        flow.getRecover().add(recover);

        return flow;
    }

    /**
     * 构建故障参数
     *
     * 1. 从ChaosBlade Box获取参数模版
     * 2. 填充Fault Configuration参数（由LLM生成）
     * 3. 填充Sphere of Influence参数（namespace, container-names）
     * 4. 保持General Configuration默认值
     */
    private List<Object> buildFaultArguments(
            String faultCode,
            String namespace,
            Map<String, String> faultParams,
            ApplicationFullInfo appInfo) {

        List<Object> result = new ArrayList<>();

        // 获取参数模版
        List<String> nodeGroups = appInfo.getGroups();
        JsonNode template = chaosBladeService.getSceneArgumentsTemplate(faultCode, nodeGroups);

        if (template == null || !template.isArray()) {
            logger.warn("Failed to get arguments template for {}, using empty arguments", faultCode);
            return result;
        }

        // 遍历参数组
        for (JsonNode groupNode : template) {
            ObjectNode group = groupNode.deepCopy();
            String gradeName = group.path("gradeName").asText();

            ArrayNode argumentList = (ArrayNode) group.path("argumentList");
            if (argumentList == null || !argumentList.isArray()) {
                result.add(objectMapper.convertValue(group, Map.class));
                continue;
            }

            // 根据分组类型填充参数
            for (int i = 0; i < argumentList.size(); i++) {
                ObjectNode arg = (ObjectNode) argumentList.get(i);
                String paramName = arg.path("name").asText();

                if (gradeName.contains("Fault Configuration")) {
                    // 故障配置：优先使用LLM生成的参数，否则使用默认值
                    if (faultParams != null && faultParams.containsKey(paramName)) {
                        arg.put("value", faultParams.get(paramName));
                        arg.put("state", true);
                    } else {
                        // LLM未提供参数，使用模板默认值或通用默认值
                        String defaultValue = getDefaultFaultParamValue(faultCode, paramName, arg);
                        if (defaultValue != null && !defaultValue.isEmpty()) {
                            arg.put("value", defaultValue);
                            arg.put("state", true);
                            logger.info("Using default value for param {}: {}", paramName, defaultValue);
                        }
                    }
                } else if (gradeName.contains("Sphere of Influence")) {
                    // 影响范围：填充namespace和container-names
                    fillSphereOfInfluenceParam(arg, paramName, namespace, appInfo);
                }
                // General Configuration保持默认值
            }

            result.add(objectMapper.convertValue(group, Map.class));
        }

        return result;
    }

    /**
     * 填充影响范围参数
     */
    private void fillSphereOfInfluenceParam(
            ObjectNode arg,
            String paramName,
            String namespace,
            ApplicationFullInfo appInfo) {

        switch (paramName) {
            case "namespace":
                arg.put("value", namespace);
                arg.put("state", true);
                break;
            case "container-names":
                // 获取第一个Pod的容器名（通常与服务名相同或相似）
                String containerName = extractContainerName(appInfo);
                if (containerName != null) {
                    arg.put("value", containerName);
                    arg.put("state", true);
                }
                break;
            case "names":
                // Pod名称，可以从scopes中获取
                if (!appInfo.getScopes().isEmpty()) {
                    String podName = appInfo.getScopes().get(0).getDeviceName();
                    arg.put("value", podName);
                    arg.put("state", true);
                }
                break;
            // 其他参数保持默认
        }
    }

    /**
     * 从应用信息中提取容器名称
     */
    private String extractContainerName(ApplicationFullInfo appInfo) {
        // 通常容器名与应用名相同
        String appName = appInfo.getApplication().getAppName();

        // 如果有Pod信息，尝试从Pod名称提取
        if (!appInfo.getScopes().isEmpty()) {
            String podName = appInfo.getScopes().get(0).getDeviceName();
            // Pod名称格式通常是: {deployment}-{hash}-{hash}
            // 容器名通常是deployment名称的一部分
            if (podName != null && podName.contains(appName)) {
                return appName;
            }
        }

        return appName;
    }

    /**
     * 调用LLM生成故障参数值
     *
     * @param scenario 场景描述（包含场景名称、目标、强度等信息）
     * @param faultCode 故障代码
     * @param nodeGroups 节点分组
     * @return 生成的参数Map（参数名 -> 参数值）
     */
    private Map<String, String> generateFaultParamsWithLLM(
            ChaosScenario scenario,
            String faultCode,
            List<String> nodeGroups) {

        logger.info("Generating fault params with LLM for scenario: {}, faultCode: {}",
            scenario.getName(), faultCode);

        // 1. 获取参数模板，提取Fault Configuration参数信息
        JsonNode template = chaosBladeService.getSceneArgumentsTemplate(faultCode, nodeGroups);
        if (template == null || !template.isArray()) {
            logger.warn("Failed to get template for {}, using default params", faultCode);
            return getDefaultFaultParams(faultCode);
        }

        // 2. 提取Fault Configuration参数列表
        List<Map<String, String>> paramInfoList = new ArrayList<>();
        for (JsonNode groupNode : template) {
            String gradeName = groupNode.path("gradeName").asText();
            if (gradeName.contains("Fault Configuration")) {
                JsonNode argumentList = groupNode.path("argumentList");
                if (argumentList.isArray()) {
                    for (JsonNode arg : argumentList) {
                        Map<String, String> paramInfo = new HashMap<>();
                        paramInfo.put("name", arg.path("name").asText());
                        paramInfo.put("alias", arg.path("alias").asText());
                        paramInfo.put("description", arg.path("description").asText());
                        paramInfo.put("required", String.valueOf(
                            arg.path("component").path("required").asBoolean(false)));
                        paramInfo.put("defaultValue",
                            arg.path("component").path("defaultValue").asText(""));
                        paramInfoList.add(paramInfo);
                    }
                }
            }
        }

        if (paramInfoList.isEmpty()) {
            logger.info("No Fault Configuration params found for {}", faultCode);
            return new HashMap<>();
        }

        // 3. 构建LLM提示词
        String prompt = buildFaultParamsPrompt(scenario, faultCode, paramInfoList);

        // 4. 调用LLM
        if (!llmClient.isConfigured()) {
            logger.warn("LLM not configured, using default fault params");
            return getDefaultFaultParams(faultCode);
        }

        try {
            String systemPrompt = buildFaultParamsSystemPrompt();
            String llmResponse = llmClient.chat(prompt, systemPrompt);

            if (llmResponse != null && !llmResponse.isEmpty()) {
                Map<String, String> params = parseFaultParamsResponse(llmResponse);
                logger.info("LLM generated fault params: {}", params);
                return params;
            }
        } catch (Exception e) {
            logger.error("LLM call failed for fault params generation: {}", e.getMessage());
        }

        // 5. 回退到默认值
        logger.warn("Using default fault params for {}", faultCode);
        return getDefaultFaultParams(faultCode);
    }

    /**
     * 构建生成故障参数的系统提示词
     */
    private String buildFaultParamsSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个混沌工程专家，负责为故障注入场景生成合适的参数值。\n\n");
        sb.append("你的任务是：根据场景描述和参数列表，为每个Fault Configuration参数生成合理的值。\n\n");
        sb.append("要求：\n");
        sb.append("1. 根据场景的intensity（强度1-5）调整参数值，强度越高参数值越极端\n");
        sb.append("2. 所有参数都必须有值，不能为空\n");
        sb.append("3. 参数值必须是字符串格式\n");
        sb.append("4. 输出严格的JSON格式\n\n");
        sb.append("输出格式：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"参数名1\": \"值1\",\n");
        sb.append("  \"参数名2\": \"值2\"\n");
        sb.append("}\n");
        sb.append("```\n");
        return sb.toString();
    }

    /**
     * 构建生成故障参数的用户提示词
     */
    private String buildFaultParamsPrompt(
            ChaosScenario scenario,
            String faultCode,
            List<Map<String, String>> paramInfoList) {

        StringBuilder sb = new StringBuilder();
        sb.append("## 场景信息\n");
        sb.append("- 场景名称: ").append(scenario.getName()).append("\n");
        sb.append("- 故障代码: ").append(faultCode).append("\n");
        sb.append("- 故障类型: ").append(scenario.getFaultName()).append("\n");
        sb.append("- 场景目标: ").append(scenario.getObjective()).append("\n");
        sb.append("- 故障强度: ").append(scenario.getIntensity()).append("/5\n");
        sb.append("- 执行时长: ").append(scenario.getDurationSeconds()).append("秒\n");
        sb.append("- 预期影响: ").append(scenario.getExpectedImpact()).append("\n\n");

        sb.append("## 需要配置的参数\n");
        sb.append("| 参数名 | 别名 | 描述 | 是否必填 | 默认值 |\n");
        sb.append("|-------|------|------|---------|-------|\n");
        for (Map<String, String> param : paramInfoList) {
            sb.append("| ").append(param.get("name"));
            sb.append(" | ").append(param.get("alias"));
            sb.append(" | ").append(param.get("description"));
            sb.append(" | ").append(param.get("required"));
            sb.append(" | ").append(param.get("defaultValue"));
            sb.append(" |\n");
        }
        sb.append("\n");

        sb.append("## 常用参数参考值\n");
        sb.append("- CPU相关: cpu-count一般为1-4，cpu-percent一般为50-100\n");
        sb.append("- 内存相关: mem-percent一般为50-90，mode一般为ram或cache\n");
        sb.append("- 网络相关: time（延迟）一般为100-1000ms，percent（丢包）一般为10-50%\n");
        sb.append("- 进程相关: process一般为java、python等进程名\n\n");

        sb.append("请为上述每个参数生成合适的值，以JSON格式输出。\n");

        return sb.toString();
    }

    /**
     * 解析LLM返回的故障参数
     */
    private Map<String, String> parseFaultParamsResponse(String llmResponse) {
        Map<String, String> params = new HashMap<>();

        try {
            // 提取JSON部分
            String json = llmResponse;
            int jsonStart = llmResponse.indexOf("{");
            int jsonEnd = llmResponse.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = llmResponse.substring(jsonStart, jsonEnd + 1);
            }

            // 解析JSON
            JsonNode root = objectMapper.readTree(json);
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String value = root.path(fieldName).asText();
                if (value != null && !value.isEmpty()) {
                    params.put(fieldName, value);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse LLM response: {}", e.getMessage());
        }

        return params;
    }

    /**
     * 获取故障类型的默认参数值（作为LLM调用失败时的后备方案）
     */
    private Map<String, String> getDefaultFaultParams(String faultCode) {
        Map<String, String> params = new HashMap<>();

        switch (faultCode) {
            case "chaos.container-cpu.fullload":
                params.put("cpu-count", "2");
                break;
            case "chaos.container-cpu.load":
                params.put("cpu-percent", "80");
                break;
            case "chaos.container-mem.load":
                params.put("mem-percent", "80");
                params.put("mode", "ram");
                break;
            case "chaos.container-network.delay":
                params.put("time", "300");
                params.put("offset", "50");
                break;
            case "chaos.container-network.loss":
                params.put("percent", "30");
                break;
            case "chaos.container-process.kill":
                params.put("process", "java");
                break;
            default:
                logger.warn("No default params defined for: {}", faultCode);
        }

        return params;
    }

    /**
     * 获取故障参数的默认值
     *
     * 优先级：
     * 1. 根据故障代码和参数名匹配预定义默认值
     * 2. 使用模板中的defaultValue
     * 3. 返回null（不设置该参数）
     */
    private String getDefaultFaultParamValue(String faultCode, String paramName, ObjectNode arg) {
        // 预定义的默认值映射（按故障类型和参数名）
        Map<String, Map<String, String>> defaultValues = new HashMap<>();

        // CPU相关故障默认值
        Map<String, String> cpuFullload = new HashMap<>();
        cpuFullload.put("cpu-count", "2");
        defaultValues.put("chaos.container-cpu.fullload", cpuFullload);

        Map<String, String> cpuLoad = new HashMap<>();
        cpuLoad.put("cpu-percent", "80");
        defaultValues.put("chaos.container-cpu.load", cpuLoad);

        // 内存相关故障默认值
        Map<String, String> memLoad = new HashMap<>();
        memLoad.put("mem-percent", "80");
        memLoad.put("mode", "ram");
        defaultValues.put("chaos.container-mem.load", memLoad);

        // 网络相关故障默认值
        Map<String, String> networkDelay = new HashMap<>();
        networkDelay.put("time", "300");
        networkDelay.put("offset", "50");
        defaultValues.put("chaos.container-network.delay", networkDelay);

        Map<String, String> networkLoss = new HashMap<>();
        networkLoss.put("percent", "30");
        defaultValues.put("chaos.container-network.loss", networkLoss);

        // 进程相关故障默认值
        Map<String, String> processKill = new HashMap<>();
        processKill.put("process", "java");
        defaultValues.put("chaos.container-process.kill", processKill);

        // 1. 尝试获取预定义默认值
        if (defaultValues.containsKey(faultCode)) {
            String predefinedValue = defaultValues.get(faultCode).get(paramName);
            if (predefinedValue != null) {
                return predefinedValue;
            }
        }

        // 2. 尝试使用模板中的默认值
        JsonNode componentNode = arg.path("component");
        if (componentNode.isObject()) {
            String templateDefault = componentNode.path("defaultValue").asText(null);
            if (templateDefault != null && !templateDefault.isEmpty()) {
                return templateDefault;
            }
        }

        // 3. 检查是否必填参数，如果是则给一个通用默认值
        boolean required = arg.path("component").path("required").asBoolean(false);
        if (required) {
            logger.warn("Required param {} for {} has no default value", paramName, faultCode);
        }

        return null;
    }

    /**
     * 批量构建ExperimentConfig
     *
     * @param scenarios 场景列表
     * @param serviceName 服务名称
     * @param namespace K8s命名空间
     * @param faultParamsMap 每个场景对应的故障参数
     * @return 实验配置列表
     */
    public List<ExperimentConfig> buildFromScenarios(
            List<ChaosScenario> scenarios,
            String serviceName,
            String namespace,
            Map<String, Map<String, String>> faultParamsMap) {

        List<ExperimentConfig> configs = new ArrayList<>();

        for (ChaosScenario scenario : scenarios) {
            try {
                Map<String, String> params = faultParamsMap != null
                    ? faultParamsMap.get(scenario.getScenarioId())
                    : null;

                ExperimentConfig config = buildFromScenario(
                    scenario, serviceName, namespace, params);
                configs.add(config);

            } catch (Exception e) {
                logger.error("Failed to build config for scenario {}: {}",
                    scenario.getScenarioId(), e.getMessage());
            }
        }

        return configs;
    }
}

