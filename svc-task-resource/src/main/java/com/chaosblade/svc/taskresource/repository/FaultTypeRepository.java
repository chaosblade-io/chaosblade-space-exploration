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

import com.chaosblade.svc.taskresource.entity.FaultType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 故障类型数据访问接口 */
@Repository
public interface FaultTypeRepository extends JpaRepository<FaultType, Long> {

  /** 根据名称查找故障类型 */
  Optional<FaultType> findByName(String name);

  /** 根据分类查找故障类型列表 */
  List<FaultType> findByCategory(String category);

  /** 根据分类分页查找故障类型 */
  Page<FaultType> findByCategory(String category, Pageable pageable);

  /** 根据启用状态查找故障类型列表 */
  List<FaultType> findByEnabled(Boolean enabled);

  /** 根据启用状态分页查找故障类型 */
  Page<FaultType> findByEnabled(Boolean enabled, Pageable pageable);

  /** 检查故障类型名称是否存在 */
  boolean existsByName(String name);

  /** 根据多个条件查询故障类型 */
  @Query(
      "SELECT ft FROM FaultType ft WHERE "
          + "(:category IS NULL OR ft.category = :category) "
          + "AND (:enabled IS NULL OR ft.enabled = :enabled) "
          + "AND (:faultCode IS NULL OR ft.faultCode = :faultCode) "
          + "AND (:name IS NULL OR ft.name LIKE %:name%)")
  Page<FaultType> findByConditions(
      @Param("category") String category,
      @Param("enabled") Boolean enabled,
      @Param("faultCode") String faultCode,
      @Param("name") String name,
      Pageable pageable);

  /** 根据故障代码查找故障类型 */
  @Query("SELECT ft FROM FaultType ft WHERE ft.faultCode LIKE %:code%")
  List<FaultType> findByFaultCodeContaining(@Param("code") String code);

  /** 获取所有分类 */
  @Query(
      "SELECT DISTINCT ft.category FROM FaultType ft WHERE ft.category IS NOT NULL ORDER BY"
          + " ft.category")
  List<String> findAllCategories();

  /** 统计各分类的故障类型数量 */
  @Query(
      "SELECT ft.category, COUNT(ft) FROM FaultType ft WHERE ft.enabled = true GROUP BY"
          + " ft.category")
  List<Object[]> countByCategory();
}
