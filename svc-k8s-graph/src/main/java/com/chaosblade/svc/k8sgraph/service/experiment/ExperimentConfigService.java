package com.chaosblade.svc.k8sgraph.service.experiment;

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

        // 获取并填充参数模版
        List<Object> arguments = buildFaultArguments(
            faultCode,
            namespace,
            faultParams,
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
                    // 故障配置：使用LLM生成的参数
                    if (faultParams != null && faultParams.containsKey(paramName)) {
                        arg.put("value", faultParams.get(paramName));
                        arg.put("state", true);
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

