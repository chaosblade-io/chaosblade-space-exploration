package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    @Query("SELECT te FROM TaskExecution te WHERE te.taskId = :taskId AND te.status IN ('INIT','GENERATING_CASES','ANALYZING_PATTERNS','RECORDING_READY','INJECTING_AND_REPLAYING','RULES_READY','LOAD_TEST_BASELINE')")
    List<TaskExecution> findRunningByTaskId(@Param("taskId") Long taskId);

    Optional<TaskExecution> findTop1ByTaskIdOrderByIdDesc(Long taskId);

    @Modifying
    @Query("UPDATE TaskExecution te SET te.status = :status WHERE te.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}

