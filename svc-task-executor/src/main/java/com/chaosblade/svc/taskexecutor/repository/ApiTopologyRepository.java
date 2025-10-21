package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.ApiTopology;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiTopologyRepository extends JpaRepository<ApiTopology, Long> {
  List<ApiTopology> findByApiId(Long apiId);

  Optional<ApiTopology> findBySystemIdAndApiId(Long systemId, Long apiId);
}
