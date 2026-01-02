package com.chaosblade.svc.k8sgraph.service;

import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.*;
import com.chaosblade.svc.k8sgraph.domain.risk.pipeline.PipelineExecutionStatus.ExecutionState;
import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import com.chaosblade.svc.k8sgraph.service.risk.RiskPipelineOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 命名空间分析执行服务
 *
 * 负责：
 * 1. 异步执行风险分析Pipeline
 * 2. 实时更新任务状态到数据库和缓存
 * 3. 持久化分析结果
 * 4. 支持任务取消
 *
 * 注意：使用Executor直接提交任务，避免@Async自调用问题
 */
@Service
public class NsAnalysisExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(NsAnalysisExecutionService.class);

    /** 存储正在执行的任务，用于取消检测 */
    private final ConcurrentHashMap<String, Boolean> cancelledTasks = new ConcurrentHashMap<>();

    /** 存储任务与执行状态的映射 */
    private final ConcurrentHashMap<String, PipelineExecutionStatus> taskStatusMap = new ConcurrentHashMap<>();

    @Autowired
    private RiskPipelineOrchestrator pipelineOrchestrator;

    @Autowired
    private NsAnalysisPersistenceService persistenceService;

    @Autowired
    private NsAnalysisCacheService cacheService;

    @Autowired
    @Qualifier("pipelineExecutor")
    private Executor pipelineExecutor;

    /**
     * 启动分析任务执行（立即返回，任务异步执行）
     *
     * 使用Executor直接提交任务，避免Spring @Async自调用不生效的问题
     */
    public void startExecution(NsAnalysisTask task) {
        String taskId = task.getTaskId();
        logger.info("Starting execution for task: {}, namespace: {}", taskId, task.getNamespace());

        // 清除可能存在的取消标记
        cancelledTasks.remove(taskId);

        // 创建执行状态
        PipelineExecutionStatus status = new PipelineExecutionStatus(taskId, task.getNamespace());
        taskStatusMap.put(taskId, status);

        // 使用Executor提交异步任务（立即返回）
        pipelineExecutor.execute(() -> executeTaskInternal(task, status));

        logger.info("Task submitted to executor, returning immediately: taskId={}", taskId);
    }

    /**
     * 取消任务执行
     */
    public boolean cancelExecution(String taskId) {
        PipelineExecutionStatus status = taskStatusMap.get(taskId);
        if (status == null) {
            logger.warn("Task not found in execution: {}", taskId);
            return false;
        }
        
        if (status.getState() != ExecutionState.PENDING && 
            status.getState() != ExecutionState.RUNNING) {
            logger.warn("Task cannot be cancelled, current state: {}", status.getState());
            return false;
        }
        
        // 标记为取消
        cancelledTasks.put(taskId, true);
        logger.info("Task marked for cancellation: {}", taskId);
        return true;
    }

    /**
     * 检查任务是否被取消
     */
    public boolean isCancelled(String taskId) {
        return cancelledTasks.getOrDefault(taskId, false);
    }

    /**
     * 获取任务执行状态
     */
    public PipelineExecutionStatus getExecutionStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    /**
     * 内部执行分析任务（在线程池中执行）
     */
    private void executeTaskInternal(NsAnalysisTask task, PipelineExecutionStatus status) {
        String taskId = task.getTaskId();
        String namespace = task.getNamespace();
        long startTime = System.currentTimeMillis();

        try {
            // 更新状态为运行中
            persistenceService.startTask(taskId);
            cacheService.updateProgress(taskId, 0, 5, "开始分析...");
            status.setState(ExecutionState.RUNNING);

            logger.info("Task execution started in thread pool: taskId={}, namespace={}, thread={}",
                taskId, namespace, Thread.currentThread().getName());

            // 检查是否被取消
            if (isCancelled(taskId)) {
                handleCancellation(taskId, status);
                return;
            }

            // 执行Pipeline并持久化结果
            PipelineResult result = executePipelineWithPersistence(task, status);

            // 检查是否被取消
            if (isCancelled(taskId)) {
                handleCancellation(taskId, status);
                return;
            }

            // 完成任务
            if (result.isSuccess()) {
                persistenceService.completeTask(taskId);
                cacheService.updateProgress(taskId, 6, 100, "分析完成");
                status.setState(ExecutionState.COMPLETED);
                status.setResult(result);

                // 缓存结果
                cacheResult(taskId, namespace, result);

                logger.info("Task completed successfully: taskId={}, duration={}ms",
                    taskId, System.currentTimeMillis() - startTime);
            } else {
                persistenceService.failTask(taskId, "ANALYSIS_FAILED", result.getErrorMessage());
                cacheService.updateProgress(taskId, status.getCurrentPhase(),
                    status.getProgressPercent(), "分析失败: " + result.getErrorMessage());
                status.setState(ExecutionState.FAILED);
                status.setErrorMessage(result.getErrorMessage());

                logger.error("Task failed: taskId={}, error={}", taskId, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Task execution failed: taskId={}, error={}", taskId, e.getMessage(), e);
            persistenceService.failTask(taskId, "EXECUTION_ERROR", e.getMessage());
            cacheService.updateProgress(taskId, status.getCurrentPhase(),
                status.getProgressPercent(), "执行异常: " + e.getMessage());
            status.setState(ExecutionState.FAILED);
            status.setErrorMessage(e.getMessage());
        } finally {
            // 清理
            cancelledTasks.remove(taskId);
        }
    }

    /**
     * 执行Pipeline并持久化结果
     */
    private PipelineResult executePipelineWithPersistence(NsAnalysisTask task, PipelineExecutionStatus status) {
        String taskId = task.getTaskId();
        String namespace = task.getNamespace();
        int topN = task.getTopN() != null ? task.getTopN() : 5;

        // 启动进度更新线程
        Thread progressThread = startProgressMonitor(taskId, status);

        PipelineResult result;
        try {
            // 执行Pipeline
            result = pipelineOrchestrator.execute(namespace, status);

            // 检查是否被取消
            if (isCancelled(taskId)) {
                throw new RuntimeException("Task cancelled by user");
            }
        } finally {
            // 停止进度监控
            if (progressThread != null) {
                progressThread.interrupt();
            }
        }

        if (result.isSuccess()) {
            // 持久化结果（包含风险数据）
            persistResult(taskId, namespace, result, topN);
        }

        return result;
    }

    /**
     * 启动进度监控线程
     */
    private Thread startProgressMonitor(String taskId, PipelineExecutionStatus status) {
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 检查是否被取消
                    if (isCancelled(taskId)) {
                        break;
                    }

                    // 从status同步进度到持久层
                    int phase = status.getCurrentPhase();
                    int percent = status.getProgressPercent();
                    String message = getPhaseMessage(status);

                    persistenceService.updateProgress(taskId, phase, percent);
                    cacheService.updateProgress(taskId, phase, percent, message);

                    Thread.sleep(1000); // 每秒更新一次
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "progress-monitor-" + taskId);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 获取阶段消息
     */
    private String getPhaseMessage(PipelineExecutionStatus status) {
        int phase = status.getCurrentPhase();
        if (phase >= 1 && phase <= status.getPhaseStatuses().size()) {
            PipelineExecutionStatus.PhaseStatus phaseStatus = status.getPhaseStatuses().get(phase - 1);
            return phaseStatus.getMessage() != null ? phaseStatus.getMessage() : phaseStatus.getPhaseName();
        }
        return "执行中...";
    }

    /**
     * 持久化分析结果（包含风险数据）
     *
     * 将所有结果持久化到ns_analysis_results表
     */
    private void persistResult(String taskId, String namespace, PipelineResult result, int topN) {
        try {
            Map<String, Object> resultData = new HashMap<>();

            // 统计数据
            int servicesCount = result.getPhase1Results().size();
            resultData.put("servicesCount", servicesCount);

            // 计算触发的规则总数和关键风险数
            int rulesTriggered = 0;
            int criticalCount = 0;
            for (ServiceRiskProfile profile : result.getPhase1Results().values()) {
                rulesTriggered += profile.getTriggeredRules().size();
                if ("CRITICAL".equals(profile.getRiskLevel()) || "HIGH".equals(profile.getRiskLevel())) {
                    criticalCount++;
                }
            }
            resultData.put("rulesTriggered", rulesTriggered);
            resultData.put("criticalCount", criticalCount);

            // 从Phase3获取最高风险信息
            if (result.getPhase3Result() != null && result.getPhase3Result().getRankedServices() != null
                    && !result.getPhase3Result().getRankedServices().isEmpty()) {
                RiskRankResult.RankedService topService = result.getPhase3Result().getRankedServices().get(0);
                resultData.put("maxRiskScore", topService.getRiskRankScore());
                resultData.put("topRiskService", topService.getServiceName());
                resultData.put("riskLevel", topService.getRiskLevel());

                // 构建排名服务列表用于存储
                resultData.put("rankedServices", buildRankedServicesList(result, topN));
            }

            // 存储所有阶段结果
            // Phase1: 规则扫描结果
            resultData.put("phase1Result", result.getPhase1Results());

            // Phase2: 拓扑风险分析结果
            if (result.getPhase2Result() != null) {
                resultData.put("phase2Result", result.getPhase2Result());
            }

            // Phase3: 风险排名结果
            resultData.put("phase3Ranking", result.getPhase3Result());

            // Phase4: Trace分析结果
            if (result.getPhase4Results() != null && !result.getPhase4Results().isEmpty()) {
                resultData.put("phase4Results", result.getPhase4Results());
            }

            // Phase5: 综合分析与故障场景生成结果
            if (result.getPhase5Result() != null) {
                resultData.put("phase5Result", result.getPhase5Result());
            }

            // 各阶段执行耗时
            Map<String, Long> phaseTimings = new HashMap<>();
            phaseTimings.put("phase1TimeMs", result.getPhase1TimeMs());
            phaseTimings.put("phase2TimeMs", result.getPhase2TimeMs());
            phaseTimings.put("phase3TimeMs", result.getPhase3TimeMs());
            phaseTimings.put("phase4TimeMs", result.getPhase4TimeMs());
            phaseTimings.put("phase5TimeMs", result.getPhase5TimeMs());
            phaseTimings.put("phase6TimeMs", result.getPhase6TimeMs());
            phaseTimings.put("totalTimeMs", result.getTotalTimeMs());
            resultData.put("phaseTimings", phaseTimings);

            persistenceService.saveResult(taskId, resultData);
            logger.info("Result persisted for task: {}", taskId);
        } catch (Exception e) {
            logger.error("Failed to persist result: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 构建排名服务列表
     */
    private List<Map<String, Object>> buildRankedServicesList(PipelineResult result, int topN) {
        List<Map<String, Object>> rankedList = new ArrayList<>();

        if (result.getPhase3Result() != null && result.getPhase3Result().getRankedServices() != null) {
            List<RiskRankResult.RankedService> rankedServices = result.getPhase3Result().getRankedServices();
            int count = Math.min(topN, rankedServices.size());

            for (int i = 0; i < count; i++) {
                RiskRankResult.RankedService rankedService = rankedServices.get(i);
                Map<String, Object> serviceData = new HashMap<>();

                serviceData.put("rank", i + 1);
                serviceData.put("serviceName", rankedService.getServiceName());
                serviceData.put("riskLevel", rankedService.getRiskLevel());
                serviceData.put("riskScore", rankedService.getRiskRankScore());
                serviceData.put("inherentScore", rankedService.getInherentRiskScore());
                serviceData.put("propagatedScore", rankedService.getPropagatedRiskScore());
                serviceData.put("description", buildRiskDescription(rankedService, result));
                serviceData.put("recommendations", buildRecommendations(rankedService, result));

                // 添加LLM分析（如果有）
                if (result.getPhase5Result() != null && result.getPhase5Result().getServiceAnalyses() != null) {
                    ComprehensiveAnalysisResult.ServiceComprehensiveAnalysis serviceAnalysis =
                        result.getPhase5Result().getServiceAnalyses().get(rankedService.getServiceName());
                    if (serviceAnalysis != null) {
                        serviceData.put("llmAnalysis", serviceAnalysis);
                    }
                }

                rankedList.add(serviceData);
            }
        }

        return rankedList;
    }

    /**
     * 构建风险描述
     */
    private String buildRiskDescription(RiskRankResult.RankedService rankedService, PipelineResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("服务 %s 综合风险评分: %.2f",
            rankedService.getServiceName(), rankedService.getRiskRankScore()));
        sb.append(String.format("\n固有风险: %.2f, 传播风险: %.2f",
            rankedService.getInherentRiskScore(), rankedService.getPropagatedRiskScore()));

        // 添加触发的规则信息
        ServiceRiskProfile profile = result.getPhase1Results().get(rankedService.getServiceName());
        if (profile != null && !profile.getTriggeredRules().isEmpty()) {
            sb.append("\n触发的风险规则: ");
            for (TriggeredRule rule : profile.getTriggeredRules()) {
                sb.append("\n- ").append(rule.getRuleName()).append(" (").append(rule.getScore()).append("分)");
            }
        }

        return sb.toString();
    }

    /**
     * 构建建议措施
     */
    private List<String> buildRecommendations(RiskRankResult.RankedService rankedService, PipelineResult result) {
        List<String> recommendations = new ArrayList<>();

        ServiceRiskProfile profile = result.getPhase1Results().get(rankedService.getServiceName());
        if (profile != null) {
            for (TriggeredRule rule : profile.getTriggeredRules()) {
                if (rule.getRemediation() != null && !rule.getRemediation().isEmpty()) {
                    recommendations.add(rule.getRemediation());
                }
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("建议进行故障演练验证系统弹性");
            recommendations.add("检查服务的资源配置和副本数");
        }

        return recommendations;
    }

    /**
     * 处理任务取消
     */
    private void handleCancellation(String taskId, PipelineExecutionStatus status) {
        logger.info("Task cancelled: {}", taskId);
        persistenceService.failTask(taskId, "CANCELLED", "任务被用户取消");
        cacheService.updateProgress(taskId, status.getCurrentPhase(),
            status.getProgressPercent(), "任务已取消");
        status.setState(ExecutionState.FAILED);
        status.setErrorMessage("任务被用户取消");
    }

    /**
     * 缓存结果（包含所有阶段数据）
     */
    private void cacheResult(String taskId, String namespace, PipelineResult result) {
        try {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("taskId", taskId);
            resultMap.put("namespace", namespace);
            resultMap.put("success", result.isSuccess());
            resultMap.put("executionTime", result.getExecutionTime());

            // 缓存摘要信息
            resultMap.put("summary", result.getSummary());

            // 缓存各阶段执行耗时
            Map<String, Long> phaseTimings = new HashMap<>();
            phaseTimings.put("phase1TimeMs", result.getPhase1TimeMs());
            phaseTimings.put("phase2TimeMs", result.getPhase2TimeMs());
            phaseTimings.put("phase3TimeMs", result.getPhase3TimeMs());
            phaseTimings.put("phase4TimeMs", result.getPhase4TimeMs());
            phaseTimings.put("phase5TimeMs", result.getPhase5TimeMs());
            phaseTimings.put("phase6TimeMs", result.getPhase6TimeMs());
            phaseTimings.put("totalTimeMs", result.getTotalTimeMs());
            resultMap.put("phaseTimings", phaseTimings);

            // 缓存各阶段结果（完整数据）
            resultMap.put("phase1Results", result.getPhase1Results());
            resultMap.put("phase2Result", result.getPhase2Result());
            resultMap.put("phase3Result", result.getPhase3Result());
            resultMap.put("phase4Results", result.getPhase4Results());
            resultMap.put("phase5Result", result.getPhase5Result());

            cacheService.cacheResult(taskId, resultMap);
            cacheService.cacheLatestTaskId(namespace, taskId);

            logger.info("Cached complete result for task: {}, phases: 1-5", taskId);
        } catch (Exception e) {
            logger.warn("Failed to cache result: taskId={}", taskId, e);
        }
    }
}

