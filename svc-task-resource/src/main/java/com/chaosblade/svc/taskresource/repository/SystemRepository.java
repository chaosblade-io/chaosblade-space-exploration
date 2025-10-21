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

import com.chaosblade.svc.taskresource.entity.System;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统数据访问接口
 */
@Repository
public interface SystemRepository extends JpaRepository<System, Long> {
    
    /**
     * 根据名称查找系统
     */
    Optional<System> findByName(String name);

    /**
     * 根据系统key查找系统
     */
    Optional<System> findBySystemKey(String systemKey);

    /**
     * 根据所有者查找系统列表
     */
    List<System> findByOwner(String owner);

    /**
     * 根据名称模糊查询系统
     */
    @Query("SELECT s FROM System s WHERE s.name LIKE %:name%")
    Page<System> findByNameContaining(@Param("name") String name, Pageable pageable);

    /**
     * 根据所有者分页查询系统
     */
    Page<System> findByOwner(String owner, Pageable pageable);

    /**
     * 检查系统名称是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查系统key是否存在
     */
    boolean existsBySystemKey(String systemKey);

    /**
     * 根据名称和所有者查询系统
     */
    @Query("SELECT s FROM System s WHERE (:name IS NULL OR s.name LIKE %:name%) AND (:owner IS NULL OR s.owner = :owner)")
    Page<System> findByNameAndOwner(@Param("name") String name, @Param("owner") String owner, Pageable pageable);
}
