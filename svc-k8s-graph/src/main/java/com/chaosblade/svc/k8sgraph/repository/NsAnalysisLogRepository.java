package com.chaosblade.svc.k8sgraph.repository;

import com.chaosblade.svc.k8sgraph.entity.NsAnalysisLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 命名空间分析日志Repository
 */
@Repository
public interface NsAnalysisLogRepository extends JpaRepository<NsAnalysisLog, Long> {

    /**
     * 根据任务ID查询日志（按时间升序）
     */
    List<NsAnalysisLog> findByTaskIdOrderByCreatedAtAsc(String taskId);

    /**
     * 根据任务ID分页查询日志
     */
    Page<NsAnalysisLog> findByTaskIdOrderByCreatedAtAsc(String taskId, Pageable pageable);

    /**
     * 根据任务ID和最低日志级别查询
     */
    @Query("SELECT l FROM NsAnalysisLog l WHERE l.taskId = :taskId AND l.level >= :minLevel ORDER BY l.createdAt ASC")
    List<NsAnalysisLog> findByTaskIdAndMinLevel(@Param("taskId") String taskId, @Param("minLevel") int minLevel);

    /**
     * 根据任务ID和阶段查询
     */
    List<NsAnalysisLog> findByTaskIdAndPhaseOrderByCreatedAtAsc(String taskId, Integer phase);

    /**
     * 根据任务ID和级别查询
     */
    List<NsAnalysisLog> findByTaskIdAndLevelOrderByCreatedAtAsc(String taskId, Integer level);

    /**
     * 统计任务的日志数量
     */
    long countByTaskId(String taskId);

    /**
     * 统计任务的错误日志数量
     */
    @Query("SELECT COUNT(l) FROM NsAnalysisLog l WHERE l.taskId = :taskId AND l.level >= 3")
    long countErrorsByTaskId(@Param("taskId") String taskId);

    /**
     * 删除任务的所有日志
     */
    @Modifying
    void deleteByTaskId(String taskId);

    /**
     * 获取任务最新的N条日志
     */
    @Query("SELECT l FROM NsAnalysisLog l WHERE l.taskId = :taskId ORDER BY l.createdAt DESC")
    List<NsAnalysisLog> findLatestByTaskId(@Param("taskId") String taskId, Pageable pageable);
}

