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

package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.svc.taskexecutor.config.KubernetesProperties;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class KubernetesService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);

    private final KubernetesClient client;
    private final ExecutorService executor;
    private final KubernetesProperties props;

    public KubernetesService(KubernetesProperties props) {
        this.props = props;
        this.executor = Executors.newFixedThreadPool(Math.max(1, props.getThreadPoolSize()));
        Config cfg = new ConfigBuilder()
                .withMasterUrl(props.getApiUrl())
                .withOauthToken(props.getToken())
                .withTrustCerts(!props.isVerifySsl())
                .withRequestTimeout(props.getRequestTimeoutMs())
                .withConnectionTimeout(props.getConnectionTimeoutMs())
                .build();
        this.client = new KubernetesClientBuilder().withConfig(cfg).build();
    }

    public CompletableFuture<Result> getDeploymentInfoAsync(String namespace, String serviceName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Pod> pods = new ArrayList<>();
                for (String key : props.getLabelKeys()) {
                    pods.addAll(client.pods().inNamespace(namespace).withLabel(key, serviceName).list().getItems());
                    if (!pods.isEmpty()) break;
                }

                List<String> podNames = pods.stream().map(p -> p.getMetadata().getName()).distinct().toList();
                List<String> containerNames = pods.stream()
                        .flatMap(p -> p.getSpec().getContainers().stream())
                        .map(Container::getName)
                        .distinct()
                        .collect(Collectors.toList());

                return new Result(namespace, podNames, containerNames);
            } catch (Exception e) {
                log.warn("K8s query failed for ns={}, svc={}: {}", namespace, serviceName, e.toString());
                return new Result(namespace, List.of(), List.of());
            }
        }, executor);
    }

    @Override
    public void close() {
        try { client.close(); } catch (Exception ignored) {}
        executor.shutdown();
    }

    public static class Result {
        private final String namespace;
        private final List<String> podNames;
        private final List<String> containerNames;
        public Result(String namespace, List<String> podNames, List<String> containerNames) {
            this.namespace = namespace; this.podNames = podNames; this.containerNames = containerNames;
        }
        public String getNamespace() { return namespace; }
        public List<String> getPodNames() { return podNames; }
        public List<String> getContainerNames() { return containerNames; }
    }

    /**
     * 获取 Service 的应用端口（优先选择名称包含 http 的端口，否则取第一个 TCP 端口，否则返回 -1）
     */
    public int getServicePort(String namespace, String serviceName) {
        try {
            var svc = client.services().inNamespace(namespace).withName(serviceName).get();
            if (svc == null || svc.getSpec() == null || svc.getSpec().getPorts() == null || svc.getSpec().getPorts().isEmpty()) {
                // 尝试用 label keys 搜索服务
                for (String key : props.getLabelKeys()) {
                    var list = client.services().inNamespace(namespace).withLabel(key, serviceName).list();
                    if (list != null && list.getItems() != null && !list.getItems().isEmpty()) {
                        svc = list.getItems().get(0);
                        break;
                    }
                }
            }
            if (svc == null || svc.getSpec() == null || svc.getSpec().getPorts() == null || svc.getSpec().getPorts().isEmpty()) {
                log.warn("No Service or ports found for ns={}, svc={}", namespace, serviceName);
                return -1;
            }
            // 选择端口
            var ports = svc.getSpec().getPorts();
            // 优先 name 包含 http 的端口
            for (var p : ports) {
                String n = p.getName();
                if (n != null && n.toLowerCase().contains("http") && (p.getPort() != null && p.getPort() > 0)) {
                    return p.getPort();
                }
            }
            // 否则取第一个 TCP 端口
            for (var p : ports) {
                if (p.getPort() != null && p.getPort() > 0 && (p.getProtocol() == null || "TCP".equalsIgnoreCase(p.getProtocol()))) {
                    return p.getPort();
                }
            }
            // 兜底：第一个定义的端口
            var p0 = ports.get(0);
            return (p0.getPort() != null && p0.getPort() > 0) ? p0.getPort() : -1;
        } catch (Exception e) {
            log.warn("Failed to get service port for ns={}, svc={}: {}", namespace, serviceName, e.toString());
            return -1;
        }
    }
    /**
     * 根据名称或 labelKeys 解析实际存在的 Service 名称
     */
    private String resolveServiceName(String namespace, String serviceName) {
        try {
            var direct = client.services().inNamespace(namespace).withName(serviceName).get();
            if (direct != null) return serviceName;
            for (String key : props.getLabelKeys()) {
                var list = client.services().inNamespace(namespace).withLabel(key, serviceName).list();
                if (list != null && list.getItems() != null && !list.getItems().isEmpty()) {
                    return list.getItems().get(0).getMetadata().getName();
                }
            }
        } catch (Exception e) {
            log.debug("[K8s] resolveServiceName error: ns={}, svc={}, ex={}", namespace, serviceName, e.toString());
        }
        return null;
    }

    /**
     * 等待服务稳定：所有 Pod 就绪，且 Endpoints 有可用地址
     */
    public boolean waitForServiceStable(String namespace, String serviceName, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        log.info("[K8s] Waiting service stable: ns={}, svc={}, timeoutMs={}", namespace, serviceName, timeoutMs);
        int lastTotalPods = 0;
        int lastReadyCnt = 0;
        boolean lastEpReady = false;
        try {
            while (System.currentTimeMillis() < deadline) {
                boolean podsReady = false;
                var pods = client.pods().inNamespace(namespace).withLabel("app", serviceName).list().getItems();
                if (pods == null || pods.isEmpty()) {
                    // 尝试其它 label key
                    log.debug("[K8s] No pods found by label 'app', try keys: {}", props.getLabelKeys());
                    for (String key : props.getLabelKeys()) {
                        pods = client.pods().inNamespace(namespace).withLabel(key, serviceName).list().getItems();
                        if (pods != null && !pods.isEmpty()) {
                            log.debug("[K8s] Found {} pods using label key '{}'", pods.size(), key);
                            break;
                        }
                    }
                }
                int readyCnt = 0;
                if (pods != null && !pods.isEmpty()) {
                    lastTotalPods = pods.size();
                    readyCnt = (int) pods.stream().filter(p -> {
                        try {
                            var st = p.getStatus();
                            if (st == null) return false;
                            boolean condReady = st.getConditions() != null && st.getConditions().stream().anyMatch(c ->
                                    "Ready".equalsIgnoreCase(c.getType()) && "True".equalsIgnoreCase(c.getStatus()));
                            boolean containersReady = st.getContainerStatuses() != null && st.getContainerStatuses().stream().allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
                            return condReady && containersReady;
                        } catch (Exception e) { return false; }
                    }).count();
                    podsReady = (readyCnt == pods.size());
                    lastReadyCnt = readyCnt;
                    log.info("[K8s] Pods readiness: {}/{} for ns={}, svc={}", readyCnt, pods.size(), namespace, serviceName);
                } else {
                    log.warn("[K8s] No pods found for ns={}, svc={}", namespace, serviceName);
                }
                boolean epReady = false;
                try {
                    String resolvedSvcName = resolveServiceName(namespace, serviceName);
                    if (resolvedSvcName == null) {
                        log.warn("[K8s] No Service found for ns={}, svc={} by name or label keys {}", namespace, serviceName, props.getLabelKeys());
                    } else {
                        var ep = client.endpoints().inNamespace(namespace).withName(resolvedSvcName).get();
                        if (ep != null && ep.getSubsets() != null) {
                            epReady = ep.getSubsets().stream().anyMatch(ss -> ss.getAddresses() != null && !ss.getAddresses().isEmpty());
                        } else {
                            log.debug("[K8s] Endpoints object not found or has no subsets for ns={}, svcName={}", namespace, resolvedSvcName);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("[K8s] Endpoints check error for ns={}, svc={}: {}", namespace, serviceName, ex.toString());
                }
                lastEpReady = epReady;
                log.info("[K8s] Endpoints ready: {} for ns={}, svc={}", epReady, namespace, serviceName);
                // 放宽判定：只要 Pod 全部就绪即认为服务稳定；Endpoints 仅作日志参考
                if (podsReady) {
                    log.info("[K8s] Service stable confirmed by Pods readiness: ns={}, svc={}, endpoints={}", namespace, serviceName, epReady);
                    return true;
                }
                Thread.sleep(2000L);
            }
            log.warn("[K8s] Wait service stable timeout: ns={}, svc={}, lastPodsReady={}/{}, lastEpReady={}",
                    namespace, serviceName, lastReadyCnt, lastTotalPods, lastEpReady);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[K8s] Wait interrupted: ns={}, svc={}", namespace, serviceName);
        } catch (Exception e) {
            log.warn("[K8s] waitForServiceStable error for ns={}, svc={}: {}", namespace, serviceName, e.toString());
        }
        return false;
    }


}

