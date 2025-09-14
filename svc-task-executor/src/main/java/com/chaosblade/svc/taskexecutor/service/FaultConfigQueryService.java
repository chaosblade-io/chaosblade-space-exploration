package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.entity.ApiTopology;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import com.chaosblade.svc.taskexecutor.entity.FaultConfig;
import com.chaosblade.svc.taskexecutor.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class FaultConfigQueryService {

    private final DetectionTaskRepository detectionTaskRepository;
    private final ApiTopologyRepository apiTopologyRepository;
    private final ApiTopologyNodeRepository apiTopologyNodeRepository;
    private final FaultConfigRepository faultConfigRepository;
    private final SystemRepository systemRepository;
    private final KubernetesService kubernetesService;

    public FaultConfigQueryService(DetectionTaskRepository detectionTaskRepository,
                                   ApiTopologyRepository apiTopologyRepository,
                                   ApiTopologyNodeRepository apiTopologyNodeRepository,
                                   FaultConfigRepository faultConfigRepository,
                                   SystemRepository systemRepository,
                                   KubernetesService kubernetesService) {
        this.detectionTaskRepository = detectionTaskRepository;
        this.apiTopologyRepository = apiTopologyRepository;
        this.apiTopologyNodeRepository = apiTopologyNodeRepository;
        this.faultConfigRepository = faultConfigRepository;
        this.systemRepository = systemRepository;
        this.kubernetesService = kubernetesService;
    }

    public List<ServiceFaultConfig> getFaultConfigsByTaskId(Long taskId) {
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + taskId));

        ApiTopology topo = apiTopologyRepository.findBySystemIdAndApiId(task.getSystemId(), task.getApiId())
                .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND",
                        "未找到该系统与API对应的拓扑: system_id=" + task.getSystemId() + ", api_id=" + task.getApiId()));

        List<ApiTopologyNode> nodes = apiTopologyNodeRepository.findByTopologyId(topo.getId());
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> nodeIdToName = nodes.stream().collect(Collectors.toMap(ApiTopologyNode::getId, ApiTopologyNode::getName));

        // 通过 system_id 查询 systems 表以获取 namespace（system_key）
        com.chaosblade.svc.taskexecutor.entity.System system = systemRepository.findById(task.getSystemId())
                .orElseThrow(() -> new BusinessException("SYSTEM_NOT_FOUND", "系统不存在: " + task.getSystemId()));
        String namespace = system.getSystemKey();

        List<Long> nodeIds = nodes.stream().map(ApiTopologyNode::getId).toList();
        // 支持 task 范围：优先取 taskId 定向配置，若无则包含公共配置（task_id IS NULL）
        List<FaultConfig> configs = faultConfigRepository.findByNodeIdsWithTaskScope(nodeIds, task.getId());
        if (configs.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 serviceName 分组
        Map<String, List<ServiceFaultConfig.FaultEntry>> grouped = new LinkedHashMap<>();
        for (FaultConfig fc : configs) {
            String serviceName = nodeIdToName.getOrDefault(fc.getNodeId(), "UNKNOWN");
            grouped.computeIfAbsent(serviceName, k -> new ArrayList<>())
                    .add(new ServiceFaultConfig.FaultEntry(fc.getId(), fc.getNodeId(), fc.getFaultscript(), fc.getType()));
        }

        // 并发查询每个服务在 K8s 中的部署信息
        List<CompletableFuture<ServiceFaultConfig>> futures = new ArrayList<>();
        for (Map.Entry<String, List<ServiceFaultConfig.FaultEntry>> e : grouped.entrySet()) {
            String serviceName = e.getKey();
            List<ServiceFaultConfig.FaultEntry> entries = e.getValue();
            futures.add(kubernetesService.getDeploymentInfoAsync(namespace, serviceName)
                    .thenApply(k -> new ServiceFaultConfig(serviceName, k.getNamespace(), k.getPodNames(), k.getContainerNames(), entries)));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }
}

