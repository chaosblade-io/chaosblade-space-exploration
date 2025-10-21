package com.chaosblade.svc.reqrspproxy.service;

import com.chaosblade.svc.reqrspproxy.config.RecordingConfig;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** Kubernetes Tap 管理器 */
@Component
public class K8sTapManager {

  private static final Logger logger = LoggerFactory.getLogger(K8sTapManager.class);

  @Autowired private KubernetesClient k8s;

  @Autowired private RecordingConfig recordingConfig;

  @Autowired private TaskScheduler taskScheduler;

  // 管理每个 recording 的自动停止任务句柄，便于取消
  private final java.util.concurrent.ConcurrentHashMap<
          String, java.util.concurrent.ScheduledFuture<?>>
      autoStopTasks = new java.util.concurrent.ConcurrentHashMap<>();

  /** 创建或更新 ConfigMap */
  public void applyOrUpdateConfigMap(String namespace, String cmName, String envoyYaml) {
    logger.info("Creating/updating ConfigMap {} in namespace {}", cmName, namespace);

    try {
      ConfigMap cm =
          new ConfigMapBuilder()
              .withNewMetadata()
              .withName(cmName)
              .withNamespace(namespace)
              .addToLabels("app", "envoy-tap")
              .addToLabels("managed-by", "reqrsp-proxy")
              .endMetadata()
              .addToData("envoy.yaml", envoyYaml)
              .build();

      k8s.configMaps().inNamespace(namespace).resource(cm).createOrReplace();
      logger.info("ConfigMap {} created/updated successfully", cmName);

    } catch (KubernetesClientException e) {
      logger.error("Failed to create/update ConfigMap {}: {}", cmName, e.getMessage(), e);
      throw new RuntimeException("Failed to create/update ConfigMap: " + e.getMessage(), e);
    }
  }

  /** 注入或更新 Envoy sidecar 包含重试机制处理并发冲突 */
  public void injectOrUpdateSidecar(String namespace, String deploymentName, String cmName) {
    logger.info(
        "Injecting/updating Envoy sidecar in deployment {} in namespace {}",
        deploymentName,
        namespace);

    int maxRetries = 3;
    int retryDelay = 1000; // 1 秒

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        k8s.apps()
            .deployments()
            .inNamespace(namespace)
            .withName(deploymentName)
            .edit(
                deployment -> {
                  PodSpec spec = deployment.getSpec().getTemplate().getSpec();

                  // 确保 volumes 存在
                  ensureVolume(
                      spec,
                      "envoy-config",
                      new VolumeBuilder()
                          .withName("envoy-config")
                          .withConfigMap(
                              new ConfigMapVolumeSourceBuilder().withName(cmName).build())
                          .build());

                  ensureVolume(
                      spec,
                      "envoy-taps",
                      new VolumeBuilder()
                          .withName("envoy-taps")
                          .withEmptyDir(new EmptyDirVolumeSource())
                          .build());

                  // 创建 Envoy 容器
                  Container envoyContainer = createEnvoyContainer();

                  // 添加或替换 Envoy 容器
                  List<Container> containers = spec.getContainers();
                  if (containers == null) {
                    containers = new ArrayList<>();
                    spec.setContainers(containers);
                  }

                  final List<Container> finalContainers = containers;
                  int envoyIndex =
                      IntStream.range(0, finalContainers.size())
                          .filter(i -> "envoy".equals(finalContainers.get(i).getName()))
                          .findFirst()
                          .orElse(-1);

                  if (envoyIndex >= 0) {
                    finalContainers.set(envoyIndex, envoyContainer);
                    logger.debug("Replaced existing Envoy container");
                  } else {
                    finalContainers.add(envoyContainer);
                    logger.debug("Added new Envoy container");
                  }

                  // 清理服务端管理字段，避免序列化 FieldsV1/managedFields
                  if (deployment.getMetadata() != null) {
                    deployment.getMetadata().setManagedFields(null);
                  }
                  return deployment;
                });

        logger.info(
            "Envoy sidecar injected/updated successfully in deployment {} (attempt {})",
            deploymentName,
            attempt);

        // 触发滚动更新以应用新的配置
        triggerRollingUpdate(namespace, deploymentName);
        return; // 成功，退出重试循环

      } catch (KubernetesClientException e) {
        if (e.getCode() == 409 && attempt < maxRetries) {
          // 409 Conflict - 资源版本冲突，重试
          logger.warn(
              "Deployment {} conflict detected during sidecar injection (attempt {}), retrying in"
                  + " {}ms: {}",
              deploymentName,
              attempt,
              retryDelay,
              e.getMessage());

          try {
            Thread.sleep(retryDelay);
            retryDelay *= 2; // 指数退避
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying sidecar injection", ie);
          }
        } else {
          // 其他错误或重试次数用完
          logger.error(
              "Failed to inject/update sidecar in deployment {} after {} attempts: {}",
              deploymentName,
              attempt,
              e.getMessage(),
              e);
          throw new RuntimeException("Failed to inject/update sidecar: " + e.getMessage(), e);
        }
      }
    }
  }

  /** 创建 Envoy 容器 */
  private Container createEnvoyContainer() {
    return new ContainerBuilder()
        .withName("envoy")
        .withImage(recordingConfig.getEnvoy().getImage())
        .withArgs("-c", "/etc/envoy/envoy.yaml", "--log-level", "info")
        .addToPorts(
            new ContainerPortBuilder()
                .withContainerPort(recordingConfig.getEnvoy().getPort())
                .withName("proxy")
                .build())
        .addToPorts(
            new ContainerPortBuilder()
                .withContainerPort(recordingConfig.getEnvoy().getAdminPort())
                .withName("admin")
                .build())
        .addToVolumeMounts(
            new VolumeMountBuilder()
                .withName("envoy-config")
                .withMountPath("/etc/envoy")
                .withReadOnly(true)
                .build())
        .addToVolumeMounts(
            new VolumeMountBuilder()
                .withName("envoy-taps")
                .withMountPath(recordingConfig.getEnvoy().getTapDir())
                .build())
        .withResources(
            new ResourceRequirementsBuilder()
                .addToRequests(Map.of("cpu", new Quantity("50m"), "memory", new Quantity("128Mi")))
                .addToLimits(Map.of("cpu", new Quantity("1"), "memory", new Quantity("512Mi")))
                .build())
        .build();
  }

  /** 重定向 Service 到 Envoy 端口 */
  public void redirectServiceToEnvoy(String namespace, String serviceName, int targetPort) {
    logger.info(
        "Redirecting service {} to port {} in namespace {}", serviceName, targetPort, namespace);

    try {
      Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
      if (service == null) {
        throw new RuntimeException("Service not found: " + serviceName);
      }

      // 更新第一个端口的 targetPort
      if (service.getSpec().getPorts() != null && !service.getSpec().getPorts().isEmpty()) {
        service.getSpec().getPorts().get(0).setTargetPort(new IntOrString(targetPort));
        // 清理服务端管理字段，避免序列化 FieldsV1/managedFields
        if (service.getMetadata() != null) {
          service.getMetadata().setManagedFields(null);
        }
        k8s.services().inNamespace(namespace).resource(service).replace();
        logger.info("Service {} redirected to port {} successfully", serviceName, targetPort);
      } else {
        throw new RuntimeException("Service has no ports defined: " + serviceName);
      }

    } catch (KubernetesClientException e) {
      logger.error(
          "Failed to redirect service {} to port {}: {}",
          serviceName,
          targetPort,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to redirect service: " + e.getMessage(), e);
    }
  }

  /** 等待部署就绪 */
  public void waitRolloutReady(String namespace, String deploymentName) {
    logger.info("Waiting for deployment {} to be ready in namespace {}", deploymentName, namespace);

    try {
      k8s.apps()
          .deployments()
          .inNamespace(namespace)
          .withName(deploymentName)
          .waitUntilReady(5, TimeUnit.MINUTES);

      logger.info("Deployment {} is ready", deploymentName);

    } catch (KubernetesClientException e) {
      logger.error(
          "Failed to wait for deployment {} readiness: {}", deploymentName, e.getMessage(), e);
      throw new RuntimeException("Deployment readiness timeout: " + e.getMessage(), e);
    }
  }

  /** 移除 sidecar 或禁用 Tap */
  public void removeSidecarOrDisableTap(String namespace, String deploymentName) {
    logger.info(
        "Removing Envoy sidecar from deployment {} in namespace {}", deploymentName, namespace);

    try {
      k8s.apps()
          .deployments()
          .inNamespace(namespace)
          .withName(deploymentName)
          .edit(
              deployment -> {
                PodSpec spec = deployment.getSpec().getTemplate().getSpec();

                // 移除 Envoy 容器
                List<Container> containers = spec.getContainers();
                if (containers != null) {
                  containers.removeIf(container -> "envoy".equals(container.getName()));
                }

                // 移除相关 volumes
                List<Volume> volumes = spec.getVolumes();
                if (volumes != null) {
                  volumes.removeIf(
                      volume ->
                          "envoy-config".equals(volume.getName())
                              || "envoy-taps".equals(volume.getName()));
                }

                // 清理服务端管理字段，避免序列化 FieldsV1/managedFields
                if (deployment.getMetadata() != null) {
                  deployment.getMetadata().setManagedFields(null);
                }

                return deployment;
              });

      logger.info("Envoy sidecar removed from deployment {} successfully", deploymentName);

    } catch (KubernetesClientException e) {
      logger.error(
          "Failed to remove sidecar from deployment {}: {}", deploymentName, e.getMessage(), e);
      throw new RuntimeException("Failed to remove sidecar: " + e.getMessage(), e);
    }
  }

  /** 调度自动停止任务 */
  public void scheduleAutoStop(String recordingId, Integer durationSec, Runnable stopCallback) {
    if (durationSec == null || durationSec <= 0) {
      logger.debug("No auto-stop scheduled for recording {}", recordingId);
      return;
    }

    logger.info("Scheduling auto-stop for recording {} in {} seconds", recordingId, durationSec);

    java.util.Date startAt = new java.util.Date(System.currentTimeMillis() + durationSec * 1000L);
    java.util.concurrent.ScheduledFuture<?> future =
        taskScheduler.schedule(
            () -> {
              logger.info("Auto-stopping recording {} after {} seconds", recordingId, durationSec);
              try {
                stopCallback.run();
              } catch (Exception e) {
                logger.error(
                    "Failed to auto-stop recording {}: {}", recordingId, e.getMessage(), e);
              } finally {
                autoStopTasks.remove(recordingId);
              }
            },
            startAt);
    if (future != null) {
      autoStopTasks.put(recordingId, future);
    }
  }

  /** 取消自动停止任务 */
  public void cancelAutoStop(String recordingId) {
    try {
      java.util.concurrent.ScheduledFuture<?> f = autoStopTasks.remove(recordingId);
      if (f != null) {
        boolean cancelled = f.cancel(false);
        logger.debug("Auto-stop task for {} cancelled: {}", recordingId, cancelled);
      }
    } catch (Exception e) {
      logger.warn("Failed to cancel auto-stop for {}: {}", recordingId, e.getMessage());
    }
  }

  /** 确保 Volume 存在 */
  private void ensureVolume(PodSpec spec, String name, Volume volume) {
    List<Volume> volumes = spec.getVolumes();
    if (volumes == null) {
      volumes = new ArrayList<>();
      spec.setVolumes(volumes);
    }

    final List<Volume> finalVolumes = volumes;
    int index =
        IntStream.range(0, finalVolumes.size())
            .filter(i -> name.equals(finalVolumes.get(i).getName()))
            .findFirst()
            .orElse(-1);

    if (index >= 0) {
      finalVolumes.set(index, volume);
    } else {
      finalVolumes.add(volume);
    }
  }

  /** 获取 Service 的原始 targetPort */
  public Integer getServiceOriginalTargetPort(String namespace, String serviceName) {
    try {
      Service service = k8s.services().inNamespace(namespace).withName(serviceName).get();
      if (service == null) {
        throw new RuntimeException("Service not found: " + serviceName);
      }

      if (service.getSpec().getPorts() != null && !service.getSpec().getPorts().isEmpty()) {
        IntOrString targetPort = service.getSpec().getPorts().get(0).getTargetPort();
        return targetPort != null ? targetPort.getIntVal() : null;
      }

      return null;

    } catch (KubernetesClientException e) {
      logger.error(
          "Failed to get original target port for service {}: {}", serviceName, e.getMessage(), e);
      throw new RuntimeException("Failed to get service target port: " + e.getMessage(), e);
    }
  }

  /** 获取 Service 端口（兼容方法） */
  public Integer getServicePort(String namespace, String serviceName) {
    return getServiceOriginalTargetPort(namespace, serviceName);
  }

  /** 恢复 Service 到原始配置 */
  public void restoreServiceToOriginal(String namespace, String serviceName, int originalPort) {
    logger.info(
        "Restoring service {} to original port {} in namespace {}",
        serviceName,
        originalPort,
        namespace);
    redirectServiceToEnvoy(namespace, serviceName, originalPort);
  }

  /** 移除 Envoy sidecar（别名方法） */
  public void removeSidecar(String namespace, String deploymentName) {
    logger.info("Removing sidecar from deployment {} in namespace {}", deploymentName, namespace);
    removeSidecarOrDisableTap(namespace, deploymentName);
  }

  /** 删除 ConfigMap */
  public void deleteConfigMap(String namespace, String configMapName) {
    logger.info("Deleting ConfigMap {} in namespace {}", configMapName, namespace);

    try {
      k8s.configMaps().inNamespace(namespace).withName(configMapName).delete();
      logger.info("ConfigMap {} deleted successfully", configMapName);

    } catch (KubernetesClientException e) {
      logger.error("Failed to delete ConfigMap {}: {}", configMapName, e.getMessage(), e);
      throw new RuntimeException("Failed to delete ConfigMap: " + e.getMessage(), e);
    }
  }

  /** 公共方法：触发配置变更后的滚动更新 */
  public void triggerRollingUpdateForConfigChange(String namespace, String deploymentName) {
    logger.info(
        "Triggering rolling update for config change in deployment {} in namespace {}",
        deploymentName,
        namespace);
    triggerRollingUpdate(namespace, deploymentName);
  }

  /** 触发 Deployment 的滚动更新 通过添加/更新 annotation 来强制 Pod 重启 包含重试机制处理并发冲突 */
  private void triggerRollingUpdate(String namespace, String deploymentName) {
    logger.info(
        "Triggering rolling update for deployment {} in namespace {}", deploymentName, namespace);

    int maxRetries = 3;
    int retryDelay = 1000; // 1 秒

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        // 使用 kubectl rollout restart 的等效操作
        // 添加或更新 spec.template.metadata.annotations 中的重启时间戳
        String restartAnnotation = "kubectl.kubernetes.io/restartedAt";
        String timestamp = java.time.Instant.now().toString();

        k8s.apps()
            .deployments()
            .inNamespace(namespace)
            .withName(deploymentName)
            .edit(
                deployment -> {
                  // 确保 template.metadata 存在
                  if (deployment.getSpec().getTemplate().getMetadata() == null) {
                    deployment.getSpec().getTemplate().setMetadata(new ObjectMetaBuilder().build());
                  }

                  // 确保 annotations 存在
                  if (deployment.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
                    deployment
                        .getSpec()
                        .getTemplate()
                        .getMetadata()
                        .setAnnotations(new HashMap<>());
                  }

                  // 添加重启注解
                  deployment
                      .getSpec()
                      .getTemplate()
                      .getMetadata()
                      .getAnnotations()
                      .put(restartAnnotation, timestamp);

                  // 清理管理字段
                  if (deployment.getMetadata() != null) {
                    deployment.getMetadata().setManagedFields(null);
                  }

                  return deployment;
                });

        logger.info(
            "Rolling update triggered successfully for deployment {} (attempt {})",
            deploymentName,
            attempt);

        // 等待滚动更新开始
        waitForRollingUpdateToStart(namespace, deploymentName);
        return; // 成功，退出重试循环

      } catch (KubernetesClientException e) {
        if (e.getCode() == 409 && attempt < maxRetries) {
          // 409 Conflict - 资源版本冲突，重试
          logger.warn(
              "Deployment {} conflict detected (attempt {}), retrying in {}ms: {}",
              deploymentName,
              attempt,
              retryDelay,
              e.getMessage());

          try {
            Thread.sleep(retryDelay);
            retryDelay *= 2; // 指数退避
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying rolling update", ie);
          }
        } else {
          // 其他错误或重试次数用完
          logger.error(
              "Failed to trigger rolling update for deployment {} after {} attempts: {}",
              deploymentName,
              attempt,
              e.getMessage(),
              e);
          throw new RuntimeException("Failed to trigger rolling update: " + e.getMessage(), e);
        }
      }
    }
  }

  /** 等待滚动更新开始 */
  private void waitForRollingUpdateToStart(String namespace, String deploymentName) {
    logger.info("Waiting for rolling update to start for deployment {}", deploymentName);

    try {
      // 等待最多 30 秒，检查 Deployment 状态
      for (int i = 0; i < 30; i++) {
        Deployment deployment =
            k8s.apps().deployments().inNamespace(namespace).withName(deploymentName).get();

        if (deployment != null && deployment.getStatus() != null) {
          Integer updatedReplicas = deployment.getStatus().getUpdatedReplicas();
          Integer replicas = deployment.getStatus().getReplicas();

          if (updatedReplicas != null && replicas != null && updatedReplicas > 0) {
            logger.info(
                "Rolling update started for deployment {}, updated replicas: {}/{}",
                deploymentName,
                updatedReplicas,
                replicas);
            return;
          }
        }

        Thread.sleep(1000); // 等待 1 秒
      }

      logger.warn(
          "Rolling update may not have started within 30 seconds for deployment {}",
          deploymentName);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("Interrupted while waiting for rolling update to start: {}", e.getMessage());
    } catch (Exception e) {
      logger.warn("Error while waiting for rolling update to start: {}", e.getMessage());
    }
  }
}
