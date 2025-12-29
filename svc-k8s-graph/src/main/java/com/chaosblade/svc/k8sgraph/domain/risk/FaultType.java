package com.chaosblade.svc.k8sgraph.domain.risk;

/**
 * ChaosBlade 支持的故障类型
 */
public final class FaultType {
    private FaultType() {}
    
    // Pod/容器故障
    public static final String CONTAINER_CPU = "chaosblade.k8s.container-cpu";
    public static final String CONTAINER_MEMORY = "chaosblade.k8s.container-memory";
    public static final String CONTAINER_DISK = "chaosblade.k8s.container-disk";
    public static final String CONTAINER_PROCESS_STOP = "chaosblade.k8s.container-process-stop";
    public static final String CONTAINER_REMOVE = "chaosblade.k8s.container-remove";
    public static final String POD_KILL = "chaosblade.k8s.pod-kill";
    
    // 网络故障
    public static final String NETWORK_DELAY = "chaosblade.k8s.container-network-delay";
    public static final String NETWORK_LOSS = "chaosblade.k8s.container-network-loss";
    public static final String NETWORK_CORRUPT = "chaosblade.k8s.container-network-corrupt";
    public static final String NETWORK_OCCUPY = "chaosblade.k8s.container-network-occupy";
    public static final String NETWORK_DNS = "chaosblade.k8s.container-network-dns";
    
    /**
     * 获取故障类型的显示名称
     */
    public static String getDisplayName(String faultCode) {
        switch (faultCode) {
            case CONTAINER_CPU: return "CPU 满载";
            case CONTAINER_MEMORY: return "内存满载";
            case CONTAINER_DISK: return "磁盘负载提升";
            case CONTAINER_PROCESS_STOP: return "进程停滞";
            case CONTAINER_REMOVE: return "容器移除";
            case POD_KILL: return "Pod 删除";
            case NETWORK_DELAY: return "网络延迟";
            case NETWORK_LOSS: return "网络丢包";
            case NETWORK_CORRUPT: return "网络损坏";
            case NETWORK_OCCUPY: return "网络占用";
            case NETWORK_DNS: return "DNS 异常";
            default: return faultCode;
        }
    }
}

