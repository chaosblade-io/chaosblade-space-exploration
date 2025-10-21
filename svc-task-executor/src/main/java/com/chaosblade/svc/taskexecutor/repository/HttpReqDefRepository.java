package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.HttpReqDef;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HttpReqDefRepository extends JpaRepository<HttpReqDef, Long> {
  List<HttpReqDef> findByApiId(Long apiId);
}
