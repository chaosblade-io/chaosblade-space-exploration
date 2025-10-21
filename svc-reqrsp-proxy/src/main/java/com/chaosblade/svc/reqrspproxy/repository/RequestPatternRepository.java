package com.chaosblade.svc.reqrspproxy.repository;

import com.chaosblade.svc.reqrspproxy.entity.RequestPattern;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestPatternRepository extends JpaRepository<RequestPattern, Long> {
  List<RequestPattern> findByExecutionId(Long executionId);

  List<RequestPattern> findByExecutionIdAndServiceName(Long executionId, String serviceName);
}
