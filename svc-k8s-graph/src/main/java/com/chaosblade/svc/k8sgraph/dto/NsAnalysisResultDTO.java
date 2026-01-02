package com.chaosblade.svc.k8sgraph.dto;

import com.chaosblade.svc.k8sgraph.entity.NsAnalysisResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 命名空间分析结果DTO
 *
 * 对应新的数据库表结构
 */
public class NsAnalysisResultDTO {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long id;
    private Long taskDbId;
    private String namespace;
    private String resultVersion;

    // 摘要信息
    private Integer servicesCount;
    private Integer rulesTriggered;
    private Integer criticalCount;
    private BigDecimal maxRiskScore;
    private String topRiskService;
    private String riskLevel;

    // 服务排名摘要
    private List<Object> rankedServicesSummary;

    // 存储信息
    private Integer dataSizeBytes;
    private Boolean isCompressed;
    private String storageType;

    // 各阶段结果
    private Object phase1Result;
    private Object phase2Result;
    private Object phase3Ranking;
    private Object phase4Results;
    private Object phase5Result;
    private Object phaseTimings;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public NsAnalysisResultDTO() {}

    public static NsAnalysisResultDTO fromEntity(NsAnalysisResult entity) {
        NsAnalysisResultDTO dto = new NsAnalysisResultDTO();
        dto.setId(entity.getId());
        dto.setTaskDbId(entity.getTaskDbId());
        dto.setNamespace(entity.getNamespace());
        dto.setResultVersion(entity.getResultVersion());
        dto.setServicesCount(entity.getServicesCount());
        dto.setRulesTriggered(entity.getRulesTriggered());
        dto.setCriticalCount(entity.getCriticalCount());
        dto.setMaxRiskScore(entity.getMaxRiskScore());
        dto.setTopRiskService(entity.getTopRiskService());
        dto.setRiskLevel(entity.getRiskLevel() != null ? entity.getRiskLevel().name() : null);
        dto.setDataSizeBytes(entity.getDataSizeBytes());
        dto.setIsCompressed(entity.getIsCompressed());
        dto.setStorageType(entity.getStorageType() != null ? entity.getStorageType().name() : null);
        dto.setCreatedAt(entity.getCreatedAt());

        // Parse JSON fields - 各阶段结果
        try {
            if (entity.getRankedServicesSummary() != null) {
                dto.setRankedServicesSummary(objectMapper.readValue(
                    entity.getRankedServicesSummary(), new TypeReference<List<Object>>() {}));
            }
            if (entity.getPhase1Result() != null) {
                dto.setPhase1Result(objectMapper.readValue(entity.getPhase1Result(), Object.class));
            }
            if (entity.getPhase2Result() != null) {
                dto.setPhase2Result(objectMapper.readValue(entity.getPhase2Result(), Object.class));
            }
            if (entity.getPhase3Ranking() != null) {
                dto.setPhase3Ranking(objectMapper.readValue(entity.getPhase3Ranking(), Object.class));
            }
            if (entity.getPhase4Results() != null) {
                dto.setPhase4Results(objectMapper.readValue(entity.getPhase4Results(), Object.class));
            }
            if (entity.getPhase5Result() != null) {
                dto.setPhase5Result(objectMapper.readValue(entity.getPhase5Result(), Object.class));
            }
            if (entity.getPhaseTimings() != null) {
                dto.setPhaseTimings(objectMapper.readValue(entity.getPhaseTimings(), Object.class));
            }
        } catch (Exception e) {
            // Log error but continue
        }
        return dto;
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

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public List<Object> getRankedServicesSummary() { return rankedServicesSummary; }
    public void setRankedServicesSummary(List<Object> rankedServicesSummary) { this.rankedServicesSummary = rankedServicesSummary; }

    public Integer getDataSizeBytes() { return dataSizeBytes; }
    public void setDataSizeBytes(Integer dataSizeBytes) { this.dataSizeBytes = dataSizeBytes; }

    public Boolean getIsCompressed() { return isCompressed; }
    public void setIsCompressed(Boolean isCompressed) { this.isCompressed = isCompressed; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public Object getPhase1Result() { return phase1Result; }
    public void setPhase1Result(Object phase1Result) { this.phase1Result = phase1Result; }

    public Object getPhase2Result() { return phase2Result; }
    public void setPhase2Result(Object phase2Result) { this.phase2Result = phase2Result; }

    public Object getPhase3Ranking() { return phase3Ranking; }
    public void setPhase3Ranking(Object phase3Ranking) { this.phase3Ranking = phase3Ranking; }

    public Object getPhase4Results() { return phase4Results; }
    public void setPhase4Results(Object phase4Results) { this.phase4Results = phase4Results; }

    public Object getPhase5Result() { return phase5Result; }
    public void setPhase5Result(Object phase5Result) { this.phase5Result = phase5Result; }

    public Object getPhaseTimings() { return phaseTimings; }
    public void setPhaseTimings(Object phaseTimings) { this.phaseTimings = phaseTimings; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

