package com.chaosblade.common.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 数据库配置类 */
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseConfig {

  private String url;
  private String username;
  private String password;
  private String driverClassName;

  // HikariCP 连接池配置
  private HikariConfig hikari = new HikariConfig();

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  public void setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName;
  }

  public HikariConfig getHikari() {
    return hikari;
  }

  public void setHikari(HikariConfig hikari) {
    this.hikari = hikari;
  }

  /** HikariCP 连接池配置 */
  public static class HikariConfig {
    private int maximumPoolSize = 20;
    private int minimumIdle = 5;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;

    public int getMaximumPoolSize() {
      return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
      this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
      return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
      this.minimumIdle = minimumIdle;
    }

    public long getConnectionTimeout() {
      return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
    }

    public long getIdleTimeout() {
      return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
      this.idleTimeout = idleTimeout;
    }

    public long getMaxLifetime() {
      return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime) {
      this.maxLifetime = maxLifetime;
    }
  }
}
