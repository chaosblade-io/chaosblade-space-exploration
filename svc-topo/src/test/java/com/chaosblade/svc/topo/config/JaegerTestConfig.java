package com.chaosblade.svc.topo.config;

/** 测试环境 Jaeger 配置参数 */
public class JaegerTestConfig {

  // Jaeger 测试服务器配置
  public static final String JAEGER_HOST = "localhost";
  public static final int JAEGER_PORT = 16685;
  public static final String SERVICE_NAME = "checkout";
  public static final String OPERATION_NAME = "oteldemo.CheckoutService/PlaceOrder";

  // 私有构造函数，防止实例化
  private JaegerTestConfig() {
    // 静态工具类，不需要实例化
  }
}
