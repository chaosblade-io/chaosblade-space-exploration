package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.HttpReqDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HttpReqDefRepository extends JpaRepository<HttpReqDef, Long> {
    List<HttpReqDef> findByApiId(Long apiId);
}

