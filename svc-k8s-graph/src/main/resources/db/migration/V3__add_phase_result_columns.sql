-- 添加各阶段结果存储列到 ns_analysis_results 表
-- Phase2: 拓扑风险分析结果
-- Phase4: Trace分析结果
-- Phase5: 综合分析与故障场景生成结果
-- Phase Timings: 各阶段执行耗时

-- 添加 Phase2 拓扑风险分析结果列
ALTER TABLE ns_analysis_results 
ADD COLUMN phase2_result LONGTEXT COMMENT 'Phase2拓扑风险分析结果 - JSON';

-- 添加 Phase4 Trace分析结果列
ALTER TABLE ns_analysis_results 
ADD COLUMN phase4_results LONGTEXT COMMENT 'Phase4 Trace分析结果(Top N服务) - JSON';

-- 添加 Phase5 综合分析与故障场景生成结果列
ALTER TABLE ns_analysis_results 
ADD COLUMN phase5_result LONGTEXT COMMENT 'Phase5综合分析与故障场景生成结果 - JSON';

-- 添加各阶段执行耗时列
ALTER TABLE ns_analysis_results 
ADD COLUMN phase_timings JSON COMMENT '各阶段执行耗时(ms) - JSON';

-- 修改 phase1_result 和 phase3_ranking 列为 LONGTEXT 以支持更大的数据
ALTER TABLE ns_analysis_results 
MODIFY COLUMN phase1_result LONGTEXT COMMENT 'Phase1规则扫描结果 - JSON';

ALTER TABLE ns_analysis_results 
MODIFY COLUMN phase3_ranking LONGTEXT COMMENT 'Phase3风险排名结果 - JSON';

