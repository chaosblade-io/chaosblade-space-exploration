package com.chaosblade.svc.reqrspproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ReqRspProxyApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReqRspProxyApplication.class, args);
  }
}
