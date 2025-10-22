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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemUnderTest {

  private static final Logger logger = LoggerFactory.getLogger(SystemUnderTest.class);

  private JaegerSource jaegerSource;
  private SystemInfo systemInfo;

  public SystemUnderTest(JaegerSource jaegerSource, SystemInfo systemInfo) {
    this.jaegerSource = jaegerSource;
    this.systemInfo = systemInfo;

    // 记录警告日志，如果systemInfo为null
    if (systemInfo == null) {
      logger.warn(
          "SystemInfo not found for systemKey: {}",
          jaegerSource != null ? jaegerSource.getSystemKey() : "unknown");
    }
  }

  public JaegerSource getJaegerSource() {
    return jaegerSource;
  }

  public void setJaegerSource(JaegerSource jaegerSource) {
    this.jaegerSource = jaegerSource;
  }

  public SystemInfo getSystemInfo() {
    return systemInfo;
  }

  public void setSystemInfo(SystemInfo systemInfo) {
    this.systemInfo = systemInfo;

    // 记录警告日志，如果systemInfo为null
    if (systemInfo == null) {
      logger.warn(
          "SystemInfo not found for systemKey: {}",
          jaegerSource != null ? jaegerSource.getSystemKey() : "unknown");
    }
  }

  @Override
  public String toString() {
    return "SystemUnderTest{" + "jaegerSource=" + jaegerSource + ", systemInfo=" + systemInfo + '}';
  }
}
