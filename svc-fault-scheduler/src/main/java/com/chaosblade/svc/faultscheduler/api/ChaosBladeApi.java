/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.faultscheduler.api;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChaosBlade API 客户端
 * 封装对 Kubernetes ChaosBlade CRD 的操作
 */
@Component
public class ChaosBladeApi {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosBladeApi.class);
    
    private static final CustomResourceDefinitionContext CTX =
            new CustomResourceDefinitionContext.Builder()
                    .withGroup("chaosblade.io")
                    .withVersion("v1alpha1")
                    .withPlural("chaosblades")
                    .withScope("Cluster")
                    .build();
    
    private final KubernetesClient client;
    
    public ChaosBladeApi(KubernetesClient client) {
        this.client = client;
    }
    
    /**
     * 创建 ChaosBlade 资源
     * 
     * @param name 资源名称
     * @param labels 标签
     * @param spec 规格定义
     * @return 创建的资源
     */
    public GenericKubernetesResource create(String name, Map<String, String> labels, Map<String, Object> spec) {
        try {
            logger.info("Creating ChaosBlade resource: {}", name);
            logger.debug("ChaosBlade spec: {}", spec);

            // 使用 kubectl 命令行工具创建资源，绕过 Fabric8 序列化问题
            String yamlContent = buildChaosBladeYaml(name, labels, spec);
            logger.debug("Generated ChaosBlade YAML: {}", yamlContent);

            // 使用 kubectl apply 创建资源
            boolean success = createResourceWithKubectl(yamlContent);
            if (!success) {
                throw new RuntimeException("Failed to create resource with kubectl");
            }

            // 等待一下让资源创建完成
            Thread.sleep(1000);

            // 获取创建的资源
            GenericKubernetesResource created = get(name);
            if (created == null) {
                throw new RuntimeException("Resource created but not found: " + name);
            }

            logger.info("Successfully created ChaosBlade resource: {}", name);
            return created;

        } catch (Exception e) {
            logger.error("Failed to create ChaosBlade resource: {}", name, e);
            throw new RuntimeException("Failed to create ChaosBlade resource: " + name, e);
        }
    }

    /**
     * 使用 kubectl 命令行工具创建资源，绕过 Jackson 序列化问题
     */
    private boolean createResourceWithKubectl(String yamlContent) {
        try {
            logger.debug("Creating resource with kubectl command");

            // 创建临时文件
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("chaosblade-", ".yaml");
            java.nio.file.Files.write(tempFile, yamlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            try {
                // 构建 kubectl 命令，使用集群内的 service account
                ProcessBuilder pb = new ProcessBuilder(
                        "kubectl", "apply", "-f", tempFile.toString()
                );

                // 设置环境变量，使用当前的 Kubernetes 配置
                java.util.Map<String, String> env = pb.environment();

                // 如果在集群内运行，kubectl 会自动使用 service account
                // 如果在集群外，需要设置 KUBECONFIG
                String kubeconfig = System.getenv("KUBECONFIG");
                if (kubeconfig == null) {
                    // 尝试使用默认的 kubeconfig 路径
                    String home = System.getProperty("user.home");
                    if (home != null) {
                        java.nio.file.Path defaultKubeconfig = java.nio.file.Paths.get(home, ".kube", "config");
                        if (java.nio.file.Files.exists(defaultKubeconfig)) {
                            env.put("KUBECONFIG", defaultKubeconfig.toString());
                        }
                    }
                }

                // 设置工作目录
                pb.directory(new java.io.File("/tmp"));

                logger.debug("Executing kubectl command: {}", String.join(" ", pb.command()));

                Process process = pb.start();

                // 读取输出
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String error = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    logger.debug("kubectl apply succeeded: {}", output);
                    return true;
                } else {
                    logger.error("kubectl apply failed with exit code {}: {}", exitCode, error);
                    return false;
                }

            } finally {
                // 清理临时文件
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    logger.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to execute kubectl command", e);
            return false;
        }
    }

    /**
     * 构建 ChaosBlade YAML 内容
     */
    @SuppressWarnings("unchecked")
    private String buildChaosBladeYaml(String name, Map<String, String> labels, Map<String, Object> spec) {
        try {
            StringBuilder yaml = new StringBuilder();
            yaml.append("apiVersion: chaosblade.io/v1alpha1\n");
            yaml.append("kind: ChaosBlade\n");
            yaml.append("metadata:\n");
            yaml.append("  name: ").append(name).append("\n");

            if (labels != null && !labels.isEmpty()) {
                yaml.append("  labels:\n");
                for (Map.Entry<String, String> entry : labels.entrySet()) {
                    yaml.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            yaml.append("spec:\n");

            // 处理 experiments 数组
            Object experimentsObj = spec.get("experiments");
            if (experimentsObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> experiments = (java.util.List<Map<String, Object>>) experimentsObj;
                yaml.append("  experiments:\n");

                for (Map<String, Object> experiment : experiments) {
                    yaml.append("  - scope: ").append(experiment.get("scope")).append("\n");
                    yaml.append("    target: ").append(experiment.get("target")).append("\n");
                    yaml.append("    action: ").append(experiment.get("action")).append("\n");

                    // 处理 desc 字段（可选）
                    if (experiment.containsKey("desc")) {
                        yaml.append("    desc: \"").append(experiment.get("desc")).append("\"\n");
                    }

                    // 处理 matchers 数组
                    Object matchersObj = experiment.get("matchers");
                    if (matchersObj instanceof java.util.List) {
                        java.util.List<Map<String, Object>> matchers = (java.util.List<Map<String, Object>>) matchersObj;
                        yaml.append("    matchers:\n");

                        for (Map<String, Object> matcher : matchers) {
                            yaml.append("    - name: ").append(matcher.get("name")).append("\n");

                            Object valueObj = matcher.get("value");
                            if (valueObj instanceof java.util.List) {
                                java.util.List<String> values = (java.util.List<String>) valueObj;
                                yaml.append("      value:\n");
                                for (String value : values) {
                                    yaml.append("      - \"").append(value).append("\"\n");
                                }
                            }
                        }
                    }
                }
            }

            return yaml.toString();

        } catch (Exception e) {
            logger.error("Failed to build ChaosBlade YAML", e);
            throw new RuntimeException("Failed to build ChaosBlade YAML", e);
        }
    }








    
    /**
     * 获取 ChaosBlade 资源
     * 
     * @param name 资源名称
     * @return 资源对象，如果不存在返回 null
     */
    public GenericKubernetesResource get(String name) {
        try {
            logger.debug("Retrieving ChaosBlade resource: {}", name);
            
            GenericKubernetesResource blade = client.genericKubernetesResources(CTX)
                    .withName(name)
                    .get();
            
            if (blade == null) {
                logger.debug("ChaosBlade resource not found: {}", name);
            } else {
                logger.debug("Successfully retrieved ChaosBlade resource: {}", name);
            }
            
            return blade;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve ChaosBlade resource: {}", name, e);
            throw new RuntimeException("Failed to retrieve ChaosBlade resource: " + name, e);
        }
    }
    
    /**
     * 删除 ChaosBlade 资源
     * 
     * @param name 资源名称
     * @return 是否删除成功
     */
    public boolean delete(String name) {
        try {
            logger.info("Deleting ChaosBlade resource: {}", name);
            
            List<io.fabric8.kubernetes.api.model.StatusDetails> result = client.genericKubernetesResources(CTX)
                    .withName(name)
                    .delete();
            
            boolean deleted = result != null && !result.isEmpty();
            
            if (deleted) {
                logger.info("Successfully deleted ChaosBlade resource: {}", name);
            } else {
                logger.warn("ChaosBlade resource not found for deletion: {}", name);
            }
            
            return deleted;
            
        } catch (Exception e) {
            logger.error("Failed to delete ChaosBlade resource: {}", name, e);
            throw new RuntimeException("Failed to delete ChaosBlade resource: " + name, e);
        }
    }
    
    /**
     * 获取 ChaosBlade 资源的状态
     * 
     * @param blade ChaosBlade 资源
     * @return 状态信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> status(GenericKubernetesResource blade) {
        if (blade == null) {
            logger.debug("Cannot get status from null ChaosBlade resource");
            return Map.of();
        }
        
        try {
            Object statusObj = blade.getAdditionalProperties().get("status");
            
            if (statusObj instanceof Map<?, ?> statusMap) {
                Map<String, Object> status = (Map<String, Object>) statusMap;
                logger.debug("Retrieved status for ChaosBlade: {}", status);
                return status;
            } else {
                logger.debug("No status found in ChaosBlade resource");
                return Map.of();
            }
            
        } catch (Exception e) {
            logger.error("Failed to extract status from ChaosBlade resource", e);
            return Map.of();
        }
    }
    
    /**
     * 获取 ChaosBlade 相关的事件
     * 
     * @param bladeName ChaosBlade 名称
     * @param limit 最大事件数量
     * @return 事件列表
     */
    public List<Map<String, Object>> eventsForBlade(String bladeName, int limit) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        try {
            logger.debug("Retrieving events for ChaosBlade: {}, limit: {}", bladeName, limit);
            
            // 获取 core/v1 事件
            var coreEvents = client.v1().events().inAnyNamespace()
                    .withField("involvedObject.kind", "ChaosBlade")
                    .withField("involvedObject.name", bladeName)
                    .list()
                    .getItems();
            
            coreEvents.stream()
                    .limit(limit)
                    .forEach(event -> {
                        events.add(Map.of(
                                "type", event.getType() != null ? event.getType() : "Unknown",
                                "reason", event.getReason() != null ? event.getReason() : "Unknown",
                                "message", event.getMessage() != null ? event.getMessage() : "",
                                "lastTimestamp", event.getLastTimestamp() != null ? 
                                        event.getLastTimestamp().toString() : "",
                                "source", "core/v1"
                        ));
                    });
            
            // 尝试获取 events.k8s.io/v1 事件
            try {
                var eventsV1 = client.events().v1().events().inAnyNamespace()
                        .withField("regarding.kind", "ChaosBlade")
                        .withField("regarding.name", bladeName)
                        .list()
                        .getItems();
                
                eventsV1.stream()
                        .limit(Math.max(0, limit - events.size()))
                        .forEach(event -> {
                            events.add(Map.of(
                                    "type", event.getType() != null ? event.getType() : "Unknown",
                                    "reason", event.getReason() != null ? event.getReason() : "Unknown",
                                    "note", event.getNote() != null ? event.getNote() : "",
                                    "eventTime", event.getEventTime() != null ? 
                                            event.getEventTime().toString() : "",
                                    "source", "events.k8s.io/v1"
                            ));
                        });
                        
            } catch (Exception e) {
                logger.debug("Failed to retrieve events.k8s.io/v1 events (may not be available): {}", e.getMessage());
            }
            
            logger.debug("Retrieved {} events for ChaosBlade: {}", events.size(), bladeName);
            return events;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for ChaosBlade: {}", bladeName, e);
            return events; // 返回已收集的事件，即使部分失败
        }
    }
    
    /**
     * 检查 ChaosBlade 资源是否存在
     * 
     * @param name 资源名称
     * @return 是否存在
     */
    public boolean exists(String name) {
        try {
            GenericKubernetesResource blade = get(name);
            return blade != null;
        } catch (Exception e) {
            logger.error("Failed to check ChaosBlade existence: {}", name, e);
            return false;
        }
    }
}
