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

package com.chaosblade.svc.taskexecutor.service;

import com.chaosblade.common.core.exception.BusinessException;
import com.chaosblade.svc.taskexecutor.dto.ServiceFaultConfig;
import com.chaosblade.svc.taskexecutor.dto.TestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.SimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.MinimalSimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.EnhancedSimplifiedTestCaseDTO;
import com.chaosblade.svc.taskexecutor.dto.EnhancedFaultTargetDTO;
import com.chaosblade.svc.taskexecutor.entity.ApiTopology;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyEdge;
import com.chaosblade.svc.taskexecutor.entity.ApiTopologyNode;
import com.chaosblade.svc.taskexecutor.entity.DetectionTask;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyEdgeRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyNodeRepository;
import com.chaosblade.svc.taskexecutor.repository.ApiTopologyRepository;
import com.chaosblade.svc.taskexecutor.repository.DetectionTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 根据拓扑与故障配置生成测试用例
 */
@Service
public class TestCaseGenerationService {

    private final DetectionTaskRepository detectionTaskRepository;
    private final ApiTopologyRepository apiTopologyRepository;
    private final ApiTopologyNodeRepository nodeRepository;
    private final ApiTopologyEdgeRepository edgeRepository;
    private final FaultConfigQueryService faultConfigQueryService;

    @Value("${app.testcases.highscore.topK:20}")
    private int highScoreTopK;

    @Value("${app.testcases.pathCoverage:0.8}")
    private double pathCoverage;

    public TestCaseGenerationService(DetectionTaskRepository detectionTaskRepository,
                                     ApiTopologyRepository apiTopologyRepository,
                                     ApiTopologyNodeRepository nodeRepository,
                                     ApiTopologyEdgeRepository edgeRepository,
                                     FaultConfigQueryService faultConfigQueryService) {
        this.detectionTaskRepository = detectionTaskRepository;
        this.apiTopologyRepository = apiTopologyRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.faultConfigQueryService = faultConfigQueryService;
    }

    /**
     * 生成完整测试套件
     */
    public List<TestCaseDTO> generateForTask(Long taskId) {
        // 1) 加载任务与拓扑
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + taskId));
        ApiTopology topo = apiTopologyRepository.findBySystemIdAndApiId(task.getSystemId(), task.getApiId())
                .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND",
                        "未找到该系统与API对应的拓扑: system_id=" + task.getSystemId() + ", api_id=" + task.getApiId()));
        List<ApiTopologyNode> nodes = nodeRepository.findByTopologyId(topo.getId());
        List<ApiTopologyEdge> edges = edgeRepository.findByTopologyId(topo.getId());

        if (nodes.isEmpty()) return List.of();

        // 2) 构建原始图（按 from -> to）
        Map<Long, String> id2name = nodes.stream().collect(Collectors.toMap(ApiTopologyNode::getId, ApiTopologyNode::getName));
        Map<Long, Set<Long>> g = new LinkedHashMap<>();
        Map<Long, Set<Long>> rg = new LinkedHashMap<>(); // 反图
        for (ApiTopologyNode n : nodes) { g.put(n.getId(), new LinkedHashSet<>()); rg.put(n.getId(), new LinkedHashSet<>()); }
        for (ApiTopologyEdge e : edges) {
            Long u = e.getFromNodeId(), v = e.getToNodeId();
            if (g.containsKey(u) && g.containsKey(v)) { g.get(u).add(v); rg.get(v).add(u); }
        }
        // 统计原始 out-degree 与叶子
        Map<Long, Integer> outDeg = new LinkedHashMap<>();
        for (Long id : g.keySet()) outDeg.put(id, g.get(id).size());

        // 3) Tarjan SCC 压缩为 DAG
        Map<Long, Integer> comp = tarjanSCC(g);
        int compN = comp.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<Set<Long>> compNodes = new ArrayList<>();
        for (int i = 0; i < compN; i++) compNodes.add(new LinkedHashSet<>());
        for (Map.Entry<Long,Integer> en : comp.entrySet()) compNodes.get(en.getValue()).add(en.getKey());
        List<Set<Integer>> dag = new ArrayList<>();
        List<Set<Integer>> rdag = new ArrayList<>();
        for (int i = 0; i < compN; i++) { dag.add(new LinkedHashSet<>()); rdag.add(new LinkedHashSet<>()); }
        for (Map.Entry<Long, Set<Long>> en : g.entrySet()) {
            int cu = comp.get(en.getKey());
            for (Long v : en.getValue()) {
                int cv = comp.get(v);
                if (cu != cv) { dag.get(cu).add(cv); rdag.get(cv).add(cu); }
            }
        }

        // 4) 计算 DAG 上的路径计数
        List<Integer> topoOrder = topoSort(dag);
        long[] inPaths = new long[compN];
        long[] outPaths = new long[compN];
        // roots: 入度为0
        boolean[] hasIn = new boolean[compN];
        for (int v = 0; v < compN; v++) if (!rdag.get(v).isEmpty()) hasIn[v] = true;
        for (int v = 0; v < compN; v++) if (!hasIn[v]) inPaths[v] = 1;
        for (int u : topoOrder) {
            for (int v : dag.get(u)) inPaths[v] += inPaths[u];
        }
        // leaves: 出度为0
        boolean[] hasOut = new boolean[compN];
        for (int u = 0; u < compN; u++) if (!dag.get(u).isEmpty()) hasOut[u] = true;
        // 反向拓扑累加
        for (int v = 0; v < compN; v++) if (!hasOut[v]) outPaths[v] = 1;
        for (int i = topoOrder.size() - 1; i >= 0; i--) {
            int u = topoOrder.get(i);
            long sum = 0;
            for (int v : dag.get(u)) sum += outPaths[v];
            if (sum > 0) outPaths[u] = sum;
        }
        long totalPaths = 0;
        for (int v = 0; v < compN; v++) if (!hasOut[v]) totalPaths += inPaths[v];
        if (totalPaths == 0) totalPaths = nodes.size(); // 兜底：无边或环导致计数为0时

        // 5) 节点打分：score = in_paths * out_paths（按压缩后的 comp 赋分回原节点）
        Map<Long, Long> score = new LinkedHashMap<>();
        for (Long id : g.keySet()) {
            int c = comp.get(id);
            score.put(id, inPaths[c] * outPaths[c]);
        }

        // 6) 必测节点选择：分支点（原始出度>1）+ 叶子（原始出度=0）+ 高分 Top-K/覆盖阈值
        Set<Long> must = new LinkedHashSet<>();
        for (Long id : g.keySet()) if (outDeg.getOrDefault(id, 0) > 1) must.add(id); // 分支
        for (Long id : g.keySet()) if (outDeg.getOrDefault(id, 0) == 0) must.add(id); // 叶子
        // 高分挑选（按 comp 去重以降低重叠）
        List<Long> orderByScore = score.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey).toList();
        long covered = 0;
        Set<Integer> pickedComps = new HashSet<>();
        for (Long id : orderByScore) {
            if (must.contains(id)) continue;
            int c = comp.get(id);
            if (pickedComps.add(c)) {
                must.add(id);
                covered += inPaths[c] * outPaths[c];
            }
            if (must.size() >= highScoreTopK || (double)covered / (double)totalPaths >= pathCoverage) break;
        }

        // 7) 取副本数（通过 K8s 部署信息，按 pod 数估算）
        Map<String, Integer> svcReplicas = new LinkedHashMap<>();
        var svcFaults = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        for (ServiceFaultConfig sfc : svcFaults) {
            int replicas = (sfc.getNames() != null && !sfc.getNames().isEmpty()) ? sfc.getNames().size() : 1;
            svcReplicas.put(sfc.getServiceName(), replicas);
        }

        // 8) 生成定向、标准化的最小用例集（不做全拓扑笛卡尔）
        List<TestCaseDTO> focused = new ArrayList<>();
        for (Long id : must) {
            String svc = id2name.get(id);
            int replicas = svcReplicas.getOrDefault(svc, 1);
            long sc = score.getOrDefault(id, 0L);
            String reason = outDeg.getOrDefault(id,0) == 0 ? "LEAF" : (outDeg.getOrDefault(id,0) > 1 ? "BRANCH_POINT" : "HIGH_SCORE");
            if (replicas <= 1) {
                focused.add(new TestCaseDTO(svc, reason, sc, replicas,
                        new TestCaseDTO.ChaosSpec("all", null, 60, 60, "FAST_5XX_OR_TIMEOUT")));
            } else {
                focused.add(new TestCaseDTO(svc, reason, sc, replicas,
                        new TestCaseDTO.ChaosSpec(null, 1, 60, 60, "OK_200_OR_AFTER_RETRY")));
                focused.add(new TestCaseDTO(svc, reason, sc, replicas,
                        new TestCaseDTO.ChaosSpec("all", null, 60, 60, "FAST_5XX_OR_TIMEOUT")));
            }
        }
        return focused;
    }

    // Tarjan SCC: 将图 g 分解为强连通分量，返回每个节点所属分量编号 [0..compN-1]
    private Map<Long, Integer> tarjanSCC(Map<Long, Set<Long>> g) {
        Map<Long, Integer> index = new LinkedHashMap<>();
        Map<Long, Integer> low = new LinkedHashMap<>();
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> onStack = new HashSet<>();
        Map<Long, Integer> comp = new LinkedHashMap<>();
        int[] idx = {0};
        int[] compId = {0};

        for (Long v : g.keySet()) {
            if (!index.containsKey(v)) {
                strongConnect(v, g, index, low, stack, onStack, comp, idx, compId);
            }
        }
        return comp;
    }

    private void strongConnect(Long v,
                               Map<Long, Set<Long>> g,
                               Map<Long, Integer> index,
                               Map<Long, Integer> low,
                               Deque<Long> stack,
                               Set<Long> onStack,
                               Map<Long, Integer> comp,
                               int[] idx,
                               int[] compId) {
        index.put(v, idx[0]);
        low.put(v, idx[0]);
        idx[0]++;
        stack.push(v);
        onStack.add(v);

        for (Long w : g.getOrDefault(v, Collections.emptySet())) {
            if (!index.containsKey(w)) {
                strongConnect(w, g, index, low, stack, onStack, comp, idx, compId);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }

        if (Objects.equals(low.get(v), index.get(v))) {
            Long w;
            do {
                w = stack.pop();
                onStack.remove(w);
                comp.put(w, compId[0]);
            } while (!w.equals(v));
            compId[0]++;
        }
    }

    // 对 DAG 做拓扑排序，返回顶点顺序
    private List<Integer> topoSort(List<Set<Integer>> dag) {
        int n = dag.size();
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) {
            for (int v : dag.get(u)) indeg[v]++;
        }
        Deque<Integer> dq = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) dq.addLast(i);
        List<Integer> order = new ArrayList<>();
        while (!dq.isEmpty()) {
            int u = dq.removeFirst();
            order.add(u);
            for (int v : dag.get(u)) {
                if (--indeg[v] == 0) dq.addLast(v);
            }
        }
        return order;
    }

    // ===== Step1 输出：高价值节点选择结果 =====
    public static class Step1Result {
        public static class NodeScore {
            private Long nodeId; private String serviceName; private long score; private long inPaths; private long outPaths; private int outDegree; private String reason; private int compId;
            public NodeScore(Long nodeId, String serviceName, long score, long inPaths, long outPaths, int outDegree, String reason, int compId) {
                this.nodeId=nodeId; this.serviceName=serviceName; this.score=score; this.inPaths=inPaths; this.outPaths=outPaths; this.outDegree=outDegree; this.reason=reason; this.compId=compId;
            }
            public Long getNodeId(){return nodeId;} public String getServiceName(){return serviceName;} public long getScore(){return score;} public long getInPaths(){return inPaths;} public long getOutPaths(){return outPaths;} public int getOutDegree(){return outDegree;} public String getReason(){return reason;} public int getCompId(){return compId;}
        }
        private List<NodeScore> selected; // 必测集合（含理由）
        private List<NodeScore> ranked;   // 全量按分数降序
        private long totalPaths;          // 总根→叶路径数
        private long coveredPaths;        // 被 selected 覆盖的路径数（按 comp 汇总）
        private int topK;                 // 使用的TopK
        private double coverageTarget;    // 目标覆盖阈值
        public Step1Result(List<NodeScore> selected, List<NodeScore> ranked, long totalPaths, long coveredPaths, int topK, double coverageTarget) {
            this.selected=selected; this.ranked=ranked; this.totalPaths=totalPaths; this.coveredPaths=coveredPaths; this.topK=topK; this.coverageTarget=coverageTarget;
        }
        public List<NodeScore> getSelected(){return selected;} public List<NodeScore> getRanked(){return ranked;}
        public long getTotalPaths(){return totalPaths;} public long getCoveredPaths(){return coveredPaths;}
        public int getTopK(){return topK;} public double getCoverageTarget(){return coverageTarget;}
    }

    // 仅执行“第一步”并返回评分与选点结果
    public Step1Result computeStep1(Long taskId) {
        DetectionTask task = detectionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DETECTION_TASK_NOT_FOUND", "检测任务不存在: " + taskId));
        ApiTopology topo = apiTopologyRepository.findBySystemIdAndApiId(task.getSystemId(), task.getApiId())
                .orElseThrow(() -> new BusinessException("TOPOLOGY_NOT_FOUND",
                        "未找到该系统与API对应的拓扑: system_id=" + task.getSystemId() + ", api_id=" + task.getApiId()));
        List<ApiTopologyNode> nodes = nodeRepository.findByTopologyId(topo.getId());
        List<ApiTopologyEdge> edges = edgeRepository.findByTopologyId(topo.getId());
        if (nodes.isEmpty()) return new Step1Result(List.of(), List.of(), 0, 0, highScoreTopK, pathCoverage);

        Map<Long, String> id2name = nodes.stream().collect(Collectors.toMap(ApiTopologyNode::getId, ApiTopologyNode::getName));
        Map<Long, Set<Long>> g = new LinkedHashMap<>();
        Map<Long, Set<Long>> rg = new LinkedHashMap<>();
        for (ApiTopologyNode n : nodes) { g.put(n.getId(), new LinkedHashSet<>()); rg.put(n.getId(), new LinkedHashSet<>()); }
        for (ApiTopologyEdge e : edges) { Long u=e.getFromNodeId(), v=e.getToNodeId(); if (g.containsKey(u)&&g.containsKey(v)) { g.get(u).add(v); rg.get(v).add(u);} }
        Map<Long, Integer> outDeg = new LinkedHashMap<>(); for (Long id : g.keySet()) outDeg.put(id, g.get(id).size());

        Map<Long, Integer> comp = tarjanSCC(g);
        int compN = comp.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<Set<Integer>> dag = new ArrayList<>(); List<Set<Integer>> rdag = new ArrayList<>();
        for (int i=0;i<compN;i++){ dag.add(new LinkedHashSet<>()); rdag.add(new LinkedHashSet<>());}
        for (Map.Entry<Long, Set<Long>> en : g.entrySet()) { int cu = comp.get(en.getKey()); for (Long v : en.getValue()) { int cv = comp.get(v); if (cu!=cv){ dag.get(cu).add(cv); rdag.get(cv).add(cu);} } }

        List<Integer> topoOrder = topoSort(dag);
        long[] inPaths = new long[compN]; long[] outPaths = new long[compN];
        boolean[] hasIn = new boolean[compN]; for (int v=0; v<compN; v++) if(!rdag.get(v).isEmpty()) hasIn[v]=true; for (int v=0; v<compN; v++) if(!hasIn[v]) inPaths[v]=1;
        for (int u : topoOrder) for (int v : dag.get(u)) inPaths[v] += inPaths[u];
        boolean[] hasOut = new boolean[compN]; for (int u=0; u<compN; u++) if(!dag.get(u).isEmpty()) hasOut[u]=true;
        for (int v=0; v<compN; v++) if(!hasOut[v]) outPaths[v]=1; for (int i=topoOrder.size()-1; i>=0; i--){ int u=topoOrder.get(i); long sum=0; for (int v:dag.get(u)) sum+=outPaths[v]; if (sum>0) outPaths[u]=sum; }
        long totalPaths = 0; for (int v=0; v<compN; v++) if(!hasOut[v]) totalPaths += inPaths[v]; if (totalPaths==0) totalPaths = nodes.size();

        // 全量打分
        Map<Long, Long> score = new LinkedHashMap<>(); for (Long id : g.keySet()) { int c=comp.get(id); score.put(id, inPaths[c]*outPaths[c]); }
        List<Step1Result.NodeScore> ranked = score.entrySet().stream()
                .sorted((a,b)->Long.compare(b.getValue(), a.getValue()))
                .map(en -> new Step1Result.NodeScore(en.getKey(), id2name.get(en.getKey()), en.getValue(), inPaths[comp.get(en.getKey())], outPaths[comp.get(en.getKey())], outDeg.getOrDefault(en.getKey(),0), null, comp.get(en.getKey())))
                .toList();

        // 必测集合
        Set<Long> must = new LinkedHashSet<>();
        for (Long id : g.keySet()) if (outDeg.getOrDefault(id,0)>1) must.add(id);
        for (Long id : g.keySet()) if (outDeg.getOrDefault(id,0)==0) must.add(id);
        long covered=0; Set<Integer> picked = new HashSet<>();
        for (Step1Result.NodeScore ns : ranked) {
            if (must.contains(ns.getNodeId())) continue;
            int c = ns.getCompId();
            if (picked.add(c)) { must.add(ns.getNodeId()); covered += ns.getInPaths()*ns.getOutPaths(); }
            if (must.size()>=highScoreTopK || (double)covered/(double)totalPaths >= pathCoverage) break;
        }

        // 为 selected 节点填充 reason
        List<Step1Result.NodeScore> selected = new ArrayList<>();
        Set<Long> mustSet = new HashSet<>(must);
        for (Step1Result.NodeScore ns : ranked) {
            if (!mustSet.contains(ns.getNodeId())) continue;
            String reason = (outDeg.getOrDefault(ns.getNodeId(),0)==0) ? "LEAF" : (outDeg.getOrDefault(ns.getNodeId(),0)>1 ? "BRANCH_POINT" : "HIGH_SCORE");
            selected.add(new Step1Result.NodeScore(ns.getNodeId(), ns.getServiceName(), ns.getScore(), ns.getInPaths(), ns.getOutPaths(), ns.getOutDegree(), reason, ns.getCompId()));
        }
        return new Step1Result(selected, ranked, totalPaths, covered, highScoreTopK, pathCoverage);
    }


    // 简化版用例生成：最多2并发故障，覆盖 0/1/2 故障数，并确保每个被选服务至少被注入一次
    public List<SimplifiedTestCaseDTO> generateSimplifiedForTask(Long taskId) {
        Step1Result step1 = computeStep1(taskId);
        // 选点来源：Step1 的 selected（若为空，则从 ranked 取前若干）
        List<Step1Result.NodeScore> targets = step1.getSelected();
        if (targets == null || targets.isEmpty()) {
            targets = step1.getRanked().stream().limit(Math.max(1, highScoreTopK)).toList();
        }
        // 去重并按分数降序
        targets = targets.stream()
                .sorted((a,b)->Long.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());

        // 估算副本数（与 Step2 一致）
        Map<String, Integer> svcReplicas = new LinkedHashMap<>();
        var svcFaults = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        for (ServiceFaultConfig sfc : svcFaults) {
            int replicas = (sfc.getNames() != null && !sfc.getNames().isEmpty()) ? sfc.getNames().size() : 1;
            svcReplicas.put(sfc.getServiceName(), replicas);
        }

        // 标准化 chaos spec（默认 count=1 & killMode=all 两种，只在需要时选择一种）
        TestCaseDTO.ChaosSpec allDown = new TestCaseDTO.ChaosSpec("all", null, 60, 60, "FAST_5XX_OR_TIMEOUT");
        TestCaseDTO.ChaosSpec oneDown = new TestCaseDTO.ChaosSpec(null, 1, 60, 60, "OK_200_OR_AFTER_RETRY");

        List<SimplifiedTestCaseDTO> result = new ArrayList<>();

        // 0 faults baseline（单条基线）
        result.add(new SimplifiedTestCaseDTO(0, List.of(), "baseline"));

        // 1 fault：确保每个目标服务至少一次被覆盖
        for (Step1Result.NodeScore ns : targets) {
            String svc = ns.getServiceName();
            int replicas = svcReplicas.getOrDefault(svc, 1);
            var chaos = (replicas > 1) ? oneDown : allDown;
            var ft = new SimplifiedTestCaseDTO.FaultTarget(svc, chaos, replicas, ns.getReason()!=null?ns.getReason():"HIGH_SCORE", ns.getScore());
            result.add(new SimplifiedTestCaseDTO(1, List.of(ft), "single fault: "+svc));
        }

        // 2 faults：根据 targets 的降序，生成相邻对（或滑动窗口组合），保证规模可控
        for (int i = 0; i + 1 < targets.size(); i += 2) {
            var a = targets.get(i);
            var b = targets.get(i+1);
            String svA = a.getServiceName(); String svB = b.getServiceName();
            int repA = svcReplicas.getOrDefault(svA, 1); int repB = svcReplicas.getOrDefault(svB, 1);
            var chaosA = (repA > 1) ? oneDown : allDown;
            var chaosB = (repB > 1) ? oneDown : allDown;
            var fA = new SimplifiedTestCaseDTO.FaultTarget(svA, chaosA, repA, a.getReason()!=null?a.getReason():"HIGH_SCORE", a.getScore());
            var fB = new SimplifiedTestCaseDTO.FaultTarget(svB, chaosB, repB, b.getReason()!=null?b.getReason():"HIGH_SCORE", b.getScore());
            result.add(new SimplifiedTestCaseDTO(2, List.of(fA, fB), "dual fault: "+svA+" + "+svB));
        }

        return result;
    }

    // 简化版（最小字段）用例生成：仅输出 service_name 与 fault_config_id，并将单故障用例排在前面
    public List<MinimalSimplifiedTestCaseDTO> generateMinimalSimplifiedForTask(Long taskId) {
        Step1Result step1 = computeStep1(taskId);
        // 目标集合：优先 selected；为空则从 ranked 取 topK
        List<Step1Result.NodeScore> targets = step1.getSelected();
        if (targets == null || targets.isEmpty()) {
            targets = step1.getRanked().stream().limit(Math.max(1, highScoreTopK)).toList();
        }
        // service -> 任取一个 fault_config_id（若无配置则跳过，无法注入）
        Map<String, Long> pickFaultId = new LinkedHashMap<>();
        var svcFaults = faultConfigQueryService.getFaultConfigsByTaskId(taskId);
        for (ServiceFaultConfig sfc : svcFaults) {
            if (sfc.getFaultConfig()!=null && !sfc.getFaultConfig().isEmpty()) {
                // 这里简单选第一条，后续可按策略（如 kill-all / count=1 的脚本识别）选择
                pickFaultId.put(sfc.getServiceName(), sfc.getFaultConfig().get(0).getId());
            }
        }

        List<MinimalSimplifiedTestCaseDTO> out = new ArrayList<>();
        // 0 faults 基线
        out.add(new MinimalSimplifiedTestCaseDTO(List.of()));

        // 1 fault：每个目标服务各1条（排序时会放在前面）
        List<MinimalSimplifiedTestCaseDTO> singles = new ArrayList<>();
        for (Step1Result.NodeScore ns : targets) {
            Long fid = pickFaultId.get(ns.getServiceName());
            if (fid == null) continue; // 无可用故障配置则跳过
            singles.add(new MinimalSimplifiedTestCaseDTO(List.of(new MinimalSimplifiedTestCaseDTO.Fault(ns.getServiceName(), fid))));
        }

        // 2 faults：滑动窗口相邻对，覆盖更充分（i,i+1）
        List<MinimalSimplifiedTestCaseDTO> duals = new ArrayList<>();
        for (int i = 0; i + 1 < targets.size(); i += 1) {
            var a = targets.get(i); var b = targets.get(i+1);
            Long fa = pickFaultId.get(a.getServiceName()); Long fb = pickFaultId.get(b.getServiceName());
            if (fa == null || fb == null) continue;
            duals.add(new MinimalSimplifiedTestCaseDTO(List.of(
                    new MinimalSimplifiedTestCaseDTO.Fault(a.getServiceName(), fa),
                    new MinimalSimplifiedTestCaseDTO.Fault(b.getServiceName(), fb)
            )));
        }

        // 排序：单故障在前，然后 0 故障（基线），最后双故障
        // 具体顺序：1-fault 列表（按 targets 次序） + baseline + 2-fault 列表
        out.addAll(0, singles); // 将单故障插入到最前
        out.addAll(duals);      // 双故障附加在末尾
        return out;
    }

    // 生成增强版（包含 ChaosBlade faultDefinition）的最简用例
    public List<EnhancedSimplifiedTestCaseDTO> generateEnhancedSimpleCases(Long taskId) {
        Step1Result step1 = computeStep1(taskId);
        var targets = step1.getSelected();
        if (targets==null || targets.isEmpty()) {
            targets = step1.getRanked().stream().limit(Math.max(1, highScoreTopK)).toList();
        }
        // service -> ServiceFaultConfig
        Map<String, ServiceFaultConfig> svcInfo = new LinkedHashMap<>();
        for (ServiceFaultConfig sfc : faultConfigQueryService.getFaultConfigsByTaskId(taskId)) {
            svcInfo.put(sfc.getServiceName(), sfc);
        }

        // 构造 faultDefinition 生成器
        java.util.function.Function<ServiceFaultConfig, EnhancedFaultTargetDTO> buildOne = (sfc) -> {
            String ns = sfc.getNamespace();
            String svc = sfc.getServiceName();
            java.util.List<String> containerValues = (sfc.getContainerNames()!=null)? sfc.getContainerNames() : java.util.List.of();
            java.util.List<String> podValues = (sfc.getNames()!=null)? sfc.getNames() : java.util.List.of();
            java.util.Map<String, Object> def = new java.util.LinkedHashMap<>();
            // 仅返回 spec，不包含 kind/apiVersion/metadata（按要求）
            java.util.Map<String, Object> exp = new java.util.LinkedHashMap<>();
            exp.put("scope", "container");
            exp.put("target", "container");
            exp.put("action", "remove");
            java.util.List<java.util.Map<String,Object>> matchers = new java.util.ArrayList<>();
            // 命名空间 + 容器名；无容器名时退回到 pod 名（names）
            if (!podValues.isEmpty()) {
                matchers.add(java.util.Map.of("name","names","value", podValues));
            }
            matchers.add(java.util.Map.of("name","namespace","value", java.util.List.of(ns)));
            if (!containerValues.isEmpty()) {
                matchers.add(java.util.Map.of("name","container-names","value", containerValues));
            }
            matchers.add(java.util.Map.of("name","force","value", java.util.List.of("true")));
            java.util.Map<String, Object> expObj = new java.util.LinkedHashMap<>();
            expObj.putAll(exp);
            expObj.put("matchers", matchers);
            java.util.Map<String, Object> spec = new java.util.LinkedHashMap<>();
            spec.put("experiments", java.util.List.of(expObj));
            def.put("spec", spec);
            return new EnhancedFaultTargetDTO(ns, svc, def);
        };

        List<EnhancedSimplifiedTestCaseDTO> out = new ArrayList<>();
        // baseline
        out.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of()));

        // 1 fault：覆盖所有目标服务
        List<EnhancedSimplifiedTestCaseDTO> singles = new ArrayList<>();
        for (var ns : targets) {
            ServiceFaultConfig sfc = svcInfo.get(ns.getServiceName());
            if (sfc == null) continue; // 无故障配置或无法解析K8s信息
            singles.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of(buildOne.apply(sfc))));
        }

        // 2 faults: 生成所有无序对 C(n,2)，避免重复 [A,B]/[B,A]
        List<EnhancedSimplifiedTestCaseDTO> duals = new ArrayList<>();
        for (int i=0; i<targets.size(); i++) {
            for (int j=i+1; j<targets.size(); j++) {
                ServiceFaultConfig a = svcInfo.get(targets.get(i).getServiceName());
                ServiceFaultConfig b = svcInfo.get(targets.get(j).getServiceName());
                if (a==null || b==null) continue;
                duals.add(new EnhancedSimplifiedTestCaseDTO(java.util.List.of(buildOne.apply(a), buildOne.apply(b))));
            }
        }

        // 排序：单故障 -> baseline -> 双故障
        out.addAll(0, singles);
        out.addAll(duals);
        return out;
    }

    // 计算自底向上的拓扑排序，优先叶子
    private List<String> computeBottomUpOrder(Map<String, List<String>> children) {
        Map<String, Integer> outDeg = new LinkedHashMap<>();
        children.forEach((k, v) -> outDeg.put(k, v.size()));
        Deque<String> queue = new ArrayDeque<>();
        outDeg.forEach((k, d) -> { if (d == 0) queue.add(k); });
        List<String> order = new ArrayList<>();
        Map<String, List<String>> parents = new LinkedHashMap<>();
        children.keySet().forEach(k -> parents.put(k, new ArrayList<>()));
        for (Map.Entry<String, List<String>> e : children.entrySet()) {
            for (String c : e.getValue()) parents.get(c).add(e.getKey());
        }
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String leaf = queue.removeFirst();
            if (visited.contains(leaf)) continue;
            visited.add(leaf);
            order.add(leaf);
            for (String p : parents.getOrDefault(leaf, List.of())) {
                int d = outDeg.get(p) - 1;
                outDeg.put(p, d);
                if (d == 0) queue.addLast(p);
            }
        }
        // 若有环，补齐剩余节点
        for (String k : children.keySet()) if (!visited.contains(k)) order.add(k);
        return order;
    }

    // 多列表笛卡尔积
    private static <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> result = new ArrayList<>();
        if (lists.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }
        backtrack(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T> void backtrack(List<List<T>> lists, int idx, List<T> path, List<List<T>> out) {
        if (idx == lists.size()) { out.add(new ArrayList<>(path)); return; }
        for (T item : lists.get(idx)) {
            path.add(item);
            backtrack(lists, idx + 1, path, out);
            path.remove(path.size() - 1);
        }
    }
}

