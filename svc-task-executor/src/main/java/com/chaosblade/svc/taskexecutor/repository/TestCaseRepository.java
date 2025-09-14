package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByTaskId(Long taskId);
    List<TestCase> findByExecutionId(Long executionId);
}

