package com.chaosblade.svc.reqrspproxy.repository;

import com.chaosblade.svc.reqrspproxy.entity.HttpReqDef;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** HTTP请求定义数据访问接口 */
@Repository
public interface HttpReqDefRepository extends JpaRepository<HttpReqDef, Long> {

  /** 根据ID查找请求定义 */
  Optional<HttpReqDef> findById(Long id);

  /** 根据编码查找请求定义 */
  Optional<HttpReqDef> findByCode(String code);

  /** 根据API ID查找请求定义列表 */
  List<HttpReqDef> findByApiId(Long apiId);

  /** 根据HTTP方法查找请求定义列表 */
  List<HttpReqDef> findByMethod(HttpReqDef.HttpMethod method);

  /** 根据名称模糊查找请求定义列表 */
  List<HttpReqDef> findByNameContaining(String name);

  /** 检查编码是否存在 */
  boolean existsByCode(String code);

  /** 根据API ID检查是否存在请求定义 */
  boolean existsByApiId(Long apiId);

  /** 查找所有有效的请求定义（按创建时间倒序） */
  @Query("SELECT h FROM HttpReqDef h ORDER BY h.createdAt DESC")
  List<HttpReqDef> findAllOrderByCreatedAtDesc();

  /** 根据URL模板模糊查找 */
  @Query("SELECT h FROM HttpReqDef h WHERE h.urlTemplate LIKE %:urlPattern%")
  List<HttpReqDef> findByUrlTemplateContaining(@Param("urlPattern") String urlPattern);

  /** 根据多个ID查找请求定义列表 */
  @Query("SELECT h FROM HttpReqDef h WHERE h.id IN :ids")
  List<HttpReqDef> findByIdIn(@Param("ids") List<Long> ids);

  /** 根据API ID和HTTP方法查找请求定义 */
  @Query("SELECT h FROM HttpReqDef h WHERE h.apiId = :apiId AND h.method = :method")
  List<HttpReqDef> findByApiIdAndMethod(
      @Param("apiId") Long apiId, @Param("method") HttpReqDef.HttpMethod method);
}
