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

package com.chaosblade.svc.topo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JaegerSource {

  @JsonProperty("host")
  private String host;

  @JsonProperty("httpPort")
  private Integer httpPort;

  @JsonProperty("basePath")
  private String basePath = "/api/traces";

  @JsonProperty("limit")
  private Integer limit = 20;

  @JsonProperty("systemKey")
  private String systemKey;

  @JsonProperty("entryService")
  private String entryService;

  // Getters and Setters

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(Integer httpPort) {
    this.httpPort = httpPort;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public String getSystemKey() {
    return systemKey;
  }

  public void setSystemKey(String systemKey) {
    this.systemKey = systemKey;
  }

  public String getEntryService() {
    return entryService;
  }

  public void setEntryService(String entryService) {
    this.entryService = entryService;
  }

  @Override
  public String toString() {
    return "JaegerSource{"
        + "host='"
        + host
        + '\''
        + ", httpPort="
        + httpPort
        + ", basePath='"
        + basePath
        + '\''
        + ", limit="
        + limit
        + ", systemKey='"
        + systemKey
        + '\''
        + ", entryService='"
        + entryService
        + '\''
        + '}';
  }
}
