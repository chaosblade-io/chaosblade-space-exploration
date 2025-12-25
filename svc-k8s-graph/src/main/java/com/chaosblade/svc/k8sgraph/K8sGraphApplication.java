package com.chaosblade.svc.k8sgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * K8s资源拓扑图服务启动类
 * 
 * 功能：展示Kubernetes资源拓扑关系，采用分域设计：
 * - k8s.infra: 基础设施域（Node、Namespace、PV、StorageClass等）
 * - k8s.workload: 工作负载域（Deployment、Pod、Job等）
 * - k8s.network: 网络域（Service、Ingress、NetworkPolicy等）
 * - k8s.config: 配置与安全域（ConfigMap、Secret、ServiceAccount等）
 */
@SpringBootApplication
@EnableConfigurationProperties
public class K8sGraphApplication {
    public static void main(String[] args) {
        SpringApplication.run(K8sGraphApplication.class, args);
    }
}