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
