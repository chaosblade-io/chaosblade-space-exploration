# ChaosBlade Space Exploration 项目文档

## 文档列表

### 📘 第二节：功能模块设计
**文件**：[第二节-功能模块设计.md](./第二节-功能模块设计.md)

**内容概览**：
- ✅ 2.1 核心功能列表（9个核心功能）
- ✅ 2.2 各模块的详细设计（7个主要模块）
  - 任务资源管理模块 (svc-task-resource)
  - 拓扑发现与可视化模块 (svc-topo)
  - 请求录制与代理模块 (svc-reqrsp-proxy)
  - 故障注入调度模块 (svc-fault-scheduler)
  - 任务执行编排模块 (svc-task-executor)
  - 结果处理模块 (svc-result-processor)
  - 公共模块 (common)
- ✅ 2.3 业务流程图（3个Mermaid流程图）
  - 完整故障检测流程
  - 请求录制与拦截流程
  - 测试用例生成算法流程
- ✅ 2.4 数据库设计
  - ER图（14个核心表）
  - 核心表结构详细说明
  - 数据存储说明（MySQL、Redis、K8s、文件系统）

**页数**：约50页（包含详细的表结构说明）

---

## 项目架构概览

### 服务列表

| 服务名 | 端口 | 职责 | 框架 |
|--------|------|------|------|
| svc-task-resource | 8101 | 任务资源管理 | Spring MVC |
| svc-task-executor | 8102 | 任务执行编排 | Spring MVC |
| svc-fault-scheduler | 8103 | 故障注入调度 | Spring MVC |
| svc-result-processor | 8104 | 结果处理 | Spring MVC |
| svc-reqrsp-proxy | 8105 | 请求录制/拦截/回放 | Spring WebFlux |
| svc-topo | 8106 | 拓扑可视化 | Spring MVC + React |

### 核心技术栈

**后端**：
- Java 21
- Spring Boot 3.x
- Spring Data JPA
- MySQL 9.x
- Redis

**Kubernetes集成**：
- Fabric8 Kubernetes Client
- ChaosBlade Operator
- Envoy Proxy

**分布式追踪**：
- OpenTelemetry
- Jaeger

**前端（svc-topo）**：
- React 18
- XFlow
- Ant Design

**图处理**：
- JGraphT 1.5.x

---

## 核心业务流程

### 1. 完整故障检测流程

```
用户 → 创建检测任务 → 查询服务拓扑 → 保存拓扑 → 启动测试执行
                                                    ↓
                                            生成测试用例
                                                    ↓
                                    ┌───────────────┴───────────────┐
                                    ↓                               ↓
                            启动录制（Envoy）                  注入故障（ChaosBlade）
                                    ↓                               ↓
                            回放请求 ←──────────────────────────────┘
                                    ↓
                            收集结果 → 停止录制 → 删除故障 → 保存测试结果
```

### 2. 测试用例生成算法

```
加载拓扑 → 构建有向图 → 查找SCC → 压缩为DAG → 计算路径和分数
                                                    ↓
                                    选择关键节点（叶子、分支点、高分）
                                                    ↓
                                    生成用例（0/1/2故障）
                                                    ↓
                                    根据副本数选择故障模式
```

---

## 数据库核心表

### 业务核心表
- `systems` - 系统定义
- `apis` - API定义
- `detection_tasks` - 检测任务

### 拓扑相关表
- `api_topologies` - 拓扑元数据
- `api_topology_nodes` - 拓扑节点
- `api_topology_edges` - 拓扑边
- `fault_config` - 故障配置

### 执行相关表
- `task_execution` - 任务执行记录
- `test_cases` - 测试用例
- `test_result` - 测试结果
- `request_patterns` - 请求模式

### 配置相关表
- `http_req_def` - HTTP请求定义
- `fault_types` - 故障类型字典
- `baggage_map` - 追踪上下文

---

## 快速开始

### 查看完整文档

```bash
# 查看功能模块设计文档
cat docs/第二节-功能模块设计.md

# 或使用Markdown阅读器
```

### 理解项目架构

1. **阅读顺序建议**：
   - 先看 2.1 核心功能列表，了解项目能做什么
   - 再看 2.3 业务流程图，理解核心流程
   - 然后看 2.2 各模块详细设计，深入了解每个模块
   - 最后看 2.4 数据库设计，理解数据模型

2. **开发人员**：
   - 重点关注 2.2 各模块的"对外接口"和"依赖关系"
   - 查看业务流程图理解服务间调用关系

3. **测试人员**：
   - 重点关注 2.3 业务流程图
   - 理解测试用例生成算法

4. **运维人员**：
   - 重点关注 2.4 数据库设计
   - 了解各服务的端口和依赖

---

## 文档维护

### 更新记录

| 日期 | 版本 | 更新内容 | 作者 |
|------|------|----------|------|
| 2025-11-03 | 1.0 | 初始版本，完成第二节功能模块设计 | AI Agent |

### 贡献指南

如需更新文档，请：
1. 确保代码变更后及时更新文档
2. 保持文档结构的一致性
3. 使用Mermaid绘制流程图和ER图
4. 提供详细的表结构说明

---

## 相关资源

- **项目README**：[../README.md](../README.md)
- **数据库脚本**：[../scripts/spaceexploration.sql](../scripts/spaceexploration.sql)
- **Docker部署**：[../README-DOCKER.md](../README-DOCKER.md)

---

**文档生成工具**：Augment Agent  
**最后更新**：2025-11-03  
**维护团队**：开发团队

