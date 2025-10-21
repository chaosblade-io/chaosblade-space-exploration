package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.DetectionTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 检测任务数据访问接口 */
@Repository
public interface DetectionTaskRepository extends JpaRepository<DetectionTask, Long> {

  /** 根据名称查找检测任务 */
  Optional<DetectionTask> findByName(String name);

  /** 根据故障配置ID查找任务 */
  List<DetectionTask> findByFaultConfigurationsId(Long faultConfigurationsId);

  /** 根据SLO ID查找任务 */
  List<DetectionTask> findBySloId(Long sloId);

  /** 检查任务名称是否存在 */
  boolean existsByName(String name);

  /** 根据多个条件查询检测任务 */
  @Query(
      "SELECT dt FROM DetectionTask dt WHERE "
          + "(:faultConfigurationsId IS NULL OR dt.faultConfigurationsId = :faultConfigurationsId) "
          + "AND (:systemId IS NULL OR dt.systemId = :systemId) "
          + "AND (:apiId IS NULL OR dt.apiId = :apiId) "
          + "AND (:sloId IS NULL OR dt.sloId = :sloId) "
          + "AND (:createdBy IS NULL OR dt.createdBy = :createdBy) "
          + "AND (:name IS NULL OR dt.name LIKE %:name%) "
          + "AND (:startDate IS NULL OR dt.createdAt >= :startDate) "
          + "AND (:endDate IS NULL OR dt.createdAt <= :endDate) "
          + "AND dt.archivedAt IS NULL")
  Page<DetectionTask> findByConditions(
      @Param("faultConfigurationsId") Long faultConfigurationsId,
      @Param("systemId") Long systemId,
      @Param("apiId") Long apiId,
      @Param("sloId") Long sloId,
      @Param("createdBy") String createdBy,
      @Param("name") String name,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable);

  /** 统计各系统的任务数量 */
  @Query("SELECT dt.systemId, COUNT(dt) FROM DetectionTask dt GROUP BY dt.systemId")
  List<Object[]> countBySystemId();

  /** 统计指定时间范围内的任务数量 */
  @Query(
      "SELECT COUNT(dt) FROM DetectionTask dt WHERE dt.createdAt BETWEEN :startDate AND :endDate")
  long countByDateRange(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  /** 查找未归档的任务（分页） */
  @Query("SELECT t FROM DetectionTask t WHERE t.archivedAt IS NULL")
  Page<DetectionTask> findActiveTasksWithPagination(Pageable pageable);

  /** 查找已归档的任务（分页） */
  @Query("SELECT t FROM DetectionTask t WHERE t.archivedAt IS NOT NULL")
  Page<DetectionTask> findArchivedTasksWithPagination(Pageable pageable);
}
