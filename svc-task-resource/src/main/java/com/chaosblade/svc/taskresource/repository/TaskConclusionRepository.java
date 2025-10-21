package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TaskConclusion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskConclusionRepository extends JpaRepository<TaskConclusion, Long> {
  Optional<TaskConclusion> findByExecutionId(Long executionId);
}
