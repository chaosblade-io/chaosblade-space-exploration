package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByExecutionId(Long executionId);
}

