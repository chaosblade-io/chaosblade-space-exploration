package com.chaosblade.svc.topo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 主页面控制器
 *
 * 负责渲染主页面和提供基本的导航功能
 */
@Controller
@RequestMapping("/")
public class HomeController {

    /**
     * 主页面
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Topology Visualizer");
        model.addAttribute("description", "OpenTelemetry 拓扑可视化工具");
        model.addAttribute("version", "1.0.0");

        return "index";
    }

    /**
     * 关于页面（可选）
     */
    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("title", "关于 - Topology Visualizer");
        model.addAttribute("appName", "Topology Visualizer");
        model.addAttribute("version", "1.0.0");
        model.addAttribute("description", "基于OpenTelemetry trace数据的微服务拓扑可视化工具");

        // 技术栈信息
        model.addAttribute("technologies", new String[]{
            "Spring Boot 3.x",
            "JGraphT 1.5.x",
            "Bootstrap 5",
            "Jackson JSON"
        });

        return "about";
    }

    /**
     * API文档页面
     */
    @GetMapping("/docs")
    public String docs(Model model) {
        model.addAttribute("title", "API文档 - Topology Visualizer");

        return "docs";
    }

}
