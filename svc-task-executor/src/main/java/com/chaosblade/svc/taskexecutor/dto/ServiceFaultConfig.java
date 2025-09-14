package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

public class ServiceFaultConfig {
    private String serviceName;
    private String namespace;
    private List<String> names; // pod names
    private List<String> containerNames;
    private List<FaultEntry> faultConfig;

    public ServiceFaultConfig() {}
    public ServiceFaultConfig(String serviceName, String namespace, List<String> names,
                              List<String> containerNames, List<FaultEntry> faultConfig) {
        this.serviceName = serviceName;
        this.namespace = namespace;
        this.names = names;
        this.containerNames = containerNames;
        this.faultConfig = faultConfig;
    }
    public String getServiceName() { return serviceName; }
    public String getNamespace() { return namespace; }
    public List<String> getNames() { return names; }
    public List<String> getContainerNames() { return containerNames; }
    public List<FaultEntry> getFaultConfig() { return faultConfig; }

    public static class FaultEntry {
        private Long id;
        private Long nodeId;
        private String faultScript;
        private String type; // 来自 fault_config.type
        public FaultEntry() {}
        public FaultEntry(Long id, Long nodeId, String faultScript, String type) {
            this.id = id; this.nodeId = nodeId; this.faultScript = faultScript; this.type = type;
        }
        public Long getId() { return id; }
        public Long getNodeId() { return nodeId; }
        public String getFaultScript() { return faultScript; }
        public String getType() { return type; }
    }
}

