package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.entity.ProxyInstance;
import com.chaosblade.svc.reqrspproxy.repository.ProxyInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * K8s Proxy Manager — 替代 K8sTapManager。
 *
 * 核心操作：
 * 1. deployProxy()     — 部署 proxy-agent Deployment
 * 2. hijackService()   — Selector Swap 劫持目标 Service
 * 3. restoreService()  — 恢复原始 selector
 * 4. destroyProxy()    — 删除 proxy-agent Deployment
 */
@Component
public class K8sProxyManager {

    private static final Logger logger = LoggerFactory.getLogger(K8sProxyManager.class);

    @Autowired
    private KubernetesClient k8s;

    @Autowired
    private ProxyInstanceRepository proxyInstanceRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${proxy.agent.image:1.94.151.57:85/test/proxy-agent:latest}")
    private String proxyAgentImage;

    @Value("${proxy.agent.control-port:9090}")
    private int defaultControlPort;

    @Value("${proxy.agent.ready-timeout-sec:60}")
    private int readyTimeoutSec;

    /**
     * 部署 proxy-agent Deployment 并等待 Ready。
     *
     * @param recordingId   录制会话 ID
     * @param namespace     K8s 命名空间
     * @param targetService 目标服务名称
     * @param targetPodIp   目标 Pod 的 IP（proxy 转发目标）
     * @param targetPort    目标服务端口
     * @return 已更新的 ProxyInstance（含 Pod IP）
     */
    public ProxyInstance deployProxy(String recordingId, String namespace,
                                     String targetService, int targetPort) {
        String deploymentName = "proxy-agent-" + recordingId;
        String proxyLabel = deploymentName;
        // 使用影子 Service DNS 作为转发目标（兼容 pod-kill 等改变 Pod IP 的故障）
        String proxyTarget = "http://" + targetService + "-original." + namespace + ".svc.cluster.local:" + targetPort;

        // 避免 control port 与目标服务端口冲突
        int controlPort = defaultControlPort;
        if (targetPort == controlPort) {
            controlPort = controlPort + 1; // 9090 -> 9091
            logger.info("Control port {} conflicts with target port, using {} instead", defaultControlPort, controlPort);
        }

        logger.info("Deploying proxy-agent: deployment={}, target={}, namespace={}, controlPort={}",
                deploymentName, proxyTarget, namespace, controlPort);

        // 创建 ProxyInstance 记录
        ProxyInstance instance = new ProxyInstance();
        instance.setRecordingId(recordingId);
        instance.setNamespace(namespace);
        instance.setTargetService(targetService);
        instance.setProxyPort(targetPort);
        instance.setControlPort(controlPort);
        instance.setDeploymentName(deploymentName);
        instance.setStatus(ProxyInstance.Status.DEPLOYING);
        proxyInstanceRepo.save(instance);

        try {
            // 构建 proxy-agent Deployment
            Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata()
                        .withName(deploymentName)
                        .withNamespace(namespace)
                        .addToLabels("app", proxyLabel)
                        .addToLabels("managed-by", "reqrsp-proxy")
                        .addToLabels("recording-id", recordingId)
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                            .addToMatchLabels("app", proxyLabel)
                        .endSelector()
                        .withNewTemplate()
                            .withNewMetadata()
                                .addToLabels("app", proxyLabel)
                                .addToLabels("managed-by", "reqrsp-proxy")
                            .endMetadata()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("proxy-agent")
                                    .withImage(proxyAgentImage)
                                    .withImagePullPolicy("Always")
                                    .addNewPort().withContainerPort(targetPort).endPort()
                                    .addNewPort().withContainerPort(controlPort).endPort()
                                    .addNewEnv().withName("PROXY_TARGET")
                                        .withValue(proxyTarget).endEnv()
                                    .addNewEnv().withName("GRPC_TARGET")
                                        .withValue(targetService + "-original." + namespace + ".svc.cluster.local:" + targetPort).endEnv()
                                    .addNewEnv().withName("PROXY_PORT")
                                        .withValue(String.valueOf(targetPort)).endEnv()
                                    .addNewEnv().withName("CONTROL_PORT")
                                        .withValue(String.valueOf(controlPort)).endEnv()
                                    .addNewEnv().withName("SNAPSHOT_DIR")
                                        .withValue("/data/snapshots").endEnv()
                                    .addNewEnv().withName("INITIAL_MODE")
                                        .withValue("passthrough").endEnv()
                                .endContainer()
                                .addNewImagePullSecret()
                                    .withName("ghcr-secret")
                                .endImagePullSecret()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();

            k8s.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
            logger.info("Deployment {} created, waiting for ready...", deploymentName);

            // 等待 Pod Ready
            k8s.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName)
                    .waitUntilReady(readyTimeoutSec, TimeUnit.SECONDS);

            // 获取 Pod IP
            String podIp = getPodIp(namespace, proxyLabel);
            instance.setProxyPodIp(podIp);
            instance.setStatus(ProxyInstance.Status.RUNNING);
            proxyInstanceRepo.save(instance);

            logger.info("proxy-agent deployed: deployment={}, podIP={}", deploymentName, podIp);
            return instance;

        } catch (Exception e) {
            logger.error("Failed to deploy proxy-agent: {}", deploymentName, e);
            instance.setStatus(ProxyInstance.Status.ERROR);
            instance.setErrorMessage(e.getMessage());
            proxyInstanceRepo.save(instance);
            throw new RuntimeException("Failed to deploy proxy-agent: " + e.getMessage(), e);
        }
    }

    /**
     * Selector Swap 劫持目标 Service 的流量到 proxy-agent。
     *
     * 将目标 Service 的 selector 从 {app: targetService} 改为 {app: proxyLabel}，
     * 使 EndpointSlice Controller 自动将流量路由到 proxy Pod。
     */
    public void hijackService(String recordingId, String namespace, String targetService) {
        String deploymentName = "proxy-agent-" + recordingId;
        String proxyLabel = deploymentName;

        logger.info("Hijacking service {}/{} → selector app={}", namespace, targetService, proxyLabel);

        // 读取并保存原始 selector
        Service svc = k8s.services().inNamespace(namespace).withName(targetService).get();
        if (svc == null) {
            throw new RuntimeException("Service not found: " + namespace + "/" + targetService);
        }
        Map<String, String> originalSelector = svc.getSpec().getSelector();

        // 保存原始 selector 到数据库
        ProxyInstance instance = proxyInstanceRepo.findByRecordingId(recordingId)
                .orElseThrow(() -> new RuntimeException("ProxyInstance not found: " + recordingId));
        try {
            instance.setOriginalSelector(objectMapper.writeValueAsString(originalSelector));
            proxyInstanceRepo.save(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize selector: " + e.getMessage(), e);
        }

        // Selector Swap: 把 Service selector 指向 proxy pod 的 label
        java.util.Map<String, String> proxySelector = new java.util.HashMap<>();
        proxySelector.put("app", proxyLabel);
        svc.getSpec().setSelector(proxySelector);
        // 清理 managedFields 避免序列化冲突
        if (svc.getMetadata() != null) {
            svc.getMetadata().setManagedFields(null);
        }
        k8s.services().inNamespace(namespace).withName(targetService).patch(svc);

        logger.info("Service {}/{} selector swapped to app={}", namespace, targetService, proxyLabel);
    }

    /**
     * 恢复目标 Service 的原始 selector。
     */
    public void restoreService(String recordingId) {
        ProxyInstance instance = proxyInstanceRepo.findByRecordingId(recordingId)
                .orElseThrow(() -> new RuntimeException("ProxyInstance not found: " + recordingId));

        if (instance.getOriginalSelector() == null) {
            logger.warn("No original selector saved for recording {}, skip restore", recordingId);
            return;
        }

        String namespace = instance.getNamespace();
        String targetService = instance.getTargetService();

        logger.info("Restoring service {}/{} original selector", namespace, targetService);

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> originalSelector = objectMapper.readValue(
                    instance.getOriginalSelector(), Map.class);

            Service svc = k8s.services().inNamespace(namespace).withName(targetService).get();
            if (svc != null) {
                svc.getSpec().setSelector(originalSelector);
                if (svc.getMetadata() != null) {
                    svc.getMetadata().setManagedFields(null);
                }
                k8s.services().inNamespace(namespace).withName(targetService).patch(svc);
                logger.info("Service {}/{} selector restored to {}", namespace, targetService, originalSelector);
            }

            instance.setStatus(ProxyInstance.Status.RESTORING);
            proxyInstanceRepo.save(instance);

        } catch (Exception e) {
            logger.error("Failed to restore service {}/{}", namespace, targetService, e);
            instance.setErrorMessage("Restore failed: " + e.getMessage());
            proxyInstanceRepo.save(instance);
            throw new RuntimeException("Failed to restore service: " + e.getMessage(), e);
        }
    }

    /**
     * 创建影子 Service（{svc}-original），保留原始 selector，
     * 用于 proxy-agent 的转发目标（避免 Selector Swap 后循环）。
     */
    public void createShadowService(String namespace, String targetService) {
        Service original = k8s.services().inNamespace(namespace).withName(targetService).get();
        if (original == null) {
            throw new RuntimeException("Service not found: " + namespace + "/" + targetService);
        }

        String shadowName = targetService + "-original";
        Map<String, String> originalSelector = original.getSpec().getSelector();
        ServicePort firstPort = original.getSpec().getPorts().get(0);

        Service shadow = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata()
                    .withName(shadowName)
                    .withNamespace(namespace)
                    .addToLabels("managed-by", "reqrsp-proxy")
                    .addToLabels("shadow-of", targetService)
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(originalSelector)
                    .addNewPort()
                        .withPort(firstPort.getPort())
                        .withNewTargetPort(firstPort.getTargetPort().getIntVal())
                        .withProtocol("TCP")
                    .endPort()
                .endSpec()
                .build();

        k8s.services().inNamespace(namespace).createOrReplace(shadow);
        logger.info("Shadow service created: {}/{}", namespace, shadowName);
    }

    /**
     * 删除影子 Service。
     */
    public void deleteShadowService(String namespace, String targetService) {
        String shadowName = targetService + "-original";
        try {
            k8s.services().inNamespace(namespace).withName(shadowName).delete();
            logger.info("Shadow service deleted: {}/{}", namespace, shadowName);
        } catch (Exception e) {
            logger.warn("Failed to delete shadow service {}/{}: {}", namespace, shadowName, e.getMessage());
        }
    }

    /**
     * 重启目标 Deployment（通过添加 annotation 触发 rolling update），
     * 强制 Pod 重建以清除缓存的 gRPC 长连接。
     */
    public void restartDeployment(String namespace, String deploymentName) {
        try {
            io.fabric8.kubernetes.api.model.apps.Deployment deploy =
                    k8s.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deploy == null) {
                logger.warn("Deployment not found for restart: {}/{}", namespace, deploymentName);
                return;
            }
            // 添加/更新 annotation 触发 rolling update（等同于 kubectl rollout restart）
            if (deploy.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
                deploy.getSpec().getTemplate().getMetadata().setAnnotations(new java.util.HashMap<>());
            }
            deploy.getSpec().getTemplate().getMetadata().getAnnotations()
                    .put("kubectl.kubernetes.io/restartedAt", java.time.Instant.now().toString());
            if (deploy.getMetadata() != null) {
                deploy.getMetadata().setManagedFields(null);
            }
            k8s.apps().deployments().inNamespace(namespace).withName(deploymentName).patch(deploy);
            logger.info("Deployment {}/{} restart triggered", namespace, deploymentName);
        } catch (Exception e) {
            logger.warn("Failed to restart deployment {}/{}: {}", namespace, deploymentName, e.getMessage());
        }
    }

    /**
     * 删除 proxy-agent Deployment。
     */
    public void destroyProxy(String recordingId) {
        ProxyInstance instance = proxyInstanceRepo.findByRecordingId(recordingId).orElse(null);
        if (instance == null) {
            logger.warn("ProxyInstance not found for recording {}, nothing to destroy", recordingId);
            return;
        }

        String namespace = instance.getNamespace();
        String deploymentName = instance.getDeploymentName();
        String targetService = instance.getTargetService();

        // 1. 恢复 Service selector（防止残留劫持）
        try {
            restoreService(recordingId);
        } catch (Exception e) {
            logger.warn("Failed to restore service during destroyProxy for {}: {}", recordingId, e.getMessage());
        }

        // 2. 删除 proxy-agent Deployment
        logger.info("Destroying proxy-agent deployment: {}/{}", namespace, deploymentName);
        try {
            k8s.apps().deployments().inNamespace(namespace)
                    .withName(deploymentName).delete();
            logger.info("Deployment {} deleted", deploymentName);
        } catch (Exception e) {
            logger.error("Failed to delete deployment {}", deploymentName, e);
        }

        // 3. 删除影子 Service
        try {
            deleteShadowService(namespace, targetService);
        } catch (Exception e) {
            logger.warn("Failed to delete shadow service for {}: {}", targetService, e.getMessage());
        }

        instance.setStatus(ProxyInstance.Status.DESTROYED);
        proxyInstanceRepo.save(instance);
    }

    /**
     * 获取目标服务的第一个 Endpoint Pod IP。
     */
    public String getServicePodIp(String namespace, String serviceName) {
        Endpoints endpoints = k8s.endpoints().inNamespace(namespace).withName(serviceName).get();
        if (endpoints != null && endpoints.getSubsets() != null && !endpoints.getSubsets().isEmpty()) {
            EndpointSubset subset = endpoints.getSubsets().get(0);
            if (subset.getAddresses() != null && !subset.getAddresses().isEmpty()) {
                return subset.getAddresses().get(0).getIp();
            }
        }
        throw new RuntimeException("No endpoint found for service " + namespace + "/" + serviceName);
    }

    /**
     * 获取目标 Service 的端口。
     */
    public int getServicePort(String namespace, String serviceName) {
        Service svc = k8s.services().inNamespace(namespace).withName(serviceName).get();
        if (svc != null && svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty()) {
            return svc.getSpec().getPorts().get(0).getPort();
        }
        throw new RuntimeException("No port found for service " + namespace + "/" + serviceName);
    }

    /**
     * 获取 ProxyInstance。
     */
    public ProxyInstance getProxyInstance(String recordingId) {
        return proxyInstanceRepo.findByRecordingId(recordingId).orElse(null);
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private String getPodIp(String namespace, String labelValue) {
        PodList pods = k8s.pods().inNamespace(namespace)
                .withLabel("app", labelValue)
                .list();
        if (pods.getItems().isEmpty()) {
            throw new RuntimeException("No pod found with label app=" + labelValue);
        }
        // 找第一个 Running 的 Pod
        for (Pod pod : pods.getItems()) {
            if ("Running".equals(pod.getStatus().getPhase())) {
                return pod.getStatus().getPodIP();
            }
        }
        // 如果没有 Running 的，返回第一个的 IP
        return pods.getItems().get(0).getStatus().getPodIP();
    }
}
