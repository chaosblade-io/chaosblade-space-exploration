package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TaskExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {
    List<TaskExecutionLog> findByExecutionIdOrderByTsAsc(Long executionId);
}

