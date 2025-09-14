package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.InterceptReplayResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterceptReplayResultRepository extends JpaRepository<InterceptReplayResult, Long> {
    List<InterceptReplayResult> findByExecutionId(Long executionId);
}

