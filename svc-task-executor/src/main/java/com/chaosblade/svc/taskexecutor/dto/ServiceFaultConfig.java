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

package com.chaosblade.svc.taskexecutor.dto;

import java.util.List;

public class ServiceFaultConfig {
  private String serviceName;
  private String namespace;
  private List<String> names; // pod names
  private List<String> containerNames;
  private List<FaultEntry> faultConfig;

  public ServiceFaultConfig() {}

  public ServiceFaultConfig(
      String serviceName,
      String namespace,
      List<String> names,
      List<String> containerNames,
      List<FaultEntry> faultConfig) {
    this.serviceName = serviceName;
    this.namespace = namespace;
    this.names = names;
    this.containerNames = containerNames;
    this.faultConfig = faultConfig;
  }

  public String getServiceName() {
    return serviceName;
  }

  public String getNamespace() {
    return namespace;
  }

  public List<String> getNames() {
    return names;
  }

  public List<String> getContainerNames() {
    return containerNames;
  }

  public List<FaultEntry> getFaultConfig() {
    return faultConfig;
  }

  public static class FaultEntry {
    private Long id;
    private Long nodeId;
    private String faultScript;
    private String type; // 来自 fault_config.type

    public FaultEntry() {}

    public FaultEntry(Long id, Long nodeId, String faultScript, String type) {
      this.id = id;
      this.nodeId = nodeId;
      this.faultScript = faultScript;
      this.type = type;
    }

    public Long getId() {
      return id;
    }

    public Long getNodeId() {
      return nodeId;
    }

    public String getFaultScript() {
      return faultScript;
    }

    public String getType() {
      return type;
    }
  }
}
