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
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemInfo {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("systemKey")
  private String key;

  @JsonProperty("systemAlias")
  private List<String> alias;

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("owner")
  private String owner;

  @JsonProperty("defaultEnvironment")
  private String defaultEnvironment;

  @JsonProperty("rootService")
  private String rootService;

  @JsonProperty("rootOperation")
  private String rootOperation;

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public List<String> getAlias() {
    return alias;
  }

  public void setAlias(List<String> alias) {
    this.alias = alias;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getDefaultEnvironment() {
    return defaultEnvironment;
  }

  public void setDefaultEnvironment(String defaultEnvironment) {
    this.defaultEnvironment = defaultEnvironment;
  }

  public String getRootService() {
    return rootService;
  }

  public void setRootService(String rootService) {
    this.rootService = rootService;
  }

  public String getRootOperation() {
    return rootOperation;
  }

  public void setRootOperation(String rootOperation) {
    this.rootOperation = rootOperation;
  }

  @Override
  public String toString() {
    return "SystemInfo{"
        + "id="
        + id
        + ", key='"
        + key
        + '\''
        + ", alias="
        + alias
        + ", name='"
        + name
        + '\''
        + ", description='"
        + description
        + '\''
        + ", owner='"
        + owner
        + '\''
        + ", defaultEnvironment='"
        + defaultEnvironment
        + '\''
        + ", rootService='"
        + rootService
        + '\''
        + ", rootOperation='"
        + rootOperation
        + '\''
        + '}';
  }
}
