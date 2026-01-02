package com.chaosblade.svc.k8sgraph.repository;

import com.chaosblade.svc.k8sgraph.entity.NsAnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 命名空间分析结果数据访问接口
 *
 * 注意: task_id 在 ns_analysis_results 表中是外键引用 ns_analysis_tasks.id (数据库主键)
 */
@Repository
public interface NsAnalysisResultRepository extends JpaRepository<NsAnalysisResult, Long> {

    /**
     * 根据任务数据库ID查找结果
     */
    Optional<NsAnalysisResult> findByTaskDbId(Long taskDbId);

    /**
     * 根据命名空间查找结果列表
     */
    List<NsAnalysisResult> findByNamespace(String namespace);

    /**
     * 根据命名空间分页查询
     */
    Page<NsAnalysisResult> findByNamespace(String namespace, Pageable pageable);

    /**
     * 查找命名空间最新的结果
     */
    Optional<NsAnalysisResult> findFirstByNamespaceOrderByCreatedAtDesc(String namespace);

    /**
     * 根据命名空间查找最新N条结果
     */
    @Query("SELECT r FROM NsAnalysisResult r WHERE r.namespace = :namespace ORDER BY r.createdAt DESC")
    List<NsAnalysisResult> findLatestByNamespace(@Param("namespace") String namespace, Pageable pageable);

    /**
     * 检查任务数据库ID是否存在结果
     */
    boolean existsByTaskDbId(Long taskDbId);

    /**
     * 统计命名空间的结果数量
     */
    long countByNamespace(String namespace);

    /**
     * 删除指定任务的结果
     */
    void deleteByTaskDbId(Long taskDbId);
}

