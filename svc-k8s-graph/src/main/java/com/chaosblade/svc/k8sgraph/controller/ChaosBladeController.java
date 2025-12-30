package com.chaosblade.svc.k8sgraph.controller;

import com.chaosblade.svc.k8sgraph.client.chaosblade.ChaosBladeClient;
import com.chaosblade.svc.k8sgraph.client.chaosblade.ChaosBladeService;
import com.chaosblade.svc.k8sgraph.client.chaosblade.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * ChaosBlade Box API测试接口
 */
@RestController
@RequestMapping("/api/chaosblade")
public class ChaosBladeController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosBladeController.class);
    
    @Autowired
    private ChaosBladeClient chaosBladeClient;
    
    @Autowired
    private ChaosBladeService chaosBladeService;
    
    /**
     * 测试登录
     * GET /api/chaosblade/login
     */
    @GetMapping("/login")
    public ResponseEntity<String> testLogin() {
        logger.info("Testing ChaosBlade login...");
        String cookie = chaosBladeClient.login();
        return ResponseEntity.ok("Login successful, cookie: " + cookie);
    }
    
    /**
     * 获取用户应用列表（原始响应）
     * GET /api/chaosblade/raw/applications?key=ts-travel2-service
     */
    @GetMapping("/raw/applications")
    public ResponseEntity<JsonNode> getRawApplications(@RequestParam(required = false) String key) {
        logger.info("Getting raw applications, key: {}", key);
        JsonNode result = chaosBladeClient.getUserApplications(key);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取用户应用分组（原始响应）
     * GET /api/chaosblade/raw/groups?appId=78
     */
    @GetMapping("/raw/groups")
    public ResponseEntity<JsonNode> getRawGroups(@RequestParam String appId) {
        logger.info("Getting raw groups for appId: {}", appId);
        JsonNode result = chaosBladeClient.getUserApplicationGroups(appId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取应用机器列表（原始响应）
     * GET /api/chaosblade/raw/scopes?appId=78&appGroups=ts-travel2-service-group
     */
    @GetMapping("/raw/scopes")
    public ResponseEntity<JsonNode> getRawScopes(
            @RequestParam String appId,
            @RequestParam String appGroups) {
        logger.info("Getting raw scopes for appId: {}, groups: {}", appId, appGroups);
        List<String> groups = Arrays.asList(appGroups.split(","));
        JsonNode result = chaosBladeClient.getScopesByApplication(appId, groups);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取故障场景分类（原始响应）
     * GET /api/chaosblade/raw/categories
     */
    @GetMapping("/raw/categories")
    public ResponseEntity<JsonNode> getRawCategories() {
        logger.info("Getting raw scene categories");
        JsonNode result = chaosBladeClient.querySceneFunctionCategories();
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取故障场景详情（原始响应）
     * GET /api/chaosblade/raw/functions?categoryId=1216606392489857026
     */
    @GetMapping("/raw/functions")
    public ResponseEntity<JsonNode> getRawFunctions(@RequestParam String categoryId) {
        logger.info("Getting raw functions for categoryId: {}", categoryId);
        JsonNode result = chaosBladeClient.querySceneFunctionByCategoryId(categoryId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取故障参数模板（原始响应）
     * GET /api/chaosblade/raw/parameters?appCode=chaos.container-mem.load&nodeGroups=node-agent-group
     */
    @GetMapping("/raw/parameters")
    public ResponseEntity<JsonNode> getRawParameters(
            @RequestParam String appCode,
            @RequestParam(required = false) String nodeGroups) {
        logger.info("Getting raw parameters for appCode: {}", appCode);
        List<String> groups = nodeGroups != null ? Arrays.asList(nodeGroups.split(",")) : null;
        JsonNode result = chaosBladeClient.initMiniFlowByAppCode(appCode, groups);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 封装后的高层接口 ====================
    
    /**
     * 查找应用
     * GET /api/chaosblade/application?name=ts-travel2-service
     */
    @GetMapping("/application")
    public ResponseEntity<ApplicationInfo> findApplication(@RequestParam String name) {
        logger.info("Finding application: {}", name);
        ApplicationInfo app = chaosBladeService.findApplication(name);
        if (app == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(app);
    }
    
    /**
     * 获取应用分组
     * GET /api/chaosblade/application/{appId}/groups
     */
    @GetMapping("/application/{appId}/groups")
    public ResponseEntity<List<String>> getApplicationGroups(@PathVariable String appId) {
        logger.info("Getting groups for appId: {}", appId);
        List<String> groups = chaosBladeService.getApplicationGroups(appId);
        return ResponseEntity.ok(groups);
    }
    
    /**
     * 获取应用完整信息
     * GET /api/chaosblade/application/{name}/full
     */
    @GetMapping("/application/{name}/full")
    public ResponseEntity<ApplicationFullInfo> getApplicationFullInfo(@PathVariable String name) {
        logger.info("Getting full info for application: {}", name);
        ApplicationFullInfo fullInfo = chaosBladeService.getApplicationFullInfo(name);
        if (fullInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(fullInfo);
    }

    /**
     * 获取故障场景分类（直接返回原始响应）
     * GET /api/chaosblade/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<JsonNode> getSceneCategories() {
        logger.info("Getting scene categories");
        JsonNode result = chaosBladeClient.querySceneFunctionCategories();
        return ResponseEntity.ok(result);
    }

    /**
     * 获取故障场景功能列表
     * GET /api/chaosblade/functions?categoryId=1216606392489857026
     */
    @GetMapping("/functions")
    public ResponseEntity<List<SceneFunction>> getSceneFunctions(@RequestParam String categoryId) {
        logger.info("Getting scene functions for categoryId: {}", categoryId);
        List<SceneFunction> functions = chaosBladeService.getSceneFunctions(categoryId);
        return ResponseEntity.ok(functions);
    }

    /**
     * 获取完整的故障场景列表（递归遍历所有分类）
     * GET /api/chaosblade/all-functions
     */
    @GetMapping("/all-functions")
    public ResponseEntity<List<SceneFunction>> getAllSceneFunctions() {
        logger.info("Getting all scene functions from all categories");
        List<SceneFunction> allFunctions = chaosBladeService.getAllSceneFunctions();
        logger.info("Total scene functions found: {}", allFunctions.size());
        return ResponseEntity.ok(allFunctions);
    }

    /**
     * 获取故障场景参数（直接返回原始响应）
     * GET /api/chaosblade/parameters?appCode=chaos.container-mem.load&nodeGroups=node-agent-group
     */
    @GetMapping("/parameters")
    public ResponseEntity<JsonNode> getSceneParameters(
            @RequestParam String appCode,
            @RequestParam(required = false) String nodeGroups) {
        logger.info("Getting scene parameters for appCode: {}", appCode);
        List<String> groups = nodeGroups != null ? Arrays.asList(nodeGroups.split(",")) : null;
        JsonNode result = chaosBladeClient.initMiniFlowByAppCode(appCode, groups);
        return ResponseEntity.ok(result);
    }
}

