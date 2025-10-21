package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.FaultConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FaultConfigRepository extends JpaRepository<FaultConfig, Long> {

  @Query(
      "SELECT f FROM FaultConfig f WHERE (:nodeId IS NULL OR f.nodeId = :nodeId) "
          + "AND (:type IS NULL OR f.type = :type) "
          + "AND (:taskId IS NULL OR f.taskId = :taskId)")
  Page<FaultConfig> findByConditions(
      @Param("nodeId") Long nodeId,
      @Param("type") String type,
      @Param("taskId") Long taskId,
      Pageable pageable);
}
