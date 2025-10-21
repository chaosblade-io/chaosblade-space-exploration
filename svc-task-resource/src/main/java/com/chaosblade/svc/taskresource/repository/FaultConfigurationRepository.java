package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.FaultConfiguration;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 故障配置Repository */
@Repository
public interface FaultConfigurationRepository extends JpaRepository<FaultConfiguration, Long> {

  /** 根据故障类型ID查找配置列表 */
  List<FaultConfiguration> findByFaultTypeId(Long faultTypeId);

  /** 根据故障类型ID分页查找配置 */
  Page<FaultConfiguration> findByFaultTypeId(Long faultTypeId, Pageable pageable);

  /** 根据配置ID查找配置列表 */
  List<FaultConfiguration> findByConfigId(Long configId);

  /** 根据节点ID查找配置列表 */
  List<FaultConfiguration> findByNodeId(Long nodeId);

  /** 根据多个条件查找配置 */
  @Query(
      "SELECT fc FROM FaultConfiguration fc WHERE "
          + "(:faultTypeId IS NULL OR fc.faultTypeId = :faultTypeId) "
          + "AND (:configId IS NULL OR fc.configId = :configId) "
          + "AND (:nodeId IS NULL OR fc.nodeId = :nodeId)")
  Page<FaultConfiguration> findByConditions(
      @Param("faultTypeId") Long faultTypeId,
      @Param("configId") Long configId,
      @Param("nodeId") Long nodeId,
      Pageable pageable);

  /** 统计各故障类型的配置数量 */
  @Query("SELECT fc.faultTypeId, COUNT(fc) FROM FaultConfiguration fc GROUP BY fc.faultTypeId")
  List<Object[]> countByFaultTypeId();
}
