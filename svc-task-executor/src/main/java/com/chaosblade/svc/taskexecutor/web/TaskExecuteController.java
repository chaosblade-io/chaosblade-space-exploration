package com.chaosblade.svc.taskexecutor.web;

import com.chaosblade.common.core.ApiResponse;
import com.chaosblade.svc.taskexecutor.config.ProxyProperties;
import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import com.chaosblade.svc.taskexecutor.service.ExecutionOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskExecuteController {
    private final ExecutionOrchestrator orchestrator;
    private final ProxyProperties proxyProperties;

    public TaskExecuteController(ExecutionOrchestrator orchestrator,
                                 ProxyProperties proxyProperties) {
        this.orchestrator = orchestrator;
        this.proxyProperties = proxyProperties;
    }

    public static class ExecuteOptions {
        public Boolean force;                  // 默认 false
        public Integer ttlSecForInterceptors;  // 覆盖配置
        public Integer waitAnalyzeTimeoutSec;  // 覆盖配置
        public Integer waitInterceptorReadySec;// 覆盖配置
    }

    @PostMapping("/{taskId}/execute")
    public ResponseEntity<ApiResponse<Map<String,Object>>> execute(@PathVariable("taskId") Long taskId,
                                                                   @RequestBody(required = false) ExecuteOptions opts) {
        boolean force = opts!=null && Boolean.TRUE.equals(opts.force);
        TaskExecution te = orchestrator.createOrFailIfRunning(taskId, force);
        Long executionId = te.getId();

        // 异步拉起编排
        new Thread(() -> {
            var options = new ExecutionOrchestrator.OrchestrateOptions();
            options.ttlSecForInterceptors = proxyProperties.getTtlSecForInterceptors();
            options.waitAnalyzeTimeoutSec = proxyProperties.getWaitAnalyzeTimeoutSec();
            options.waitInterceptorReadySec = proxyProperties.getWaitInterceptorReadySec();
            if (opts != null) {
                if (opts.ttlSecForInterceptors != null) options.ttlSecForInterceptors = opts.ttlSecForInterceptors;
                if (opts.waitAnalyzeTimeoutSec != null) options.waitAnalyzeTimeoutSec = opts.waitAnalyzeTimeoutSec;
                if (opts.waitInterceptorReadySec != null) options.waitInterceptorReadySec = opts.waitInterceptorReadySec;
            }
            orchestrator.run(executionId, options);
        }, "exec-"+executionId).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "executionId", executionId,
                "status", te.getStatus()
        )));
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getExecutionStatus(@PathVariable("executionId") Long executionId) {
        java.util.Optional<TaskExecution> opt = orchestrator.getExecution(executionId);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("404", "Execution not found: "+executionId));
        }
        TaskExecution te = opt.get();
        Map<String,Object> data = new java.util.LinkedHashMap<>();
        data.put("executionId", te.getId());
        data.put("taskId", te.getTaskId());
        data.put("status", te.getStatus());
        data.put("analyzeTaskId", te.getAnalyzeTaskId());
        data.put("recordId", te.getRecordId());
        data.put("interceptRecordId", te.getInterceptRecordId());
        data.put("startedAt", te.getStartedAt());
        data.put("updatedAt", te.getUpdatedAt());
        data.put("finishedAt", te.getFinishedAt());
        data.put("errorCode", te.getErrorCode());
        data.put("errorMsg", te.getErrorMsg());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}




