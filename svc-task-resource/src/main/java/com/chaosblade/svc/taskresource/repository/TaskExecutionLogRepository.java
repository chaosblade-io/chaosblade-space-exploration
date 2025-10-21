package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TaskExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {
  List<TaskExecutionLog> findByExecutionIdOrderByTsAsc(Long executionId);
}
