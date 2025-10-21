package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.TaskExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

  @Query(
      "SELECT l FROM TaskExecutionLog l WHERE l.executionId = :executionId "
          + "AND (:minLevel IS NULL OR l.level >= :minLevel) ORDER BY l.ts ASC")
  Page<TaskExecutionLog> findByExecutionIdOrderByTsAsc(
      @Param("executionId") Long executionId,
      @Param("minLevel") Integer minLevel,
      Pageable pageable);
}
