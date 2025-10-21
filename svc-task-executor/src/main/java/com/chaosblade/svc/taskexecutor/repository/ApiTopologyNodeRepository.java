package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiTopologyNodeRepository extends JpaRepository<ApiTopologyNode, Long> {
  List<ApiTopologyNode> findByTopologyId(Long topologyId);
}
