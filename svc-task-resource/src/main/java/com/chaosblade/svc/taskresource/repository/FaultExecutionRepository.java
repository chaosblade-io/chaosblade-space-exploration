package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.FaultExecution;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 故障执行Repository - 基于fault_injection_results表 */
@Repository
public interface FaultExecutionRepository extends JpaRepository<FaultExecution, Long> {

  /** 根据执行ID查找执行记录 */
  List<FaultExecution> findByExecutionId(Long executionId);

  /** 根据执行ID分页查找执行记录 */
  Page<FaultExecution> findByExecutionId(Long executionId, Pageable pageable);

  /** 根据拓扑节点ID查找执行记录 */
  List<FaultExecution> findByTopologyNodeId(Long topologyNodeId);

  /** 根据模板类型查找执行记录 */
  List<FaultExecution> findByTemplateType(String templateType);

  /** 根据结果状态查找执行记录 */
  List<FaultExecution> findByResultStatus(FaultExecution.ResultStatus resultStatus);

  /** 根据结果状态分页查找执行记录 */
  Page<FaultExecution> findByResultStatus(
      FaultExecution.ResultStatus resultStatus, Pageable pageable);

  /** 根据多个条件查询故障执行 */
  @Query(
      "SELECT fe FROM FaultExecution fe WHERE "
          + "(:executionId IS NULL OR fe.executionId = :executionId) "
          + "AND (:topologyNodeId IS NULL OR fe.topologyNodeId = :topologyNodeId) "
          + "AND (:templateType IS NULL OR fe.templateType = :templateType) "
          + "AND (:resultStatus IS NULL OR fe.resultStatus = :resultStatus)")
  Page<FaultExecution> findByConditions(
      @Param("executionId") Long executionId,
      @Param("topologyNodeId") Long topologyNodeId,
      @Param("templateType") String templateType,
      @Param("resultStatus") FaultExecution.ResultStatus resultStatus,
      Pageable pageable);

  /** 统计各结果状态的执行数量 */
  @Query("SELECT fe.resultStatus, COUNT(fe) FROM FaultExecution fe GROUP BY fe.resultStatus")
  List<Object[]> countByResultStatus();
}
