package com.chaosblade.svc.k8sgraph.service.risk;

import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.PipelineExecutionStatus.ExecutionState;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.PipelineExecutionStatus.PhaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步Pipeline执行服务
 * 
 * 功能：
 * 1. 异步执行Pipeline
 * 2. 并发控制：同一namespace不重复执行
 * 3. 故障快速终止：任何Phase失败立即停止
 * 4. 状态追踪：实时更新执行进度
 */
@Service
public class AsyncPipelineService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncPipelineService.class);
    
    /** 存储各namespace的执行状态 */
    private final ConcurrentHashMap<String, PipelineExecutionStatus> executionStatusMap = new ConcurrentHashMap<>();
    
    /** 存储历史执行结果（最近N次） */
    private final ConcurrentHashMap<String, List<PipelineExecutionStatus>> historyMap = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 10;
    
    @Autowired
    private RiskPipelineOrchestrator pipelineOrchestrator;
    
    /**
     * 启动Pipeline异步执行
     * 
     * @param namespace 命名空间
     * @return 执行状态（如果已有任务在执行，返回当前状态）
     */
    public PipelineExecutionStatus startPipeline(String namespace) {
        // 检查是否已有任务在执行
        PipelineExecutionStatus existingStatus = executionStatusMap.get(namespace);
        if (existingStatus != null && 
            (existingStatus.getState() == ExecutionState.PENDING || 
             existingStatus.getState() == ExecutionState.RUNNING)) {
            logger.info("Pipeline for namespace '{}' is already running, executionId={}",
                namespace, existingStatus.getExecutionId());
            return existingStatus;
        }
        
        // 创建新的执行状态
        String executionId = generateExecutionId(namespace);
        PipelineExecutionStatus status = new PipelineExecutionStatus(executionId, namespace);
        executionStatusMap.put(namespace, status);
        
        logger.info("Starting async pipeline for namespace '{}', executionId={}", namespace, executionId);
        
        // 异步执行
        executeAsync(namespace, status);
        
        return status;
    }
    
    /**
     * 获取执行状态
     */
    public PipelineExecutionStatus getStatus(String namespace) {
        PipelineExecutionStatus status = executionStatusMap.get(namespace);
        if (status != null) {
            // 更新已耗时
            if (status.getState() == ExecutionState.RUNNING) {
                status.setElapsedTimeMs(System.currentTimeMillis() - parseStartTime(status.getStartTime()));
            }
        }
        return status;
    }
    
    /**
     * 获取完整结果（如果执行完成）
     */
    public PipelineResult getResult(String namespace) {
        PipelineExecutionStatus status = executionStatusMap.get(namespace);
        if (status != null && status.getState() == ExecutionState.COMPLETED) {
            return status.getResult();
        }
        return null;
    }
    
    /**
     * 获取历史执行记录
     */
    public List<PipelineExecutionStatus> getHistory(String namespace) {
        return historyMap.getOrDefault(namespace, Collections.emptyList());
    }
    
    /**
     * 异步执行Pipeline
     */
    @Async("pipelineExecutor")
    public void executeAsync(String namespace, PipelineExecutionStatus status) {
        long startTime = System.currentTimeMillis();
        
        try {
            status.setState(ExecutionState.RUNNING);
            logger.info("Async pipeline execution started for namespace: {}", namespace);
            
            // 调用同步的Pipeline执行（内部会更新各Phase状态）
            PipelineResult result = executePipelineWithStatusTracking(namespace, status);
            
            // 执行完成
            status.setResult(result);
            status.setState(result.isSuccess() ? ExecutionState.COMPLETED : ExecutionState.FAILED);
            if (!result.isSuccess()) {
                status.setErrorMessage(result.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("Async pipeline execution failed for namespace {}: {}", namespace, e.getMessage(), e);
            status.setState(ExecutionState.FAILED);
            status.setErrorMessage(e.getMessage());
        } finally {
            status.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            status.setElapsedTimeMs(System.currentTimeMillis() - startTime);
            status.setProgressPercent(100);
            
            // 保存到历史记录
            saveToHistory(namespace, status);
        }
    }
    
    /**
     * 带状态追踪的Pipeline执行
     */
    private PipelineResult executePipelineWithStatusTracking(String namespace, PipelineExecutionStatus status) {
        // 调用带状态回调的execute方法
        return pipelineOrchestrator.execute(namespace, status);
    }
    
    private String generateExecutionId(String namespace) {
        return String.format("exec-%s-%d", namespace, System.currentTimeMillis());
    }
    
    private long parseStartTime(String startTime) {
        try {
            LocalDateTime dt = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
    
    private void saveToHistory(String namespace, PipelineExecutionStatus status) {
        historyMap.computeIfAbsent(namespace, k -> Collections.synchronizedList(new ArrayList<>()));
        List<PipelineExecutionStatus> history = historyMap.get(namespace);
        history.add(0, status); // 最新的在前面
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }
}

