package com.chaosblade.svc.taskresource.web;

import com.chaosblade.common.core.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.ok("svc-task-resource: hello world");
    }
}

