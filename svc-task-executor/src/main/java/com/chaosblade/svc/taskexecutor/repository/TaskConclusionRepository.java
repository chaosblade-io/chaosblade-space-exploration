package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.TaskConclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskConclusionRepository extends JpaRepository<TaskConclusion, Long> {
    Optional<TaskConclusion> findByExecutionId(Long executionId);
}

