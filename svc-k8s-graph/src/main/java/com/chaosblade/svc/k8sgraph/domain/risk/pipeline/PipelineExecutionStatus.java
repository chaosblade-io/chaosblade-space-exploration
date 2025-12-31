package com.chaosblade.svc.k8sgraph.domain.risk.pipeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline异步执行状态
 * 用于追踪Pipeline的执行进度和状态
 */
public class PipelineExecutionStatus {
    
    /** 执行ID（唯一标识） */
    private String executionId;
    
    /** 命名空间 */
    private String namespace;
    
    /** 执行状态 */
    private ExecutionState state;
    
    /** 当前执行的Phase (1-6) */
    private int currentPhase;
    
    /** 总Phase数 */
    private static final int TOTAL_PHASES = 6;
    
    /** 进度百分比 (0-100) */
    private int progressPercent;
    
    /** 开始时间 */
    private String startTime;
    
    /** 结束时间 */
    private String endTime;
    
    /** 已耗时(ms) */
    private long elapsedTimeMs;
    
    /** 各Phase的执行状态 */
    private List<PhaseStatus> phaseStatuses = new ArrayList<>();
    
    /** 错误信息（如果失败） */
    private String errorMessage;
    
    /** 失败的Phase */
    private Integer failedPhase;
    
    /** 完整的Pipeline结果（执行完成后填充） */
    private PipelineResult result;
    
    public PipelineExecutionStatus() {
        for (int i = 1; i <= TOTAL_PHASES; i++) {
            phaseStatuses.add(new PhaseStatus(i));
        }
    }
    
    public PipelineExecutionStatus(String executionId, String namespace) {
        this();
        this.executionId = executionId;
        this.namespace = namespace;
        this.state = ExecutionState.PENDING;
        this.currentPhase = 0;
        this.progressPercent = 0;
        this.startTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    // Getters and Setters
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
    public ExecutionState getState() { return state; }
    public void setState(ExecutionState state) { this.state = state; }
    
    public int getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(int currentPhase) { 
        this.currentPhase = currentPhase;
        this.progressPercent = (currentPhase * 100) / TOTAL_PHASES;
    }
    
    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }
    
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    
    public long getElapsedTimeMs() { return elapsedTimeMs; }
    public void setElapsedTimeMs(long elapsedTimeMs) { this.elapsedTimeMs = elapsedTimeMs; }
    
    public List<PhaseStatus> getPhaseStatuses() { return phaseStatuses; }
    public void setPhaseStatuses(List<PhaseStatus> phaseStatuses) { this.phaseStatuses = phaseStatuses; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getFailedPhase() { return failedPhase; }
    public void setFailedPhase(Integer failedPhase) { this.failedPhase = failedPhase; }
    
    public PipelineResult getResult() { return result; }
    public void setResult(PipelineResult result) { this.result = result; }
    
    /** 执行状态枚举 */
    public enum ExecutionState {
        PENDING,    // 等待执行
        RUNNING,    // 执行中
        COMPLETED,  // 执行完成
        FAILED      // 执行失败
    }
    
    /** 单个Phase的执行状态 */
    public static class PhaseStatus {
        private int phaseNumber;
        private String phaseName;
        private ExecutionState state;
        private long startTimeMs;
        private long durationMs;
        private String message;
        
        private static final String[] PHASE_NAMES = {
            "", "规则扫描", "拓扑LLM分析", "RiskRank计算", 
            "Trace深度分析", "综合分析与场景生成", "实验配置生成"
        };
        
        public PhaseStatus() {}
        
        public PhaseStatus(int phaseNumber) {
            this.phaseNumber = phaseNumber;
            this.phaseName = phaseNumber <= 6 ? PHASE_NAMES[phaseNumber] : "Unknown";
            this.state = ExecutionState.PENDING;
        }
        
        // Getters and Setters
        public int getPhaseNumber() { return phaseNumber; }
        public void setPhaseNumber(int phaseNumber) { this.phaseNumber = phaseNumber; }
        
        public String getPhaseName() { return phaseName; }
        public void setPhaseName(String phaseName) { this.phaseName = phaseName; }
        
        public ExecutionState getState() { return state; }
        public void setState(ExecutionState state) { this.state = state; }
        
        public long getStartTimeMs() { return startTimeMs; }
        public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

