package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiTopologyNodeRepository extends JpaRepository<ApiTopologyNode, Long> {
    List<ApiTopologyNode> findByTopologyId(Long topologyId);
}

