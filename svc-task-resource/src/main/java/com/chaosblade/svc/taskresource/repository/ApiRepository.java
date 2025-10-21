package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.Api;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** API数据访问接口 */
@Repository
public interface ApiRepository extends JpaRepository<Api, Long> {

  /** 根据系统ID查找API列表 */
  List<Api> findBySystemId(Long systemId);

  /** 根据系统ID分页查找API */
  Page<Api> findBySystemId(Long systemId, Pageable pageable);

  /** 根据系统ID和操作ID查找API */
  Optional<Api> findBySystemIdAndOperationId(Long systemId, String operationId);

  /** 根据系统ID和版本查找API */
  List<Api> findBySystemIdAndVersion(Long systemId, String version);

  /** 根据系统ID和HTTP方法查找API */
  List<Api> findBySystemIdAndMethod(Long systemId, String method);

  /** 根据系统ID和路径查找API */
  List<Api> findBySystemIdAndPath(Long systemId, String path);

  /** 检查操作ID在系统内是否存在 */
  boolean existsBySystemIdAndOperationId(Long systemId, String operationId);

  /** 根据系统ID删除所有API */
  void deleteBySystemId(Long systemId);

  /** 根据多个条件查询API */
  @Query(
      "SELECT a FROM Api a WHERE a.systemId = :systemId "
          + "AND (:method IS NULL OR a.method = :method) "
          + "AND (:path IS NULL OR a.path LIKE %:path%) "
          + "AND (:tags IS NULL OR a.tags LIKE %:tags%) "
          + "AND (:version IS NULL OR a.version = :version)")
  Page<Api> findByConditions(
      @Param("systemId") Long systemId,
      @Param("method") String method,
      @Param("path") String path,
      @Param("tags") String tags,
      @Param("version") String version,
      Pageable pageable);

  /** 根据标签查找API */
  @Query("SELECT a FROM Api a WHERE a.systemId = :systemId AND a.tags LIKE %:tag%")
  List<Api> findBySystemIdAndTag(@Param("systemId") Long systemId, @Param("tag") String tag);

  /** 统计系统中的API数量 */
  long countBySystemId(Long systemId);

  /** 根据系统ID和版本统计API数量 */
  long countBySystemIdAndVersion(Long systemId, String version);

  /** 根据系统ID和HTTP方法统计API数量 */
  long countBySystemIdAndMethod(Long systemId, String method);
}
