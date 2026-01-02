-- 修改 max_risk_score 字段精度，支持更大的分数值
-- 从 DECIMAL(5,2) 改为 DECIMAL(10,2)，最大支持 99999999.99

ALTER TABLE ns_analysis_results 
MODIFY COLUMN max_risk_score DECIMAL(10,2) COMMENT '最高风险分数 (最大支持99999999.99)';

