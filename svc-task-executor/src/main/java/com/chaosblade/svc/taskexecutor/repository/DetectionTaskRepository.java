package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetectionTaskRepository extends JpaRepository<DetectionTask, Long> {
}

