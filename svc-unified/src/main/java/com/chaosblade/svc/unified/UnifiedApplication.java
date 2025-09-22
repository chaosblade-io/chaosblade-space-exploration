package com.chaosblade.svc.unified;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一应用启动类
 * 支持通过命令行参数启动不同的微服务
 * 
 * 使用方法：
 * java -jar unified-app.jar --service=task-resource
 * java -jar unified-app.jar --service=task-executor
 * java -jar unified-app.jar --service=fault-scheduler
 * java -jar unified-app.jar --service=result-processor
 * java -jar unified-app.jar --service=reqrsp-proxy
 * java -jar unified-app.jar --service=topo
 * 
 * 或者通过环境变量：
 * SERVICE_NAME=task-resource java -jar unified-app.jar
 */
@SpringBootApplication
public class UnifiedApplication {

    // 服务映射表 - 使用字符串类名，通过反射加载
    private static final Map<String, String> SERVICE_MAPPING = new HashMap<>();
    
    static {
        SERVICE_MAPPING.put("task-resource", "com.chaosblade.svc.taskresource.TaskResourceApplication");
        SERVICE_MAPPING.put("task-executor", "com.chaosblade.svc.taskexecutor.TaskExecutorApplication");
        SERVICE_MAPPING.put("fault-scheduler", "com.chaosblade.svc.faultscheduler.FaultSchedulerApplication");
        SERVICE_MAPPING.put("result-processor", "com.chaosblade.svc.resultprocessor.ResultProcessorApplication");
        SERVICE_MAPPING.put("reqrsp-proxy", "com.chaosblade.svc.reqrspproxy.ReqRspProxyApplication");
        SERVICE_MAPPING.put("topo", "com.chaosblade.svc.topo.TopoApplication");
    }

    public static void main(String[] args) {
        // 解析服务名称
        String serviceName = getServiceName(args);
        
        if (serviceName == null || !SERVICE_MAPPING.containsKey(serviceName)) {
            printUsage();
            System.exit(1);
        }

        // 获取对应的应用类名
        String applicationClassName = SERVICE_MAPPING.get(serviceName);
        
        System.out.println("=== 启动服务: " + serviceName + " ===");
        System.out.println("应用类: " + applicationClassName);
        System.out.println("==========================================");

        // 通过反射加载并启动对应的Spring Boot应用
        try {
            Class<?> applicationClass = Class.forName(applicationClassName);
            ConfigurableApplicationContext context = SpringApplication.run(applicationClass, args);
            
            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== 正在关闭服务: " + serviceName + " ===");
                context.close();
                System.out.println("=== 服务已关闭 ===");
            }));
            
        } catch (ClassNotFoundException e) {
            System.err.println("找不到应用类: " + applicationClassName);
            System.err.println("请确保所有微服务模块都已正确打包到统一应用中");
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("启动服务失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 从命令行参数或环境变量中获取服务名称
     */
    private static String getServiceName(String[] args) {
        // 首先尝试从命令行参数获取
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--service=")) {
                return args[i].substring("--service=".length());
            }
            if (args[i].equals("--service") && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        
        // 如果命令行参数中没有，尝试从环境变量获取
        String envService = System.getenv("SERVICE_NAME");
        if (envService != null && !envService.trim().isEmpty()) {
            return envService.trim();
        }
        
        return null;
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("=== ChaosBlade Space Exploration 统一应用 ===");
        System.out.println();
        System.out.println("使用方法:");
        System.out.println("  java -jar unified-app.jar --service=<service-name>");
        System.out.println("  或");
        System.out.println("  SERVICE_NAME=<service-name> java -jar unified-app.jar");
        System.out.println();
        System.out.println("支持的服务:");
        for (String service : SERVICE_MAPPING.keySet()) {
            System.out.println("  - " + service);
        }
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -jar unified-app.jar --service=task-resource");
        System.out.println("  SERVICE_NAME=reqrsp-proxy java -jar unified-app.jar");
        System.out.println();
    }
}
