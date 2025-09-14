package com.chaosblade.svc.reqrspproxy.repository;

import com.chaosblade.svc.reqrspproxy.entity.RequestPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestPatternRepository extends JpaRepository<RequestPattern, Long> {
    List<RequestPattern> findByExecutionId(Long executionId);
    List<RequestPattern> findByExecutionIdAndServiceName(Long executionId, String serviceName);
}
