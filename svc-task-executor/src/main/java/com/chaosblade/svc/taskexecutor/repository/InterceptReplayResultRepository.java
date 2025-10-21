package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.InterceptReplayResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterceptReplayResultRepository
    extends JpaRepository<InterceptReplayResult, Long> {
  List<InterceptReplayResult> findByExecutionId(Long executionId);
}
