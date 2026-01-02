package com.chaosblade.svc.k8sgraph.repository;

import com.chaosblade.svc.k8sgraph.entity.NsAnalysisTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 命名空间分析任务数据访问接口
 */
@Repository
public interface NsAnalysisTaskRepository extends JpaRepository<NsAnalysisTask, Long> {

    /**
     * 根据任务ID查找
     */
    Optional<NsAnalysisTask> findByTaskId(String taskId);

    /**
     * 根据命名空间查找任务列表
     */
    List<NsAnalysisTask> findByNamespace(String namespace);

    /**
     * 根据命名空间分页查询
     */
    Page<NsAnalysisTask> findByNamespace(String namespace, Pageable pageable);

    /**
     * 根据状态查找任务列表
     */
    List<NsAnalysisTask> findByStatus(NsAnalysisTask.TaskStatus status);

    /**
     * 根据命名空间和状态查找
     */
    List<NsAnalysisTask> findByNamespaceAndStatus(String namespace, NsAnalysisTask.TaskStatus status);

    /**
     * 查找命名空间最新的任务
     */
    Optional<NsAnalysisTask> findFirstByNamespaceOrderByCreatedAtDesc(String namespace);

    /**
     * 查找命名空间最新完成的任务
     */
    @Query("SELECT t FROM NsAnalysisTask t WHERE t.namespace = :namespace AND t.status = 'COMPLETED' ORDER BY t.finishedAt DESC")
    List<NsAnalysisTask> findLatestCompletedByNamespace(@Param("namespace") String namespace, Pageable pageable);

    /**
     * 根据条件分页查询
     */
    @Query("SELECT t FROM NsAnalysisTask t WHERE " +
           "(:namespace IS NULL OR t.namespace = :namespace) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:startTime IS NULL OR t.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR t.createdAt <= :endTime)")
    Page<NsAnalysisTask> findByConditions(
            @Param("namespace") String namespace,
            @Param("status") NsAnalysisTask.TaskStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 检查任务ID是否存在
     */
    boolean existsByTaskId(String taskId);

    /**
     * 统计命名空间的任务数量
     */
    long countByNamespace(String namespace);

    /**
     * 统计指定状态的任务数量
     */
    long countByStatus(NsAnalysisTask.TaskStatus status);

    /**
     * 查找运行中的任务
     */
    @Query("SELECT t FROM NsAnalysisTask t WHERE t.status = 'RUNNING' ORDER BY t.startedAt ASC")
    List<NsAnalysisTask> findRunningTasks();

    /**
     * 查找待处理的任务
     */
    @Query("SELECT t FROM NsAnalysisTask t WHERE t.status = 'PENDING' ORDER BY t.createdAt ASC")
    List<NsAnalysisTask> findPendingTasks();
}

