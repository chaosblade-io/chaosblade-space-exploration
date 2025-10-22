/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.ApiTopology;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** API拓扑数据访问接口 */
@Repository
public interface ApiTopologyRepository extends JpaRepository<ApiTopology, Long> {

  /** 根据系统ID查找拓扑列表 */
  List<ApiTopology> findBySystemId(Long systemId);

  /** 根据系统ID分页查找拓扑 */
  Page<ApiTopology> findBySystemId(Long systemId, Pageable pageable);

  /** 根据系统ID查找最新的拓扑 */
  @Query("SELECT t FROM ApiTopology t WHERE t.systemId = :systemId ORDER BY t.createdAt DESC")
  Optional<ApiTopology> findLatestBySystemId(@Param("systemId") Long systemId);

  /** 根据API ID查找拓扑 */
  List<ApiTopology> findByApiId(Long apiId);

  /** 根据系统ID和API ID查找拓扑 */
  Optional<ApiTopology> findBySystemIdAndApiId(Long systemId, Long apiId);

  /** 检查拓扑是否存在 */
  boolean existsBySystemIdAndApiId(Long systemId, Long apiId);

  /** 根据源版本查找拓扑 */
  List<ApiTopology> findBySourceVersion(String sourceVersion);

  /** 根据系统ID和源版本查询拓扑 */
  @Query(
      "SELECT t FROM ApiTopology t WHERE t.systemId = :systemId "
          + "AND (:sourceVersion IS NULL OR t.sourceVersion = :sourceVersion)")
  Page<ApiTopology> findBySystemIdAndSourceVersion(
      @Param("systemId") Long systemId,
      @Param("sourceVersion") String sourceVersion,
      Pageable pageable);
}
