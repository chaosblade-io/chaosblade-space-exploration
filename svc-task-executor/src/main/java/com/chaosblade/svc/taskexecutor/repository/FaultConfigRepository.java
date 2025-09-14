package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.FaultConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FaultConfigRepository extends JpaRepository<FaultConfig, Long> {
    List<FaultConfig> findByNodeIdIn(Collection<Long> nodeIds);

    @Query("SELECT f FROM FaultConfig f WHERE f.nodeId IN :nodeIds AND (f.taskId IS NULL OR f.taskId = :taskId)")
    List<FaultConfig> findByNodeIdsWithTaskScope(@Param("nodeIds") Collection<Long> nodeIds,
                                                 @Param("taskId") Long taskId);
}

