package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.ApiTopologyNode;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** API拓扑节点数据访问接口 */
@Repository
public interface ApiTopologyNodeRepository extends JpaRepository<ApiTopologyNode, Long> {

  /** 根据拓扑ID查找所有节点 */
  List<ApiTopologyNode> findByTopologyId(Long topologyId);

  /** 根据拓扑ID分页查找节点 */
  Page<ApiTopologyNode> findByTopologyId(Long topologyId, Pageable pageable);

  /** 根据拓扑ID删除所有节点 */
  void deleteByTopologyId(Long topologyId);

  /** 统计拓扑中的节点数量 */
  long countByTopologyId(Long topologyId);
}
