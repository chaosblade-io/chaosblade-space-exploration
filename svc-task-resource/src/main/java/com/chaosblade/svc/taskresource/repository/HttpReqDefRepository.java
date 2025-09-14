package com.chaosblade.svc.taskresource.repository;

import com.chaosblade.svc.taskresource.entity.HttpReqDef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HttpReqDefRepository extends JpaRepository<HttpReqDef, Long> {

    Optional<HttpReqDef> findByCode(String code);

    boolean existsByCode(String code);

    List<HttpReqDef> findByApiId(Long apiId);

    Optional<HttpReqDef> findTop1ByApiIdOrderByCreatedAtDesc(Long apiId);

    Page<HttpReqDef> findByNameContaining(String name, Pageable pageable);

    List<HttpReqDef> findByMethod(HttpReqDef.HttpMethod method);

    @Query("SELECT h FROM HttpReqDef h WHERE (:name IS NULL OR h.name LIKE %:name%) " +
            "AND (:method IS NULL OR h.method = :method) " +
            "AND (:apiId IS NULL OR h.apiId = :apiId)")
    Page<HttpReqDef> findByConditions(@Param("name") String name,
                                      @Param("method") HttpReqDef.HttpMethod method,
                                      @Param("apiId") Long apiId,
                                      Pageable pageable);
}

