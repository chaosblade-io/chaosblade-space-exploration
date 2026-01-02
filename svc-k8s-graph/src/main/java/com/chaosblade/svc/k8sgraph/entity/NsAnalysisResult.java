package com.chaosblade.svc.k8sgraph.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 命名空间分析结果实体
 *
 * 对应数据库表 ns_analysis_results
 * 注意: task_id 在此表中是外键引用 ns_analysis_tasks.id
 */
@Entity
@Table(name = "ns_analysis_results")
public class NsAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联任务表的ID (非taskId字符串，是数据库主键ID) */
    @Column(name = "task_id", nullable = false)
    private Long taskDbId;

    /** 命名空间（冗余字段） */
    @Column(name = "namespace", nullable = false, length = 128)
    private String namespace;

    /** 结果格式版本 */
    @Column(name = "result_version", nullable = false, length = 16)
    private String resultVersion = "v1";

    // === 摘要信息 ===

    /** 分析的服务总数 */
    @Column(name = "services_count", nullable = false)
    private Integer servicesCount = 0;

    /** 触发的规则总数 */
    @Column(name = "rules_triggered", nullable = false)
    private Integer rulesTriggered = 0;

    /** 关键风险服务数 */
    @Column(name = "critical_count", nullable = false)
    private Integer criticalCount = 0;

    /** 最高风险分数 (最大支持99999999.99) */
    @Column(name = "max_risk_score", precision = 10, scale = 10)
    private BigDecimal maxRiskScore;

    /** 最高风险服务名 */
    @Column(name = "top_risk_service", length = 128)
    private String topRiskService;

    /** 整体风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private RiskLevel riskLevel;

    /** 服务风险排名摘要(Top10) - JSON数组 */
    @Column(name = "ranked_services_summary", columnDefinition = "JSON")
    private String rankedServicesSummary;

    // === 存储相关 ===

    /** 原始数据大小(字节) */
    @Column(name = "data_size_bytes", nullable = false)
    private Integer dataSizeBytes;

    /** 压缩后大小(字节) */
    @Column(name = "compressed_size_bytes")
    private Integer compressedSizeBytes;

    /** 是否压缩 */
    @Column(name = "is_compressed", nullable = false)
    private Boolean isCompressed = false;

    /** 压缩算法 */
    @Column(name = "compression_algo", length = 16)
    private String compressionAlgo;

    /** 存储方式 */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 10)
    private StorageType storageType = StorageType.INLINE;

    /** 外部存储路径/引用 */
    @Column(name = "storage_ref", length = 512)
    private String storageRef;

    /** 完整结果数据(压缩后) - 内联存储 */
    @Lob
    @Column(name = "result_data", columnDefinition = "MEDIUMBLOB")
    private byte[] resultData;

    // === 各阶段结果存储 ===

    /** Phase1规则扫描结果 - JSON */
    @Lob
    @Column(name = "phase1_result", columnDefinition = "LONGTEXT")
    private String phase1Result;

    /** Phase2拓扑风险分析结果 - JSON */
    @Lob
    @Column(name = "phase2_result", columnDefinition = "LONGTEXT")
    private String phase2Result;

    /** Phase3风险排名结果 - JSON */
    @Lob
    @Column(name = "phase3_ranking", columnDefinition = "LONGTEXT")
    private String phase3Ranking;

    /** Phase4 Trace分析结果(Top N服务) - JSON */
    @Lob
    @Column(name = "phase4_results", columnDefinition = "LONGTEXT")
    private String phase4Results;

    /** Phase5综合分析与故障场景生成结果 - JSON */
    @Lob
    @Column(name = "phase5_result", columnDefinition = "LONGTEXT")
    private String phase5Result;

    /** 各阶段执行耗时(ms) - JSON */
    @Column(name = "phase_timings", columnDefinition = "JSON")
    private String phaseTimings;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum StorageType {
        INLINE, FILE, OSS
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskDbId() { return taskDbId; }
    public void setTaskDbId(Long taskDbId) { this.taskDbId = taskDbId; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getResultVersion() { return resultVersion; }
    public void setResultVersion(String resultVersion) { this.resultVersion = resultVersion; }

    public Integer getServicesCount() { return servicesCount; }
    public void setServicesCount(Integer servicesCount) { this.servicesCount = servicesCount; }

    public Integer getRulesTriggered() { return rulesTriggered; }
    public void setRulesTriggered(Integer rulesTriggered) { this.rulesTriggered = rulesTriggered; }

    public Integer getCriticalCount() { return criticalCount; }
    public void setCriticalCount(Integer criticalCount) { this.criticalCount = criticalCount; }

    public BigDecimal getMaxRiskScore() { return maxRiskScore; }
    public void setMaxRiskScore(BigDecimal maxRiskScore) { this.maxRiskScore = maxRiskScore; }

    public String getTopRiskService() { return topRiskService; }
    public void setTopRiskService(String topRiskService) { this.topRiskService = topRiskService; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getRankedServicesSummary() { return rankedServicesSummary; }
    public void setRankedServicesSummary(String rankedServicesSummary) { this.rankedServicesSummary = rankedServicesSummary; }

    public Integer getDataSizeBytes() { return dataSizeBytes; }
    public void setDataSizeBytes(Integer dataSizeBytes) { this.dataSizeBytes = dataSizeBytes; }

    public Integer getCompressedSizeBytes() { return compressedSizeBytes; }
    public void setCompressedSizeBytes(Integer compressedSizeBytes) { this.compressedSizeBytes = compressedSizeBytes; }

    public Boolean getIsCompressed() { return isCompressed; }
    public void setIsCompressed(Boolean isCompressed) { this.isCompressed = isCompressed; }

    public String getCompressionAlgo() { return compressionAlgo; }
    public void setCompressionAlgo(String compressionAlgo) { this.compressionAlgo = compressionAlgo; }

    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

    public byte[] getResultData() { return resultData; }
    public void setResultData(byte[] resultData) { this.resultData = resultData; }

    public String getPhase1Result() { return phase1Result; }
    public void setPhase1Result(String phase1Result) { this.phase1Result = phase1Result; }

    public String getPhase2Result() { return phase2Result; }
    public void setPhase2Result(String phase2Result) { this.phase2Result = phase2Result; }

    public String getPhase3Ranking() { return phase3Ranking; }
    public void setPhase3Ranking(String phase3Ranking) { this.phase3Ranking = phase3Ranking; }

    public String getPhase4Results() { return phase4Results; }
    public void setPhase4Results(String phase4Results) { this.phase4Results = phase4Results; }

    public String getPhase5Result() { return phase5Result; }
    public void setPhase5Result(String phase5Result) { this.phase5Result = phase5Result; }

    public String getPhaseTimings() { return phaseTimings; }
    public void setPhaseTimings(String phaseTimings) { this.phaseTimings = phaseTimings; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

