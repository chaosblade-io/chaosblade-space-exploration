package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.System;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemRepository extends JpaRepository<System, Long> {
}

