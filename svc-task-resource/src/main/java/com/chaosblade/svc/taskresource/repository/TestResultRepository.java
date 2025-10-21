package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TestResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
  List<TestResult> findByExecutionId(Long executionId);
}
