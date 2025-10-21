package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.ApiTopologyEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiTopologyEdgeRepository extends JpaRepository<ApiTopologyEdge, Long> {
  List<ApiTopologyEdge> findByTopologyId(Long topologyId);
}
