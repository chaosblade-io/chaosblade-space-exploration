package com.chaosblade.svc.topo.service;

import com.chaosblade.svc.topo.model.k8s.KubernetesMetadata;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 查询服务
 * 负责从 Kubernetes API 查询资源元数据
 */
@Service
public class KubernetesQueryService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesQueryService.class);

    @Value("${kubernetes.enabled:true}")
    private boolean enabled;

    @Value("${kubernetes.api-server:https://kubernetes.default.svc}")
    private String apiServer;

    @Value("${kubernetes.namespace:default}")
    private String defaultNamespace;

    @Value("${kubernetes.service-account-token-path:/var/run/secrets/kubernetes.io/serviceaccount/token}")
    private String serviceAccountTokenPath;

    private KubernetesClient kubernetesClient;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Kubernetes integration is disabled");
            return;
        }

        try {
            // 创建 Kubernetes 客户端配置
            Config config = new ConfigBuilder()
                .withMasterUrl(apiServer)
                .withTrustCerts(true)
                .build();

            // 创建 Kubernetes 客户端
            kubernetesClient = new DefaultKubernetesClient(config);

            logger.info("Kubernetes client initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize Kubernetes client", e);
            enabled = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (kubernetesClient != null) {
            try {
                kubernetesClient.close();
            } catch (Exception e) {
                logger.error("Failed to close Kubernetes client", e);
            }
        }
    }

    /**
     * 查询 Pod 元数据
     */
    public KubernetesMetadata queryPodMetadata(String namespace, String podName) {
        if (!enabled || kubernetesClient == null) {
            logger.debug("Kubernetes is disabled, skipping metadata query");
            return null;
        }

        logger.debug("Querying Kubernetes metadata for pod: {}/{}", namespace, podName);

        try {
            Pod pod = kubernetesClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .get();

            if (pod == null) {
                logger.warn("Pod not found: {}/{}", namespace, podName);
                return null;
            }

            return convertPodToMetadata(pod);

        } catch (Exception e) {
            logger.error("Failed to query Pod metadata: {}/{}", namespace, podName, e);
            return null;
        }
    }

    /**
     * 查询 Node 元数据
     */
    public KubernetesMetadata queryNodeMetadata(String nodeName) {
        if (!enabled || kubernetesClient == null) {
            logger.debug("Kubernetes is disabled, skipping metadata query");
            return null;
        }

        logger.debug("Querying Kubernetes metadata for node: {}", nodeName);

        try {
            io.fabric8.kubernetes.api.model.Node node = kubernetesClient.nodes()
                .withName(nodeName)
                .get();

            if (node == null) {
                logger.warn("Node not found: {}", nodeName);
                return null;
            }

            return convertNodeToMetadata(node);

        } catch (Exception e) {
            logger.error("Failed to query Node metadata: {}", nodeName, e);
            return null;
        }
    }

    /**
     * 查询命名空间下的所有 Pod
     */
    public List<KubernetesMetadata> listPodsInNamespace(String namespace) {
        if (!enabled || kubernetesClient == null) {
            logger.debug("Kubernetes is disabled, skipping pods list");
            return new ArrayList<>();
        }

        logger.debug("Listing all pods in namespace: {}", namespace);

        try {
            PodList podList = kubernetesClient.pods()
                .inNamespace(namespace)
                .list();

            List<KubernetesMetadata> metadataList = new ArrayList<>();
            for (Pod pod : podList.getItems()) {
                metadataList.add(convertPodToMetadata(pod));
            }

            return metadataList;

        } catch (Exception e) {
            logger.error("Failed to list pods in namespace: {}", namespace, e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询所有 Node
     */
    public List<KubernetesMetadata> listNodes() {
        if (!enabled || kubernetesClient == null) {
            logger.debug("Kubernetes is disabled, skipping nodes list");
            return new ArrayList<>();
        }

        logger.debug("Listing all nodes");

        try {
            NodeList nodeList = kubernetesClient.nodes().list();

            List<KubernetesMetadata> metadataList = new ArrayList<>();
            for (io.fabric8.kubernetes.api.model.Node node : nodeList.getItems()) {
                metadataList.add(convertNodeToMetadata(node));
            }

            return metadataList;

        } catch (Exception e) {
            logger.error("Failed to list nodes", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将 Pod 转换为 KubernetesMetadata
     */
    private KubernetesMetadata convertPodToMetadata(Pod pod) {
        KubernetesMetadata metadata = new KubernetesMetadata();

        ObjectMeta objectMeta = pod.getMetadata();
        if (objectMeta != null) {
            metadata.setKind("Pod");
            metadata.setApiVersion(pod.getApiVersion());
            metadata.setNamespace(objectMeta.getNamespace());
            metadata.setName(objectMeta.getName());
            metadata.setUid(objectMeta.getUid());
            metadata.setCreationTimestamp(objectMeta.getCreationTimestamp());
            metadata.setResourceVersion(objectMeta.getResourceVersion());

            // 标签
            if (objectMeta.getLabels() != null) {
                metadata.setLabels(new HashMap<>(objectMeta.getLabels()));
            }

            // 注解
            if (objectMeta.getAnnotations() != null) {
                metadata.setAnnotations(new HashMap<>(objectMeta.getAnnotations()));
            }

            // 所有者引用
            if (objectMeta.getOwnerReferences() != null) {
                List<KubernetesMetadata.OwnerReference> ownerRefs = new ArrayList<>();
                for (OwnerReference ownerRef : objectMeta.getOwnerReferences()) {
                    KubernetesMetadata.OwnerReference ref = new KubernetesMetadata.OwnerReference();
                    ref.setKind(ownerRef.getKind());
                    ref.setName(ownerRef.getName());
                    ref.setUid(ownerRef.getUid());
                    ref.setController(ownerRef.getController());
                    ownerRefs.add(ref);
                }
                metadata.setOwnerReferences(ownerRefs);
            }
        }

        return metadata;
    }

    /**
     * 将 Node 转换为 KubernetesMetadata
     */
    private KubernetesMetadata convertNodeToMetadata(io.fabric8.kubernetes.api.model.Node node) {
        KubernetesMetadata metadata = new KubernetesMetadata();

        ObjectMeta objectMeta = node.getMetadata();
        if (objectMeta != null) {
            metadata.setKind("Node");
            metadata.setApiVersion(node.getApiVersion());
            metadata.setName(objectMeta.getName());
            metadata.setUid(objectMeta.getUid());
            metadata.setCreationTimestamp(objectMeta.getCreationTimestamp());
            metadata.setResourceVersion(objectMeta.getResourceVersion());

            // 标签
            if (objectMeta.getLabels() != null) {
                metadata.setLabels(new HashMap<>(objectMeta.getLabels()));
            }

            // 注解
            if (objectMeta.getAnnotations() != null) {
                metadata.setAnnotations(new HashMap<>(objectMeta.getAnnotations()));
            }
        }

        return metadata;
    }
}

