package com.chaosblade.svc.k8sgraph.client.chaosblade;

import com.chaosblade.svc.k8sgraph.client.chaosblade.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ChaosBlade Box服务封装
 * 
 * 提供高层业务接口，封装底层API调用
 */
@Service
public class ChaosBladeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosBladeService.class);
    
    @Autowired
    private ChaosBladeClient client;
    
    /**
     * 根据应用名称查找应用信息
     * @param appName 应用名称
     * @return 应用信息，如果未找到返回null
     */
    public ApplicationInfo findApplication(String appName) {
        logger.info("Finding application: {}", appName);
        
        JsonNode response = client.getUserApplications(appName);
        
        if (response.has("result") && response.get("result").has("data")) {
            JsonNode data = response.get("result").get("data");
            if (data.isArray() && data.size() > 0) {
                // 精确匹配
                for (JsonNode app : data) {
                    if (appName.equals(app.get("app_name").asText())) {
                        ApplicationInfo info = new ApplicationInfo();
                        info.setAppId(app.get("app_id").asText());
                        info.setAppName(app.get("app_name").asText());
                        info.setAppType(app.get("app_type").asInt());
                        info.setScopeType(app.get("scope_type").asInt());
                        logger.info("Found application: {} (id={})", info.getAppName(), info.getAppId());
                        return info;
                    }
                }
            }
        }
        
        logger.warn("Application not found: {}", appName);
        return null;
    }
    
    /**
     * 获取应用的分组列表
     * @param appId 应用ID
     * @return 分组名称列表
     */
    public List<String> getApplicationGroups(String appId) {
        logger.info("Getting application groups for appId: {}", appId);
        
        JsonNode response = client.getUserApplicationGroups(appId);
        List<String> groups = new ArrayList<>();
        
        if (response.has("result") && response.get("result").isArray()) {
            for (JsonNode group : response.get("result")) {
                groups.add(group.asText());
            }
        }
        
        logger.info("Found {} groups for appId {}", groups.size(), appId);
        return groups;
    }
    
    /**
     * 获取应用的机器/Pod列表
     * @param appId 应用ID
     * @param appGroups 应用分组
     * @return 机器信息列表
     */
    public List<ScopeInfo> getApplicationScopes(String appId, List<String> appGroups) {
        logger.info("Getting scopes for appId: {}, groups: {}", appId, appGroups);

        JsonNode response = client.getScopesByApplication(appId, appGroups);
        List<ScopeInfo> scopes = new ArrayList<>();

        if (response.has("result") && response.get("result").has("data")) {
            for (JsonNode scope : response.get("result").get("data")) {
                ScopeInfo info = new ScopeInfo();
                info.setDeviceId(scope.path("deviceId").asText());
                info.setDeviceName(scope.path("deviceName").asText());
                info.setIp(scope.path("ip").asText());
                info.setPrivateIp(scope.path("privateIp").asText(scope.path("ip").asText()));
                info.setAppId(scope.path("appId").asText());
                info.setClusterId(scope.path("clusterId").asText());
                info.setClusterName(scope.path("clusterName").asText());
                info.setKubNamespace(scope.path("kubNamespace").asText());
                info.setNodeGroup(scope.path("nodeGroup").asText());
                // 新增字段
                info.setAppConfigurationId(scope.path("appConfigurationId").asText());
                info.setDeviceConfigurationId(scope.path("deviceConfigurationId").asText());
                info.setPort(scope.path("port").asInt(19527));
                info.setVpcId(scope.path("vpcId").asText());
                info.setAppName(scope.path("appName").asText());
                scopes.add(info);
            }
        }

        logger.info("Found {} scopes for appId {}", scopes.size(), appId);
        return scopes;
    }
    
    /**
     * 获取故障场景分类
     * @return 场景分类列表
     */
    public List<SceneCategory> getSceneCategories() {
        logger.info("Getting scene categories");
        
        JsonNode response = client.querySceneFunctionCategories();
        List<SceneCategory> categories = new ArrayList<>();
        
        if (response.has("result") && response.get("result").isArray()) {
            for (JsonNode cat : response.get("result")) {
                categories.add(parseCategory(cat));
            }
        }
        
        logger.info("Found {} scene categories", categories.size());
        return categories;
    }
    
    private SceneCategory parseCategory(JsonNode node) {
        SceneCategory cat = new SceneCategory();
        cat.setCategoryId(node.path("categoryId").asText());
        cat.setName(node.path("name").asText());
        cat.setLevel(node.path("level").asInt());
        cat.setParentId(node.path("parentId").asText());

        if (node.has("children") && node.get("children").isArray()) {
            List<SceneCategory> children = new ArrayList<>();
            for (JsonNode child : node.get("children")) {
                children.add(parseCategory(child));
            }
            cat.setChildren(children);
        }

        return cat;
    }

    /**
     * 根据分类ID获取故障场景列表
     * @param categoryId 分类ID
     * @return 场景功能列表
     */
    public List<SceneFunction> getSceneFunctions(String categoryId) {
        logger.info("Getting scene functions for categoryId: {}", categoryId);

        JsonNode response = client.querySceneFunctionByCategoryId(categoryId);
        List<SceneFunction> functions = new ArrayList<>();

        if (response.has("result") && response.get("result").has("data")) {
            for (JsonNode func : response.get("result").get("data")) {
                SceneFunction sf = new SceneFunction();
                sf.setFunctionId(func.path("functionId").asText());
                sf.setCode(func.path("code").asText());
                sf.setName(func.path("name").asText());
                sf.setDescription(func.path("description").asText());
                sf.setSceneId(func.path("sceneId").asText());
                sf.setType(func.path("type").asText());
                functions.add(sf);
            }
        }

        logger.info("Found {} scene functions for categoryId {}", functions.size(), categoryId);
        return functions;
    }

    /**
     * 获取所有故障场景（递归遍历所有分类）
     * @return 完整的故障场景列表
     */
    public List<SceneFunction> getAllSceneFunctions() {
        logger.info("Getting all scene functions from all categories");
        List<SceneFunction> allFunctions = new ArrayList<>();

        // 获取所有分类
        JsonNode response = client.querySceneFunctionCategories();
        if (response.has("result") && response.get("result").isArray()) {
            for (JsonNode cat : response.get("result")) {
                collectFunctionsFromCategory(cat, allFunctions);
            }
        }

        logger.info("Total scene functions collected: {}", allFunctions.size());
        return allFunctions;
    }

    /**
     * 递归遍历分类节点，收集所有故障场景
     */
    private void collectFunctionsFromCategory(JsonNode categoryNode, List<SceneFunction> allFunctions) {
        String categoryId = categoryNode.path("categoryId").asText();
        String categoryName = categoryNode.path("name").asText();

        // 获取当前分类下的故障场景
        if (categoryId != null && !categoryId.isEmpty()) {
            List<SceneFunction> functions = getSceneFunctions(categoryId);
            if (!functions.isEmpty()) {
                logger.debug("Category '{}' has {} functions", categoryName, functions.size());
                allFunctions.addAll(functions);
            }
        }

        // 递归处理子分类
        if (categoryNode.has("children") && categoryNode.get("children").isArray()) {
            for (JsonNode child : categoryNode.get("children")) {
                collectFunctionsFromCategory(child, allFunctions);
            }
        }
    }

    /**
     * 获取故障场景参数模板
     * @param appCode 故障场景代码，如 chaos.container-mem.load
     * @param nodeGroups 节点分组列表
     * @return 参数列表
     */
    public List<SceneParameter> getSceneParameters(String appCode, List<String> nodeGroups) {
        logger.info("Getting scene parameters for appCode: {}", appCode);

        JsonNode response = client.initMiniFlowByAppCode(appCode, nodeGroups);
        List<SceneParameter> parameters = new ArrayList<>();

        if (response.has("result") && response.get("result").has("attack")) {
            JsonNode attacks = response.get("result").get("attack");
            if (attacks.isArray() && attacks.size() > 0) {
                JsonNode arguments = attacks.get(0).path("arguments");
                if (arguments.isArray()) {
                    for (JsonNode argGroup : arguments) {
                        String gradeName = argGroup.path("gradeName").asText();
                        JsonNode argList = argGroup.path("argumentList");
                        if (argList.isArray()) {
                            for (JsonNode arg : argList) {
                                SceneParameter param = new SceneParameter();
                                param.setParameterId(arg.path("parameterId").asText());
                                param.setName(arg.path("name").asText());
                                param.setAlias(arg.path("alias").asText());
                                param.setDescription(arg.path("description").asText());
                                param.setDefaultValue(arg.path("component").path("defaultValue").asText());
                                param.setRequired(arg.path("component").path("required").asBoolean());
                                param.setGrade(arg.path("grade").asInt());
                                param.setGradeName(gradeName);
                                parameters.add(param);
                            }
                        }
                    }
                }
            }
        }

        logger.info("Found {} parameters for appCode {}", parameters.size(), appCode);
        return parameters;
    }

    /**
     * 获取故障场景参数模版的原始JSON（用于构建ExperimentConfig）
     *
     * 返回完整的arguments数组，便于直接填充到Activity中
     *
     * @param appCode 故障场景代码，如 chaos.container-cpu.fullload
     * @param nodeGroups 节点分组列表
     * @return 原始的arguments JsonNode数组，如果获取失败返回null
     */
    public JsonNode getSceneArgumentsTemplate(String appCode, List<String> nodeGroups) {
        logger.info("Getting scene arguments template for appCode: {}", appCode);

        JsonNode response = client.initMiniFlowByAppCode(appCode, nodeGroups);

        if (response.has("result") && response.get("result").has("attack")) {
            JsonNode attacks = response.get("result").get("attack");
            if (attacks.isArray() && attacks.size() > 0) {
                JsonNode arguments = attacks.get(0).path("arguments");
                if (arguments.isArray()) {
                    logger.info("Got arguments template with {} groups for appCode {}",
                        arguments.size(), appCode);
                    return arguments;
                }
            }
        }

        logger.warn("Failed to get arguments template for appCode: {}", appCode);
        return null;
    }

    /**
     * 便捷方法：根据应用名获取完整信息（应用+分组+机器）
     * @param appName 应用名称
     * @return 应用完整信息
     */
    public ApplicationFullInfo getApplicationFullInfo(String appName) {
        ApplicationInfo app = findApplication(appName);
        if (app == null) {
            return null;
        }

        ApplicationFullInfo fullInfo = new ApplicationFullInfo();
        fullInfo.setApplication(app);

        List<String> groups = getApplicationGroups(app.getAppId());
        fullInfo.setGroups(groups);

        List<ScopeInfo> scopes = getApplicationScopes(app.getAppId(), groups);
        fullInfo.setScopes(scopes);

        return fullInfo;
    }
}

