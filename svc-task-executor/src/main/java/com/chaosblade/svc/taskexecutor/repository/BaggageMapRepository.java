package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.BaggageMap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BaggageMapRepository extends JpaRepository<BaggageMap, Long> {
  List<BaggageMap> findByExecutionId(Long executionId);

  @Modifying
  @Transactional
  @Query(
      value =
          "INSERT INTO baggage_map (execution_id, service_name, value) VALUES (:executionId,"
              + " :serviceName, :value)\n"
              + "ON DUPLICATE KEY UPDATE value = VALUES(value)",
      nativeQuery = true)
  int upsert(
      @Param("executionId") Long executionId,
      @Param("serviceName") String serviceName,
      @Param("value") String value);
}
