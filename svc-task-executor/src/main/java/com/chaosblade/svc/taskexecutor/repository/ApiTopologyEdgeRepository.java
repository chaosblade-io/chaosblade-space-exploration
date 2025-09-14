package com.chaosblade.svc.taskexecutor.repository;

import com.chaosblade.svc.taskexecutor.entity.ApiTopologyEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiTopologyEdgeRepository extends JpaRepository<ApiTopologyEdge, Long> {
    List<ApiTopologyEdge> findByTopologyId(Long topologyId);
}

