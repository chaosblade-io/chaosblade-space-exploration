package com.chaosblade.svc.reqrspproxy.web;

import com.chaosblade.common.core.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {
    @GetMapping("/hello")
    public Mono<ApiResponse<String>> hello() {
        return Mono.just(ApiResponse.ok("svc-reqrsp-proxy: hello world"));
    }
}

