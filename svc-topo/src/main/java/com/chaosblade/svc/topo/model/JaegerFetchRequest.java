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

/** Jaeger查询请求数据模型 */
public class JaegerFetchRequest {

  private String jaegerHost;
  private int port = 16685;
  private String serviceName;
  private String operationName;
  private long startTime; // 毫秒级Unix时间戳
  private long endTime; // 毫秒级Unix时间戳

  public JaegerFetchRequest() {}

  public String getJaegerHost() {
    return jaegerHost;
  }

  public void setJaegerHost(String jaegerHost) {
    this.jaegerHost = jaegerHost;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(String operationName) {
    this.operationName = operationName;
  }

  /**
   * 获取查询开始时间
   *
   * @return 毫秒级Unix时间戳
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   * 设置查询开始时间
   *
   * @param startTime 毫秒级Unix时间戳
   */
  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  /**
   * 获取查询结束时间
   *
   * @return 毫秒级Unix时间戳
   */
  public long getEndTime() {
    return endTime;
  }

  /**
   * 设置查询结束时间
   *
   * @param endTime 毫秒级Unix时间戳
   */
  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }
}
