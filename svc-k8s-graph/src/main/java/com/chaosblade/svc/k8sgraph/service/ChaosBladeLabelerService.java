package com.chaosblade.svc.k8sgraph.service;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChaosBlade标签服务
 * 
 * 用于给K8s Deployment打上ChaosBlade所需的标签：
 * - chaos/app-instance: 部署名称
 * - chaos/app-group: 应用组名称（通常是命名空间）
 */
@Service
public class ChaosBladeLabelerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosBladeLabelerService.class);
    
    /** ChaosBlade标签前缀 */
    private static final String LABEL_APP_INSTANCE = "chaos/app-instance";
    private static final String LABEL_APP_GROUP = "chaos/app-group";
    
    @Autowired
    private KubernetesClient kubernetesClient;
    
    /**
     * 给命名空间下所有Deployment打上ChaosBlade标签
     * 
     * @param namespace 命名空间
     * @param appGroup 应用组名称（默认使用命名空间名称）
     * @return 标签结果
     */
    public LabelingResult labelNamespace(String namespace, String appGroup) {
        logger.info("Starting to label deployments in namespace: {} with app-group: {}", namespace, appGroup);
        
        LabelingResult result = new LabelingResult();
        result.setNamespace(namespace);
        result.setAppGroup(appGroup);
        
        try {
            // 获取命名空间下所有Deployment
            List<Deployment> deployments = kubernetesClient.apps().deployments()
                .inNamespace(namespace)
                .list()
                .getItems();
            
            result.setTotalDeployments(deployments.size());
            logger.info("Found {} deployments in namespace {}", deployments.size(), namespace);
            
            for (Deployment deployment : deployments) {
                String deploymentName = deployment.getMetadata().getName();
                
                try {
                    labelDeployment(namespace, deploymentName, appGroup);
                    result.getSuccessfulDeployments().add(deploymentName);
                    logger.info("Successfully labeled deployment: {}", deploymentName);
                } catch (Exception e) {
                    logger.error("Failed to label deployment {}: {}", deploymentName, e.getMessage());
                    result.getFailedDeployments().put(deploymentName, e.getMessage());
                }
            }
            
            result.setSuccess(result.getFailedDeployments().isEmpty());
            
        } catch (Exception e) {
            logger.error("Failed to list deployments in namespace {}: {}", namespace, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        logger.info("Labeling completed: {} successful, {} failed",
            result.getSuccessfulDeployments().size(),
            result.getFailedDeployments().size());
        
        return result;
    }
    
    /**
     * 给单个Deployment打上ChaosBlade标签
     */
    public void labelDeployment(String namespace, String deploymentName, String appGroup) {
        kubernetesClient.apps().deployments()
            .inNamespace(namespace)
            .withName(deploymentName)
            .edit(deployment -> {
                // 获取或创建labels
                Map<String, String> labels = deployment.getMetadata().getLabels();
                if (labels == null) {
                    labels = new HashMap<>();
                    deployment.getMetadata().setLabels(labels);
                }
                
                // 添加ChaosBlade标签
                labels.put(LABEL_APP_INSTANCE, deploymentName);
                labels.put(LABEL_APP_GROUP, appGroup);
                
                // 同时给Pod模板也打上标签（这样新创建的Pod也会有这些标签）
                Map<String, String> podLabels = deployment.getSpec().getTemplate().getMetadata().getLabels();
                if (podLabels == null) {
                    podLabels = new HashMap<>();
                    deployment.getSpec().getTemplate().getMetadata().setLabels(podLabels);
                }
                podLabels.put(LABEL_APP_INSTANCE, deploymentName);
                podLabels.put(LABEL_APP_GROUP, appGroup);
                
                // 清理managedFields避免序列化问题
                if (deployment.getMetadata() != null) {
                    deployment.getMetadata().setManagedFields(null);
                }
                
                return deployment;
            });
    }
    
    /**
     * 获取命名空间下所有Deployment的ChaosBlade标签状态
     */
    public List<DeploymentLabelStatus> getLabelStatus(String namespace) {
        List<DeploymentLabelStatus> statusList = new ArrayList<>();
        
        List<Deployment> deployments = kubernetesClient.apps().deployments()
            .inNamespace(namespace)
            .list()
            .getItems();
        
        for (Deployment deployment : deployments) {
            DeploymentLabelStatus status = new DeploymentLabelStatus();
            status.setDeploymentName(deployment.getMetadata().getName());
            
            Map<String, String> labels = deployment.getMetadata().getLabels();
            if (labels != null) {
                status.setAppInstance(labels.get(LABEL_APP_INSTANCE));
                status.setAppGroup(labels.get(LABEL_APP_GROUP));
                status.setLabeled(labels.containsKey(LABEL_APP_INSTANCE) && labels.containsKey(LABEL_APP_GROUP));
            } else {
                status.setLabeled(false);
            }
            
            statusList.add(status);
        }

        return statusList;
    }

    // ==================== 结果类定义 ====================

    /**
     * 标签操作结果
     */
    public static class LabelingResult {
        private String namespace;
        private String appGroup;
        private boolean success;
        private String errorMessage;
        private int totalDeployments;
        private List<String> successfulDeployments = new ArrayList<>();
        private Map<String, String> failedDeployments = new HashMap<>();

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }

        public String getAppGroup() { return appGroup; }
        public void setAppGroup(String appGroup) { this.appGroup = appGroup; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public int getTotalDeployments() { return totalDeployments; }
        public void setTotalDeployments(int totalDeployments) { this.totalDeployments = totalDeployments; }

        public List<String> getSuccessfulDeployments() { return successfulDeployments; }
        public void setSuccessfulDeployments(List<String> successfulDeployments) { this.successfulDeployments = successfulDeployments; }

        public Map<String, String> getFailedDeployments() { return failedDeployments; }
        public void setFailedDeployments(Map<String, String> failedDeployments) { this.failedDeployments = failedDeployments; }
    }

    /**
     * Deployment标签状态
     */
    public static class DeploymentLabelStatus {
        private String deploymentName;
        private boolean labeled;
        private String appInstance;
        private String appGroup;

        public String getDeploymentName() { return deploymentName; }
        public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }

        public boolean isLabeled() { return labeled; }
        public void setLabeled(boolean labeled) { this.labeled = labeled; }

        public String getAppInstance() { return appInstance; }
        public void setAppInstance(String appInstance) { this.appInstance = appInstance; }

        public String getAppGroup() { return appGroup; }
        public void setAppGroup(String appGroup) { this.appGroup = appGroup; }
    }
}

