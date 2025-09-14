package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TaskSlo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 任务SLO数据访问接口
 */
@Repository
public interface TaskSloRepository extends JpaRepository<TaskSlo, Long> {

    @Query("SELECT t FROM TaskSlo t WHERE (:p95 IS NULL OR t.p95 = :p95) " +
           "AND (:p99 IS NULL OR t.p99 = :p99) " +
           "AND (:errRate IS NULL OR t.errRate = :errRate) " +
           "AND (:taskId IS NULL OR t.taskId = :taskId) " +
           "AND (:nodeId IS NULL OR t.nodeId = :nodeId)")
    Page<TaskSlo> findByConditions(@Param("p95") Integer p95,
                                   @Param("p99") Integer p99,
                                   @Param("errRate") Integer errRate,
                                   @Param("taskId") Long taskId,
                                   @Param("nodeId") Long nodeId,
                                   Pageable pageable);
}
