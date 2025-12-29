package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 风险规则引擎服务
 * 
 * Phase 1: 以服务为单位，扫描其关联的所有K8s资源，识别配置层面的风险点并量化评分
 */
@Service
public class RiskRuleEngineService {
    
    private static final Logger logger = LoggerFactory.getLogger(RiskRuleEngineService.class);
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    /** 内置规则列表 */
    private List<RiskRule> rules;
    
    /** 关键基础设施关键词 */
    private static final List<String> CRITICAL_INFRA_KEYWORDS = Arrays.asList(
        "mysql", "postgres", "mongodb", "redis", "kafka", "rabbitmq", "zookeeper",
        "elasticsearch", "etcd", "consul", "vault", "minio", "database", "db", "mq", "cache"
    );
    
    @PostConstruct
    public void init() {
        this.rules = loadBuiltInRules();
        logger.info("Loaded {} risk rules", rules.size());
    }
    
    /**
     * 扫描指定命名空间的所有服务
     */
    public NamespaceScanResult scanNamespace(String namespace) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting risk scan for namespace: {}", namespace);
        
        NamespaceScanResult result = new NamespaceScanResult(namespace);
        
        // 1. 获取所有Deployment，每个Deployment视为一个服务
        List<Deployment> deployments = listDeployments(namespace);
        
        // 2. 获取所有StatefulSet
        List<StatefulSet> statefulSets = listStatefulSets(namespace);
        
        // 3. 获取PDB和HPA用于后续检查
        Map<String, PodDisruptionBudget> pdbMap = getPDBMap(namespace);
        Map<String, HorizontalPodAutoscaler> hpaMap = getHPAMap(namespace);
        
        // 4. 扫描每个Deployment
        for (Deployment deployment : deployments) {
            ServiceRiskProfile profile = scanDeployment(deployment, pdbMap, hpaMap);
            result.addServiceProfile(profile);
        }

        // 5. 扫描每个StatefulSet
        for (StatefulSet statefulSet : statefulSets) {
            ServiceRiskProfile profile = scanStatefulSet(statefulSet, pdbMap, hpaMap);
            result.addServiceProfile(profile);
        }
        
        // 6. 完成扫描，计算汇总
        result.finalize();
        result.setScanDurationMs(System.currentTimeMillis() - startTime);
        
        logger.info("Completed risk scan for namespace {}: {} services, {} ms",
            namespace, result.getSummary().getTotalServices(), result.getScanDurationMs());
        
        return result;
    }
    
    /**
     * 扫描单个服务
     */
    public ServiceRiskProfile scanService(String serviceName, String namespace) {
        logger.info("Scanning service: {}/{}", namespace, serviceName);
        
        // 尝试查找Deployment
        Deployment deployment = kubernetesClient.apps().deployments()
            .inNamespace(namespace).withName(serviceName).get();
        if (deployment != null) {
            Map<String, PodDisruptionBudget> pdbMap = getPDBMap(namespace);
            Map<String, HorizontalPodAutoscaler> hpaMap = getHPAMap(namespace);
            return scanDeployment(deployment, pdbMap, hpaMap);
        }
        
        // 尝试查找StatefulSet
        StatefulSet statefulSet = kubernetesClient.apps().statefulSets()
            .inNamespace(namespace).withName(serviceName).get();
        if (statefulSet != null) {
            Map<String, PodDisruptionBudget> pdbMap = getPDBMap(namespace);
            Map<String, HorizontalPodAutoscaler> hpaMap = getHPAMap(namespace);
            return scanStatefulSet(statefulSet, pdbMap, hpaMap);
        }
        
        // 未找到资源
        ServiceRiskProfile profile = new ServiceRiskProfile(serviceName, namespace);
        logger.warn("Service not found: {}/{}", namespace, serviceName);
        return profile;
    }
    
    /**
     * 获取所有规则
     */
    public List<RiskRule> getRules() {
        return Collections.unmodifiableList(rules);
    }
    
    // ==================== 私有方法 ====================
    
    private List<Deployment> listDeployments(String namespace) {
        try {
            return kubernetesClient.apps().deployments()
                .inNamespace(namespace).list().getItems();
        } catch (Exception e) {
            logger.error("Failed to list deployments in {}: {}", namespace, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private List<StatefulSet> listStatefulSets(String namespace) {
        try {
            return kubernetesClient.apps().statefulSets()
                .inNamespace(namespace).list().getItems();
        } catch (Exception e) {
            logger.error("Failed to list statefulsets in {}: {}", namespace, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private Map<String, PodDisruptionBudget> getPDBMap(String namespace) {
        Map<String, PodDisruptionBudget> map = new HashMap<>();
        try {
            List<PodDisruptionBudget> pdbs = kubernetesClient.policy().v1()
                .podDisruptionBudget().inNamespace(namespace).list().getItems();
            for (PodDisruptionBudget pdb : pdbs) {
                // 使用selector的matchLabels来关联
                if (pdb.getSpec() != null && pdb.getSpec().getSelector() != null) {
                    Map<String, String> labels = pdb.getSpec().getSelector().getMatchLabels();
                    if (labels != null) {
                        String appName = labels.getOrDefault("app", labels.get("app.kubernetes.io/name"));
                        if (appName != null) {
                            map.put(appName, pdb);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list PDBs in {}: {}", namespace, e.getMessage());
        }
        return map;
    }
    private Map<String, HorizontalPodAutoscaler> getHPAMap(String namespace) {
        Map<String, HorizontalPodAutoscaler> map = new HashMap<>();
        try {
            List<HorizontalPodAutoscaler> hpas = kubernetesClient.autoscaling().v2()
                .horizontalPodAutoscalers().inNamespace(namespace).list().getItems();
            for (HorizontalPodAutoscaler hpa : hpas) {
                if (hpa.getSpec() != null && hpa.getSpec().getScaleTargetRef() != null) {
                    String targetName = hpa.getSpec().getScaleTargetRef().getName();
                    map.put(targetName, hpa);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list HPAs in {}: {}", namespace, e.getMessage());
        }
        return map;
    }

    /**
     * 扫描Deployment
     */
    private ServiceRiskProfile scanDeployment(Deployment deployment,
            Map<String, PodDisruptionBudget> pdbMap,
            Map<String, HorizontalPodAutoscaler> hpaMap) {

        String name = deployment.getMetadata().getName();
        String namespace = deployment.getMetadata().getNamespace();
        ServiceRiskProfile profile = new ServiceRiskProfile(name, namespace);

        // 添加关联资源
        profile.addRelatedResource(new ServiceRiskProfile.RelatedResource("Deployment", name));

        // 检查是否为关键基础设施
        boolean isCritical = isCriticalInfra(name);
        profile.setCriticalInfra(isCritical);
        if (isCritical) {
            profile.setInfraType(detectInfraType(name));
        }

        // 获取规格
        int replicas = deployment.getSpec().getReplicas() != null ? deployment.getSpec().getReplicas() : 1;
        PodTemplateSpec template = deployment.getSpec().getTemplate();
        List<Container> containers = template.getSpec().getContainers();

        int rulesChecked = 0;

        // 检查各项规则
        // 1. 副本数检查
        rulesChecked++;
        if (replicas == 1) {
            TriggeredRule rule = createTriggeredRule("AVAIL_001", "Deployment", name, isCritical);
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("replicas", replicas);
            rule.setEvidence(evidence);
            rule.setEvidenceDescription("当前副本数: " + replicas);
            profile.addTriggeredRule(rule);
        }

        // 2. 就绪探针检查
        for (Container container : containers) {
            rulesChecked++;
            if (container.getReadinessProbe() == null) {
                TriggeredRule rule = createTriggeredRule("AVAIL_002", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置就绪探针");
                profile.addTriggeredRule(rule);
            }

            // 3. 存活探针检查
            rulesChecked++;
            if (container.getLivenessProbe() == null) {
                TriggeredRule rule = createTriggeredRule("AVAIL_003", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置存活探针");
                profile.addTriggeredRule(rule);
            }

            // 4. 资源limits检查
            rulesChecked++;
            ResourceRequirements resources = container.getResources();
            if (resources == null || resources.getLimits() == null || resources.getLimits().isEmpty()) {
                TriggeredRule rule = createTriggeredRule("RES_001", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置资源限制");
                profile.addTriggeredRule(rule);
            }

            // 5. 资源requests检查
            rulesChecked++;
            if (resources == null || resources.getRequests() == null || resources.getRequests().isEmpty()) {
                TriggeredRule rule = createTriggeredRule("RES_002", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置资源请求");
                profile.addTriggeredRule(rule);
            }

            // 6. 镜像标签检查
            rulesChecked++;
            String image = container.getImage();
            if (image != null && (image.endsWith(":latest") || !image.contains(":"))) {
                TriggeredRule rule = createTriggeredRule("STAB_001", "Container", container.getName(), false);
                Map<String, Object> imgEvidence = new HashMap<>();
                imgEvidence.put("image", image);
                rule.setEvidence(imgEvidence);
                rule.setEvidenceDescription("容器 " + container.getName() + " 使用latest或无标签镜像: " + image);
                profile.addTriggeredRule(rule);
            }
        }

        // 7. PDB检查
        rulesChecked++;
        if (!pdbMap.containsKey(name) && replicas > 1) {
            TriggeredRule rule = createTriggeredRule("AVAIL_004", "Deployment", name, isCritical);
            rule.setEvidenceDescription("多副本部署未配置PodDisruptionBudget");
            profile.addTriggeredRule(rule);
        }

        // 8. HPA检查
        rulesChecked++;
        if (!hpaMap.containsKey(name)) {
            TriggeredRule rule = createTriggeredRule("SCALE_001", "Deployment", name, false);
            rule.setEvidenceDescription("未配置HorizontalPodAutoscaler");
            profile.addTriggeredRule(rule);
        }

        // 更新统计
        profile.getScanStats().setTotalRulesChecked(rulesChecked);
        profile.getScanStats().setRulesTriggered(profile.getTriggeredRules().size());
        profile.getScanStats().setResourcesScanned(1 + containers.size());

        return profile;
    }

    /**
     * 扫描StatefulSet
     */
    private ServiceRiskProfile scanStatefulSet(StatefulSet statefulSet,
            Map<String, PodDisruptionBudget> pdbMap,
            Map<String, HorizontalPodAutoscaler> hpaMap) {

        String name = statefulSet.getMetadata().getName();
        String namespace = statefulSet.getMetadata().getNamespace();
        ServiceRiskProfile profile = new ServiceRiskProfile(name, namespace);

        profile.addRelatedResource(new ServiceRiskProfile.RelatedResource("StatefulSet", name));

        // StatefulSet通常是有状态服务，默认视为关键基础设施
        boolean isCritical = true;
        profile.setCriticalInfra(isCritical);
        profile.setInfraType(detectInfraType(name));

        int replicas = statefulSet.getSpec().getReplicas() != null ? statefulSet.getSpec().getReplicas() : 1;
        PodTemplateSpec template = statefulSet.getSpec().getTemplate();
        List<Container> containers = template.getSpec().getContainers();

        int rulesChecked = 0;

        // 单副本检查
        rulesChecked++;
        if (replicas == 1) {
            TriggeredRule rule = createTriggeredRule("AVAIL_001", "StatefulSet", name, isCritical);
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("replicas", replicas);
            rule.setEvidence(evidence);
            rule.setEvidenceDescription("有状态服务单副本部署，风险极高");
            profile.addTriggeredRule(rule);
        }

        // 容器级别检查（与Deployment相同）
        for (Container container : containers) {
            rulesChecked++;
            if (container.getReadinessProbe() == null) {
                TriggeredRule rule = createTriggeredRule("AVAIL_002", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置就绪探针");
                profile.addTriggeredRule(rule);
            }

            rulesChecked++;
            if (container.getLivenessProbe() == null) {
                TriggeredRule rule = createTriggeredRule("AVAIL_003", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置存活探针");
                profile.addTriggeredRule(rule);
            }

            rulesChecked++;
            ResourceRequirements resources = container.getResources();
            if (resources == null || resources.getLimits() == null || resources.getLimits().isEmpty()) {
                TriggeredRule rule = createTriggeredRule("RES_001", "Container", container.getName(), isCritical);
                rule.setEvidenceDescription("容器 " + container.getName() + " 未配置资源限制");
                profile.addTriggeredRule(rule);
            }
        }

        // PDB检查
        rulesChecked++;
        if (!pdbMap.containsKey(name) && replicas > 1) {
            TriggeredRule rule = createTriggeredRule("AVAIL_004", "StatefulSet", name, isCritical);
            rule.setEvidenceDescription("有状态多副本服务未配置PodDisruptionBudget");
            profile.addTriggeredRule(rule);
        }

        profile.getScanStats().setTotalRulesChecked(rulesChecked);
        profile.getScanStats().setRulesTriggered(profile.getTriggeredRules().size());
        profile.getScanStats().setResourcesScanned(1 + containers.size());

        return profile;
    }

    private boolean isCriticalInfra(String name) {
        String lowerName = name.toLowerCase();
        return CRITICAL_INFRA_KEYWORDS.stream().anyMatch(lowerName::contains);
    }

    private String detectInfraType(String name) {
        String lowerName = name.toLowerCase();
        if (lowerName.contains("mysql") || lowerName.contains("postgres") || lowerName.contains("mongodb")) {
            return "database";
        }
        if (lowerName.contains("kafka") || lowerName.contains("rabbitmq")) {
            return "mq";
        }
        if (lowerName.contains("redis")) {
            return "cache";
        }
        if (lowerName.contains("zookeeper") || lowerName.contains("etcd") || lowerName.contains("consul")) {
            return "coordination";
        }
        return "stateful";
    }

    private TriggeredRule createTriggeredRule(String ruleId, String resourceType, String resourceName, boolean applyCriticalBonus) {
        RiskRule rule = findRuleById(ruleId);
        if (rule == null) {
            logger.warn("Rule not found: {}", ruleId);
            return null;
        }

        TriggeredRule triggered = TriggeredRule.from(rule, resourceType, resourceName);
        if (applyCriticalBonus && rule.getCriticalInfraBonus() > 0) {
            triggered.applyCriticalBonus(rule.getCriticalInfraBonus());
        }
        return triggered;
    }

    private RiskRule findRuleById(String ruleId) {
        return rules.stream().filter(r -> r.getRuleId().equals(ruleId)).findFirst().orElse(null);
    }

    /**
     * 加载内置规则
     */
    private List<RiskRule> loadBuiltInRules() {
        List<RiskRule> ruleList = new ArrayList<>();

        // AVAIL_001: 单副本部署
        RiskRule avail001 = new RiskRule();
        avail001.setRuleId("AVAIL_001");
        avail001.setName("单副本部署");
        avail001.setDescription("服务仅有单个副本，任何故障都会导致服务完全不可用");
        avail001.setCategory(RiskRuleCategory.AVAILABILITY);
        avail001.setBaseScore(30);
        avail001.setCriticalInfraBonus(20);
        avail001.setSeverity("HIGH");
        avail001.setRemediation("建议至少配置2个副本以保证高可用");
        ruleList.add(avail001);

        // AVAIL_002: 无就绪探针
        RiskRule avail002 = new RiskRule();
        avail002.setRuleId("AVAIL_002");
        avail002.setName("无就绪探针");
        avail002.setDescription("未配置就绪探针，可能导致流量在服务未就绪时被转发");
        avail002.setCategory(RiskRuleCategory.AVAILABILITY);
        avail002.setBaseScore(25);
        avail002.setCriticalInfraBonus(15);
        avail002.setSeverity("HIGH");
        avail002.setRemediation("配置readinessProbe确保服务就绪后才接收流量");
        ruleList.add(avail002);

        // AVAIL_003: 无存活探针
        RiskRule avail003 = new RiskRule();
        avail003.setRuleId("AVAIL_003");
        avail003.setName("无存活探针");
        avail003.setDescription("未配置存活探针，无法自动检测和重启异常容器");
        avail003.setCategory(RiskRuleCategory.AVAILABILITY);
        avail003.setBaseScore(20);
        avail003.setCriticalInfraBonus(10);
        avail003.setSeverity("MEDIUM");
        avail003.setRemediation("配置livenessProbe确保容器异常时自动重启");
        ruleList.add(avail003);

        // AVAIL_004: 无PDB
        RiskRule avail004 = new RiskRule();
        avail004.setRuleId("AVAIL_004");
        avail004.setName("无PodDisruptionBudget");
        avail004.setDescription("多副本服务未配置PDB，节点维护时可能所有副本同时被驱逐");
        avail004.setCategory(RiskRuleCategory.AVAILABILITY);
        avail004.setBaseScore(15);
        avail004.setCriticalInfraBonus(10);
        avail004.setSeverity("MEDIUM");
        avail004.setRemediation("配置PodDisruptionBudget限制同时不可用的Pod数量");
        ruleList.add(avail004);

        // RES_001: 无资源限制
        RiskRule res001 = new RiskRule();
        res001.setRuleId("RES_001");
        res001.setName("无资源限制");
        res001.setDescription("未配置资源limits，可能耗尽节点资源影响其他服务");
        res001.setCategory(RiskRuleCategory.RESOURCE);
        res001.setBaseScore(20);
        res001.setCriticalInfraBonus(10);
        res001.setSeverity("MEDIUM");
        res001.setRemediation("配置CPU和内存的limits限制资源使用上限");
        ruleList.add(res001);

        // RES_002: 无资源请求
        RiskRule res002 = new RiskRule();
        res002.setRuleId("RES_002");
        res002.setName("无资源请求");
        res002.setDescription("未配置资源requests，调度器无法合理分配资源");
        res002.setCategory(RiskRuleCategory.RESOURCE);
        res002.setBaseScore(15);
        res002.setCriticalInfraBonus(5);
        res002.setSeverity("LOW");
        res002.setRemediation("配置CPU和内存的requests确保资源预留");
        ruleList.add(res002);

        // STAB_001: 使用latest标签
        RiskRule stab001 = new RiskRule();
        stab001.setRuleId("STAB_001");
        stab001.setName("使用latest镜像标签");
        stab001.setDescription("使用latest或无标签镜像，版本不可控可能导致意外更新");
        stab001.setCategory(RiskRuleCategory.STABILITY);
        stab001.setBaseScore(15);
        stab001.setCriticalInfraBonus(0);
        stab001.setSeverity("MEDIUM");
        stab001.setRemediation("使用明确的版本标签，避免latest");
        ruleList.add(stab001);

        // SCALE_001: 无HPA
        RiskRule scale001 = new RiskRule();
        scale001.setRuleId("SCALE_001");
        scale001.setName("无自动扩缩容");
        scale001.setDescription("未配置HPA，无法根据负载自动扩缩容");
        scale001.setCategory(RiskRuleCategory.SCALABILITY);
        scale001.setBaseScore(10);
        scale001.setCriticalInfraBonus(0);
        scale001.setSeverity("LOW");
        scale001.setRemediation("配置HorizontalPodAutoscaler实现自动扩缩容");
        ruleList.add(scale001);

        return ruleList;
    }
}

