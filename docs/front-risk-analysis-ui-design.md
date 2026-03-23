# 风险分析模块前端页面设计说明

## 1. 概述

本文档描述风险分析模块的前端页面设计，包括任务列表、新建任务、任务详情等功能模块的页面布局、组件设计、交互流程和API接口对接方案。

### 1.1 功能模块结构

```
风险分析模块
├── 任务列表页面          /risk-analysis/tasks
├── 新建任务弹窗          (模态框)
├── 任务详情页面          /risk-analysis/tasks/:taskId
│   ├── 基本信息区域
│   ├── 执行进度区域
│   ├── 执行日志区域
│   └── 分析结果区域
│       ├── 阶段1-4结果
│       └── 阶段5结果(风险+演练场景)
└── 演练场景集成
    ├── 场景代码查看器
    └── ChaosBlade跳转
```

### 1.2 API基础信息

**Base URL**: `/api/ns-analysis`

---

## 2. 任务列表页面

### 2.1 页面布局

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 页面标题栏                                                              │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │  🔍 风险分析任务                                    [+ 新建分析任务] │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 过滤区域                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 命名空间: [________▼]  状态: [________▼]  [查询] [重置]            │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 任务列表表格                                                            │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 任务ID │ 命名空间 │ 状态 │ 进度 │ 创建时间 │ 耗时 │ 创建者 │ 操作  │ │
│ ├────────┼──────────┼──────┼──────┼──────────┼──────┼────────┼───────┤ │
│ │ ns-xxx │ default  │ ✓完成│ 100% │ 2026-01-03│ 35s │ admin │ ⋯菜单 │ │
│ │ ns-yyy │ prod     │ ◐运行│  45% │ 2026-01-03│ --  │ admin │ ⋯菜单 │ │
│ │ ns-zzz │ staging  │ ✗失败│  60% │ 2026-01-02│ 20s │ admin │ ⋯菜单 │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 分页区域                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 共 50 条  每页 [20 ▼] 条    < 1 2 3 ... 5 >                         │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 表格字段定义

| 列名 | 字段 | 类型 | 宽度 | 说明 |
|------|------|------|------|------|
| 任务ID | taskId | string | 120px | 格式: ns-xxxxxxxx, 可点击跳转详情 |
| 命名空间 | namespace | string | 120px | K8s命名空间名称 |
| 状态 | status | enum | 100px | 带颜色标签显示 |
| 进度 | progressPercent | number | 100px | 进度条形式 |
| 创建时间 | createdAt | datetime | 160px | yyyy-MM-dd HH:mm:ss |
| 耗时 | totalTimeMs | number | 80px | 格式化显示: 35s / 2m 15s |
| 创建者 | createdBy | string | 100px | - |
| 操作 | - | - | 100px | 操作菜单按钮 |

### 2.3 状态映射

| 状态值 | 显示文案 | 标签颜色 | 图标 |
|--------|----------|----------|------|
| PENDING | 等待中 | 灰色 | ⏳ |
| RUNNING | 运行中 | 蓝色(闪烁) | ◐ |
| COMPLETED | 已完成 | 绿色 | ✓ |
| FAILED | 失败 | 红色 | ✗ |
| CANCELLED | 已取消 | 橙色 | ⊘ |

### 2.4 操作按钮

| 操作 | 显示条件 | 图标 | 说明 |
|------|----------|------|------|
| 查看详情 | 所有状态 | 👁️ | 跳转到详情页 |
| 停止任务 | PENDING/RUNNING | ⏹️ | 显示确认对话框 |
| 删除任务 | 非RUNNING | 🗑️ | 显示确认对话框 |

### 2.5 过滤组件

**命名空间选择器**:
- 类型: 下拉选择 + 支持搜索输入
- 数据源: 从后端获取可用命名空间列表 或 用户手动输入
- 默认值: 全部

**状态选择器**:
- 类型: 下拉选择
- 选项: 全部 | 等待中 | 运行中 | 已完成 | 失败 | 已取消
- 默认值: 全部

### 2.6 API对接

**分页查询任务列表**:
```
GET /api/ns-analysis/tasks
  ?namespace=xxx      // 可选
  &status=RUNNING     // 可选
  &page=1             // 页码, 默认1
  &size=20            // 每页条数, 默认20
```

**响应数据**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "taskId": "ns-a1b2c3d4",
        "namespace": "default",
        "status": "COMPLETED",
        "progressPercent": 100,
        "createdAt": "2026-01-03 10:00:00",
        "totalTimeMs": 35230,
        "createdBy": "admin"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 20,
    "totalPages": 3
  }
}
```

### 2.7 自动刷新机制

- 当列表中存在 `PENDING` 或 `RUNNING` 状态的任务时，启动轮询
- 轮询间隔: 5秒
- 用户切换页面或标签页时暂停轮询
- 无运行中任务时停止轮询

---

## 3. 新建分析任务弹窗

### 3.1 弹窗布局

```
┌───────────────────────────────────────────────────────────────┐
│  新建风险分析任务                                        [×] │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│   命名空间 *                                                  │
│   ┌─────────────────────────────────────────────────────┐    │
│   │ [请选择或输入命名空间                            ▼] │    │
│   └─────────────────────────────────────────────────────┘    │
│                                                               │
│   Top N 风险数量                                             │
│   ┌─────────────────────────────────────────────────────┐    │
│   │ [5                                               ▼] │    │
│   └─────────────────────────────────────────────────────┘    │
│   提示: 分析结果中返回风险排名前N的服务 (范围: 1-20)         │
│                                                               │
│   ⚠️ 该命名空间已有正在执行的分析任务 (ns-xxx)              │
│      [查看进行中的任务]                                       │
│                                                               │
├───────────────────────────────────────────────────────────────┤
│                           [取消]  [创建并开始分析]            │
└───────────────────────────────────────────────────────────────┘
```

### 3.2 表单字段

| 字段 | 类型 | 必填 | 默认值 | 验证规则 |
|------|------|------|--------|----------|
| namespace | 下拉选择/输入 | ✅ | - | 非空，长度1-128 |
| topN | 数字选择器 | ❌ | 5 | 范围1-20 |
| config.enableLlmAnalysis | 开关 | ❌ | true | - |
| config.includeMetrics | 开关 | ❌ | true | - |

### 3.3 冲突处理逻辑

1. **命名空间选择后**: 调用接口检查是否存在运行中任务
2. **检查API**: `GET /api/ns-analysis/tasks?namespace=xxx&status=RUNNING`
3. **存在冲突时**:
   - 显示警告提示
   - 提供跳转链接到进行中任务
   - 禁用"创建"按钮
4. **无冲突时**: 正常允许创建

### 3.4 创建流程

```
用户点击创建
    ↓
表单验证
    ↓ (通过)
POST /api/ns-analysis/tasks
    ↓
显示Loading
    ↓
响应成功
    ↓
关闭弹窗
    ↓
刷新列表 + 显示成功提示
    ↓
(可选) 自动跳转到详情页
```

### 3.5 API对接

**创建任务**:
```
POST /api/ns-analysis/tasks
Content-Type: application/json

{
  "namespace": "default",
  "topN": 5,
  "config": {
    "enableLlmAnalysis": true,
    "includeMetrics": true
  },
  "createdBy": "当前用户名"
}
```

**响应**:
```json
{
  "success": true,
  "message": "分析任务创建成功，正在执行",
  "data": {
    "taskId": "ns-a1b2c3d4",
    "namespace": "default",
    "status": "PENDING",
    "progressPercent": 0
  }
}
```

---

## 4. 任务详情页面

### 4.1 整体布局

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 面包屑导航                                                              │
│ 风险分析 > 任务列表 > 任务详情 ns-a1b2c3d4                              │
├─────────────────────────────────────────────────────────────────────────┤
│ 顶部操作栏                                                              │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 📋 ns-a1b2c3d4 (default)     状态: [✓ 已完成]                       │ │
│ │                                      [停止任务] [删除任务] [返回列表]│ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 内容区域 (标签页)                                                       │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ [基本信息] [执行进度] [执行日志] [分析结果]                         │ │
│ ├─────────────────────────────────────────────────────────────────────┤ │
│ │                                                                     │ │
│ │                     (标签页内容区域)                                │ │
│ │                                                                     │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 基本信息标签页

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 任务信息                                                                │
│ ┌───────────────────────────────┬───────────────────────────────────┐   │
│ │ 任务ID      │ ns-a1b2c3d4    │ 命名空间    │ default             │   │
│ │ 触发方式    │ API调用        │ Top N      │ 5                    │   │
│ │ 创建者      │ admin          │ 创建时间   │ 2026-01-03 10:00:00 │   │
│ │ 开始时间    │ 2026-01-03 10:00:05 │ 完成时间 │ 2026-01-03 10:00:40│   │
│ │ 总耗时      │ 35秒           │ 当前阶段   │ 6/6                  │   │
│ └───────────────────────────────┴───────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│ 分析概览                                                                │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                        │
│ │   12    │ │    5    │ │    2    │ │  85.5   │                        │
│ │ 服务数  │ │ 规则触发 │ │ 严重风险│ │ 最高风险│                        │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘                        │
│                                                                         │
│ 最高风险服务: payment-service    风险等级: [🔴 CRITICAL]               │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.3 执行进度标签页

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 整体进度                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ ████████████████████████████████████████████████████ 100%          │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 阶段详情                                                                │
│                                                                         │
│   ✓ 阶段1: 规则扫描          ────────────────────   8.2s              │
│   ✓ 阶段2: 拓扑风险分析      ────────────────────   5.1s              │
│   ✓ 阶段3: 风险排名计算      ────────────────────   3.0s              │
│   ✓ 阶段4: Trace深度分析     ────────────────────  12.5s              │
│   ✓ 阶段5: 综合分析与场景生成 ────────────────────   4.8s              │
│   ✓ 阶段6: 配置生成          ────────────────────   1.4s              │
│                                                                         │
│   总计: 35.0s                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**阶段定义**:

| 阶段 | 名称 | 描述 |
|------|------|------|
| 1 | 规则扫描 | K8s资源配置规则检查 |
| 2 | 拓扑风险分析 | 基于服务拓扑的LLM分析 |
| 3 | 风险排名计算 | RiskRank算法综合排名 |
| 4 | Trace深度分析 | 调用链路Trace分析 |
| 5 | 综合分析与场景生成 | 三层风险整合+故障场景生成 |
| 6 | 配置生成 | ChaosBlade实验配置生成 |

### 4.4 执行日志标签页

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 过滤选项                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 级别: [全部 ▼]  阶段: [全部 ▼]                    [刷新]           │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 日志列表                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 时间                 │ 级别   │ 阶段 │ 消息                        │ │
│ ├──────────────────────┼────────┼──────┼─────────────────────────────┤ │
│ │ 10:00:05.123        │ ℹ️ INFO │  1   │ 任务开始执行               │ │
│ │ 10:00:05.456        │ ℹ️ INFO │  1   │ 开始执行阶段1: 规则扫描    │ │
│ │ 10:00:13.789        │ ℹ️ INFO │  2   │ 开始执行阶段2: 拓扑分析    │ │
│ │ 10:00:18.234        │ ⚠️ WARN │  2   │ 服务xxx缺少健康检查配置    │ │
│ │ 10:00:40.567        │ ℹ️ INFO │  6   │ 任务执行完成, 耗时35000ms  │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 分页                                                                    │
│ │ 共 25 条  每页 [50 ▼] 条    < 1 >                                   │ │
└─────────────────────────────────────────────────────────────────────────┘
```

**日志级别样式**:

| 级别 | 值 | 图标 | 颜色 |
|------|---|------|------|
| DEBUG | 0 | 🔧 | 灰色 |
| INFO | 1 | ℹ️ | 蓝色 |
| WARN | 2 | ⚠️ | 橙色 |
| ERROR | 3 | ❌ | 红色 |

**API对接**:
```
GET /api/ns-analysis/tasks/{taskId}/logs
  ?minLevel=1       // 可选, 0-3
  &phase=2          // 可选, 1-6

GET /api/ns-analysis/tasks/{taskId}/logs/page
  ?page=1
  &size=50
```

### 4.5 分析结果标签页

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 阶段结果选择                                                            │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ [阶段1:规则] [阶段2:拓扑] [阶段3:排名] [阶段4:Trace] [阶段5:综合]   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  (各阶段结果内容区域 - 详见4.5.x小节)                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 4.5.1 阶段1结果 - 规则扫描

展示各服务的规则扫描结果:
- 服务列表(按风险分数排序)
- 每个服务触发的规则详情
- 风险分类统计

#### 4.5.2 阶段2结果 - 拓扑分析

展示拓扑风险分析结果:
- 服务拓扑关键节点识别
- LLM分析的风险点

#### 4.5.3 阶段3结果 - 风险排名

展示综合风险排名:
- Top N服务排名列表
- 各服务风险分数分布图

#### 4.5.4 阶段4结果 - Trace分析

展示Trace深度分析结果:
- API调用摘要
- 关键调用链路详情

#### 4.5.5 阶段5结果 - 综合分析与故障场景 (重点)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 风险服务概览                                                            │
│ ┌──────────────┬──────────────┬──────────────┬──────────────┐           │
│ │   严重(2)    │   高危(3)    │   中等(5)    │   低危(2)    │           │
│ │    🔴        │     🟠       │     🟡       │     🟢       │           │
│ └──────────────┴──────────────┴──────────────┴──────────────┘           │
├─────────────────────────────────────────────────────────────────────────┤
│ 服务风险列表                                                            │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 1. payment-service   风险分数: 95.5   [🔴 CRITICAL]                 │ │
│ │    ├─ 主要风险: 单点故障(Pod副本数=1)                                │ │
│ │    ├─ 推荐场景: 3个                                                 │ │
│ │    └─ [展开详情]                                                    │ │
│ ├─────────────────────────────────────────────────────────────────────┤ │
│ │ 2. order-service     风险分数: 78.2   [🟠 HIGH]                     │ │
│ │    ├─ 主要风险: 关键路径节点，无熔断保护                             │ │
│ │    ├─ 推荐场景: 2个                                                 │ │
│ │    └─ [展开详情]                                                    │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

**展开详情后**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ▼ 1. payment-service   风险分数: 95.5   [🔴 CRITICAL]                  │
├─────────────────────────────────────────────────────────────────────────┤
│ 风险详情                                                                │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 风险1: 单点故障风险 (根因)                     分数: 45             │ │
│ │   来源层级: 资源层                                                  │ │
│ │   描述: 该服务仅有1个Pod副本，任何故障将导致服务完全不可用          │ │
│ │                                                                     │ │
│ │ 风险2: 无熔断保护                               分数: 30            │ │
│ │   来源层级: 链路层                                                  │ │
│ │   描述: 下游服务故障时可能引发级联失败                              │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ 推荐演练场景                                                            │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ 场景1: Pod杀死测试                                                  │ │
│ │   类型: POD_KILL    强度: ⭐⭐⭐     时长: 60秒                     │ │
│ │   目标: 验证服务在Pod突然终止时的恢复能力                           │ │
│ │   ┌────────────────────────────────────────────────────────────┐   │ │
│ │   │ [查看场景代码]  [跳转到ChaosBlade演练]                     │   │ │
│ │   └────────────────────────────────────────────────────────────┘   │ │
│ ├─────────────────────────────────────────────────────────────────────┤ │
│ │ 场景2: CPU满载测试                                                  │ │
│ │   类型: CPU_STRESS  强度: ⭐⭐⭐⭐   时长: 120秒                    │ │
│ │   目标: 验证服务在CPU资源紧张时的表现                               │ │
│ │   ┌────────────────────────────────────────────────────────────┐   │ │
│ │   │ [查看场景代码]  [跳转到ChaosBlade演练]                     │   │ │
│ │   └────────────────────────────────────────────────────────────┘   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

**API对接**:
```
GET /api/ns-analysis/results/{taskId}
```

响应中的 `phase5Result` 字段包含完整的综合分析结果。

---

## 5. 演练场景集成

### 5.1 场景代码查看器

点击"查看场景代码"按钮后弹出代码查看器弹窗:

```
┌───────────────────────────────────────────────────────────────────────┐
│  演练场景配置 - Pod杀死测试                                      [×] │
├───────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │ {                                                               │  │
│  │   "name": "payment-service-pod-kill-test",                      │  │
│  │   "description": "验证payment-service Pod终止后的恢复能力",     │  │
│  │   "namespace": "default",                                        │  │
│  │   "definition": {                                                │  │
│  │     "runMode": "SEQUENCE",                                       │  │
│  │     "duration": 60,                                              │  │
│  │     "flowGroups": [                                              │  │
│  │       {                                                          │  │
│  │         "appName": "payment-service",                            │  │
│  │         "groupName": "pod-kill-group",                           │  │
│  │         "flows": [...]                                           │  │
│  │       }                                                          │  │
│  │     ]                                                            │  │
│  │   }                                                              │  │
│  │ }                                                                │  │
│  └─────────────────────────────────────────────────────────────────┘  │
├───────────────────────────────────────────────────────────────────────┤
│                     [复制配置]  [下载JSON]  [关闭]                    │
└───────────────────────────────────────────────────────────────────────┘
```

**功能**:
- 语法高亮显示JSON配置
- 复制到剪贴板
- 下载为JSON文件

### 5.2 ChaosBlade集成跳转

点击"跳转到ChaosBlade演练"按钮:

**跳转逻辑**:
```javascript
function navigateToChaosBlade(scenario) {
  // ChaosBlade Box 演练创建页面URL
  const baseUrl = '/chaosblade/experiment/create';

  // 参数编码
  const params = new URLSearchParams({
    // 预填充实验名称
    name: scenario.name,
    // 预填充描述
    description: scenario.objective,
    // 预填充应用信息
    appName: scenario.targetServiceName,
    namespace: scenario.namespace,
    // 预填充故障类型
    faultCode: scenario.code,
    // 预填充故障参数(JSON编码)
    faultParams: JSON.stringify(scenario.faultParams),
    // 来源标记
    source: 'risk-analysis',
    taskId: currentTaskId
  });

  // 新标签页打开
  window.open(`${baseUrl}?${params.toString()}`, '_blank');
}
```

**参数映射表**:

| 风险分析字段 | ChaosBlade字段 | 说明 |
|-------------|---------------|------|
| scenario.name | name | 实验名称 |
| scenario.objective | description | 实验描述 |
| scenario.code | faultCode | 故障类型代码 |
| scenario.faultParams | faultParams | 故障参数 |
| scenario.durationSeconds | duration | 执行时长 |
| scenario.targetServiceName | appName | 目标应用 |

### 5.3 场景类型映射

| 场景类型 | 故障代码 | 显示名称 | 图标 |
|---------|---------|---------|------|
| POD_KILL | chaos.pod.kill | Pod杀死 | 💀 |
| POD_FAILURE | chaos.pod.fail | Pod故障 | ⚠️ |
| CPU_STRESS | chaos.container-cpu.fullload | CPU满载 | 🔥 |
| MEMORY_STRESS | chaos.container-mem.oom | 内存压力 | 💾 |
| NETWORK_DELAY | chaos.network.delay | 网络延迟 | 🌐 |
| NETWORK_LOSS | chaos.network.loss | 网络丢包 | 📡 |

---

## 6. 任务管理操作

### 6.1 停止任务

**触发**: 点击"停止任务"按钮

**确认对话框**:
```
┌─────────────────────────────────────────────┐
│  ⚠️ 确认停止任务?                           │
├─────────────────────────────────────────────┤
│                                             │
│  任务 ns-a1b2c3d4 正在执行中。              │
│  停止后任务将标记为"已取消"状态，           │
│  已完成的分析阶段数据将被保留。             │
│                                             │
├─────────────────────────────────────────────┤
│              [取消]  [确认停止]             │
└─────────────────────────────────────────────┘
```

**API调用**:
```
POST /api/ns-analysis/tasks/{taskId}/cancel
```

### 6.2 删除任务

**触发**: 点击"删除任务"按钮

**确认对话框**:
```
┌─────────────────────────────────────────────┐
│  🗑️ 确认删除任务?                           │
├─────────────────────────────────────────────┤
│                                             │
│  任务 ns-a1b2c3d4 及其所有关联数据          │
│  将被永久删除，包括:                        │
│  - 分析结果                                 │
│  - 执行日志                                 │
│  - 风险评估数据                             │
│                                             │
│  此操作不可撤销。                           │
│                                             │
├─────────────────────────────────────────────┤
│              [取消]  [确认删除]             │
└─────────────────────────────────────────────┘
```

**API调用**:
```
DELETE /api/ns-analysis/tasks/{taskId}
```

### 6.3 状态实时更新

**方案**: 轮询机制

```javascript
// 详情页轮询配置
const pollingConfig = {
  // 任务状态轮询
  taskStatus: {
    interval: 3000,  // 3秒
    condition: (task) => ['PENDING', 'RUNNING'].includes(task.status)
  },

  // 进度轮询
  progress: {
    interval: 2000,  // 2秒
    condition: (task) => task.status === 'RUNNING'
  },

  // 日志轮询(运行中时)
  logs: {
    interval: 5000,  // 5秒
    condition: (task) => task.status === 'RUNNING'
  }
};
```

**进度API**:
```
GET /api/ns-analysis/tasks/{taskId}/progress
```

响应:
```json
{
  "success": true,
  "data": {
    "phase": 3,
    "percent": 65,
    "message": "正在进行风险排名计算...",
    "status": "RUNNING"
  }
}
```

---

## 7. 响应式设计

### 7.1 断点定义

| 断点 | 宽度 | 布局调整 |
|------|------|----------|
| xs | <576px | 移动端，单列布局 |
| sm | ≥576px | 平板竖屏 |
| md | ≥768px | 平板横屏 |
| lg | ≥992px | 桌面端 |
| xl | ≥1200px | 大屏桌面 |

### 7.2 关键调整

**任务列表表格(xs-sm)**:
- 隐藏: 创建者、触发方式列
- 任务ID缩短显示
- 操作按钮收缩为图标

**详情页(xs-sm)**:
- 标签页改为下拉选择
- 信息卡片单列排列
- 场景操作按钮垂直排列

---

## 8. 错误处理

### 8.1 错误提示规范

| 错误场景 | 提示方式 | 提示内容示例 |
|---------|---------|-------------|
| 网络错误 | Toast | 网络连接失败，请检查网络后重试 |
| 404 | Toast + 跳转 | 任务不存在或已被删除 |
| 400 | 表单内提示 | 命名空间格式不正确 |
| 500 | Toast | 服务器内部错误，请稍后重试 |
| 任务创建冲突 | 内联警告 | 该命名空间已有正在执行的任务 |

### 8.2 加载状态

| 操作 | 加载组件 | 加载文案 |
|------|---------|---------|
| 列表加载 | 骨架屏 | - |
| 详情加载 | 整页Loading | 加载任务详情... |
| 创建任务 | 按钮Loading | 创建中... |
| 停止任务 | 按钮Loading | 停止中... |
| 删除任务 | 按钮Loading | 删除中... |

### 8.3 空状态

```
┌─────────────────────────────────────────────┐
│                                             │
│              📋                             │
│                                             │
│         暂无分析任务                        │
│                                             │
│    点击"新建分析任务"开始                  │
│    对K8s命名空间进行风险分析               │
│                                             │
│        [+ 新建分析任务]                     │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 9. 风险级别样式规范

| 级别 | 英文 | 分数范围 | 颜色(HEX) | 背景色 | 图标 |
|------|------|---------|-----------|--------|------|
| 严重 | CRITICAL | 90-100 | #FF4D4F | #FFF2F0 | 🔴 |
| 高危 | HIGH | 70-89 | #FA8C16 | #FFF7E6 | 🟠 |
| 中等 | MEDIUM | 50-69 | #FADB14 | #FFFBE6 | 🟡 |
| 低危 | LOW | 30-49 | #52C41A | #F6FFED | 🟢 |
| 提示 | INFO | 0-29 | #1890FF | #E6F7FF | 🔵 |

---

## 10. 通用组件规范

### 10.1 任务状态徽章

```vue
<template>
  <a-tag :color="statusConfig[status].color">
    <span>{{ statusConfig[status].icon }}</span>
    {{ statusConfig[status].text }}
  </a-tag>
</template>

<script>
const statusConfig = {
  PENDING: { icon: '⏳', text: '等待中', color: 'default' },
  RUNNING: { icon: '🔄', text: '执行中', color: 'processing' },
  COMPLETED: { icon: '✅', text: '已完成', color: 'success' },
  FAILED: { icon: '❌', text: '失败', color: 'error' },
  CANCELLED: { icon: '🚫', text: '已取消', color: 'warning' }
};
</script>
```

### 10.2 风险分数徽章

```vue
<template>
  <a-tag :color="getRiskColor(score)">
    {{ getRiskLevel(score) }} ({{ score }})
  </a-tag>
</template>

<script>
function getRiskLevel(score) {
  if (score >= 90) return 'CRITICAL';
  if (score >= 70) return 'HIGH';
  if (score >= 50) return 'MEDIUM';
  if (score >= 30) return 'LOW';
  return 'INFO';
}

function getRiskColor(score) {
  if (score >= 90) return '#FF4D4F';
  if (score >= 70) return '#FA8C16';
  if (score >= 50) return '#FADB14';
  if (score >= 30) return '#52C41A';
  return '#1890FF';
}
</script>
```

### 10.3 执行进度条

```vue
<template>
  <div class="progress-wrapper">
    <a-progress
      :percent="progress.percent"
      :status="progressStatus"
      :stroke-color="strokeColor"
    />
    <div class="progress-info">
      <span>阶段 {{ progress.phase }}/6: {{ phaseNames[progress.phase] }}</span>
      <span>{{ progress.message }}</span>
    </div>
  </div>
</template>

<script>
const phaseNames = {
  1: '规则扫描',
  2: '拓扑分析',
  3: '风险排名',
  4: 'Trace分析',
  5: '综合分析',
  6: '配置生成'
};
</script>
```

### 10.4 场景卡片

```vue
<template>
  <a-card class="scenario-card" size="small">
    <template #title>
      <span>{{ getScenarioIcon(scenario.code) }} {{ scenario.name }}</span>
    </template>
    <p><strong>类型:</strong> {{ scenario.code }}</p>
    <p><strong>强度:</strong> {{ renderIntensity(scenario.intensity) }}</p>
    <p><strong>时长:</strong> {{ scenario.durationSeconds }}秒</p>
    <p><strong>目标:</strong> {{ scenario.objective }}</p>
    <div class="scenario-actions">
      <a-button size="small" @click="viewCode">查看配置</a-button>
      <a-button size="small" type="primary" @click="navigateToChaos">
        跳转演练
      </a-button>
    </div>
  </a-card>
</template>

<script>
function getScenarioIcon(code) {
  const icons = {
    'POD_KILL': '💀',
    'POD_FAILURE': '⚠️',
    'CPU_STRESS': '🔥',
    'MEMORY_STRESS': '💾',
    'NETWORK_DELAY': '🌐',
    'NETWORK_LOSS': '📡'
  };
  return icons[code] || '🔧';
}

function renderIntensity(level) {
  return '⭐'.repeat(level);
}
</script>
```

---

## 11. 状态管理

### 11.1 Vuex/Pinia Store 结构

```javascript
// stores/riskAnalysis.js
export const useRiskAnalysisStore = defineStore('riskAnalysis', {
  state: () => ({
    // 任务列表
    taskList: [],
    taskListLoading: false,
    taskListTotal: 0,

    // 当前任务详情
    currentTask: null,
    currentTaskLoading: false,

    // 分析结果
    analysisResult: null,
    analysisResultLoading: false,

    // 执行日志
    executionLogs: [],
    logsTotal: 0,

    // 轮询控制
    pollingTimers: {
      taskStatus: null,
      progress: null,
      logs: null
    },

    // 过滤条件
    filters: {
      namespace: null,
      status: null,
      page: 1,
      size: 10
    }
  }),

  actions: {
    // 获取任务列表
    async fetchTaskList() {
      this.taskListLoading = true;
      try {
        const response = await api.get('/ns-analysis/tasks', {
          params: this.filters
        });
        this.taskList = response.data.list;
        this.taskListTotal = response.data.total;
      } finally {
        this.taskListLoading = false;
      }
    },

    // 获取任务详情
    async fetchTaskDetail(taskId) {
      this.currentTaskLoading = true;
      try {
        const response = await api.get(`/ns-analysis/tasks/${taskId}`);
        this.currentTask = response.data;
      } finally {
        this.currentTaskLoading = false;
      }
    },

    // 创建任务
    async createTask(namespace) {
      const response = await api.post('/ns-analysis/tasks', { namespace });
      return response.data;
    },

    // 取消任务
    async cancelTask(taskId) {
      await api.post(`/ns-analysis/tasks/${taskId}/cancel`);
      await this.fetchTaskDetail(taskId);
    },

    // 删除任务
    async deleteTask(taskId) {
      await api.delete(`/ns-analysis/tasks/${taskId}`);
      await this.fetchTaskList();
    },

    // 获取分析结果
    async fetchAnalysisResult(taskId) {
      this.analysisResultLoading = true;
      try {
        const response = await api.get(`/ns-analysis/results/${taskId}`);
        this.analysisResult = response.data;
      } finally {
        this.analysisResultLoading = false;
      }
    },

    // 启动轮询
    startPolling(taskId) {
      // 任务状态轮询
      this.pollingTimers.taskStatus = setInterval(() => {
        if (['PENDING', 'RUNNING'].includes(this.currentTask?.status)) {
          this.fetchTaskDetail(taskId);
        } else {
          this.stopPolling('taskStatus');
        }
      }, 3000);

      // 进度轮询
      this.pollingTimers.progress = setInterval(async () => {
        if (this.currentTask?.status === 'RUNNING') {
          const response = await api.get(`/ns-analysis/tasks/${taskId}/progress`);
          this.currentTask.progress = response.data;
        } else {
          this.stopPolling('progress');
        }
      }, 2000);
    },

    // 停止轮询
    stopPolling(type = 'all') {
      if (type === 'all') {
        Object.keys(this.pollingTimers).forEach(key => {
          if (this.pollingTimers[key]) {
            clearInterval(this.pollingTimers[key]);
            this.pollingTimers[key] = null;
          }
        });
      } else if (this.pollingTimers[type]) {
        clearInterval(this.pollingTimers[type]);
        this.pollingTimers[type] = null;
      }
    }
  }
});
```

---

## 12. 路由配置

```javascript
// router/riskAnalysis.js
export const riskAnalysisRoutes = [
  {
    path: '/risk-analysis',
    name: 'RiskAnalysis',
    redirect: '/risk-analysis/tasks',
    meta: {
      title: '风险分析',
      icon: 'search'
    },
    children: [
      {
        path: 'tasks',
        name: 'RiskAnalysisTasks',
        component: () => import('@/views/riskAnalysis/TaskList.vue'),
        meta: {
          title: '分析任务列表',
          keepAlive: true
        }
      },
      {
        path: 'tasks/:taskId',
        name: 'RiskAnalysisTaskDetail',
        component: () => import('@/views/riskAnalysis/TaskDetail.vue'),
        meta: {
          title: '任务详情',
          hidden: true  // 不在菜单中显示
        },
        props: true
      }
    ]
  }
];
```

---

## 13. 国际化支持

```javascript
// locales/zh-CN/riskAnalysis.js
export default {
  riskAnalysis: {
    title: '风险分析',
    taskList: '分析任务列表',
    createTask: '新建分析任务',
    taskDetail: '任务详情',

    // 状态
    status: {
      PENDING: '等待中',
      RUNNING: '执行中',
      COMPLETED: '已完成',
      FAILED: '失败',
      CANCELLED: '已取消'
    },

    // 风险级别
    riskLevel: {
      CRITICAL: '严重',
      HIGH: '高危',
      MEDIUM: '中等',
      LOW: '低危',
      INFO: '提示'
    },

    // 阶段名称
    phase: {
      1: '规则扫描',
      2: '拓扑分析',
      3: '风险排名',
      4: 'Trace分析',
      5: '综合分析',
      6: '配置生成'
    },

    // 场景类型
    scenarioType: {
      POD_KILL: 'Pod杀死',
      POD_FAILURE: 'Pod故障',
      CPU_STRESS: 'CPU满载',
      MEMORY_STRESS: '内存压力',
      NETWORK_DELAY: '网络延迟',
      NETWORK_LOSS: '网络丢包'
    },

    // 操作
    action: {
      view: '查看详情',
      stop: '停止任务',
      delete: '删除任务',
      refresh: '刷新',
      viewCode: '查看配置',
      jumpToChaos: '跳转演练'
    },

    // 提示
    message: {
      createSuccess: '任务创建成功',
      cancelSuccess: '任务已取消',
      deleteSuccess: '任务已删除',
      confirmCancel: '确定要取消该任务吗?',
      confirmDelete: '确定要删除该任务吗? 此操作不可撤销。'
    }
  }
};
```

---

## 附录A: API接口汇总

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 任务列表 | GET | /api/ns-analysis/tasks | 获取任务列表(分页) |
| 创建任务 | POST | /api/ns-analysis/tasks | 创建新分析任务 |
| 任务详情 | GET | /api/ns-analysis/tasks/{taskId} | 获取任务详情 |
| 取消任务 | POST | /api/ns-analysis/tasks/{taskId}/cancel | 取消执行中的任务 |
| 删除任务 | DELETE | /api/ns-analysis/tasks/{taskId} | 删除任务及关联数据 |
| 任务进度 | GET | /api/ns-analysis/tasks/{taskId}/progress | 获取实时进度 |
| 执行日志 | GET | /api/ns-analysis/tasks/{taskId}/logs | 获取执行日志 |
| 分页日志 | GET | /api/ns-analysis/tasks/{taskId}/logs/page | 分页获取日志 |
| 分析结果 | GET | /api/ns-analysis/results/{taskId} | 获取分析结果 |
| 命名空间列表 | GET | /api/kubernetes/namespaces | 获取可用命名空间 |

---

## 附录B: 页面文件结构

```
src/views/riskAnalysis/
├── TaskList.vue              # 任务列表页面
├── TaskDetail.vue            # 任务详情页面
├── components/
│   ├── CreateTaskModal.vue   # 新建任务弹窗
│   ├── TaskStatusBadge.vue   # 任务状态徽章
│   ├── RiskScoreBadge.vue    # 风险分数徽章
│   ├── ExecutionProgress.vue # 执行进度条
│   ├── ExecutionLogs.vue     # 执行日志列表
│   ├── AnalysisResult/       # 分析结果组件目录
│   │   ├── Phase1Result.vue  # 阶段1结果
│   │   ├── Phase2Result.vue  # 阶段2结果
│   │   ├── Phase3Result.vue  # 阶段3结果
│   │   ├── Phase4Result.vue  # 阶段4结果
│   │   └── Phase5Result.vue  # 阶段5结果(重点)
│   ├── ScenarioCard.vue      # 场景卡片
│   └── ScenarioCodeModal.vue # 场景代码查看器
├── hooks/
│   ├── useTaskPolling.js     # 任务轮询Hook
│   └── useChaosNavigation.js # ChaosBlade跳转Hook
└── utils/
    ├── statusMapper.js       # 状态映射工具
    └── riskLevelMapper.js    # 风险级别映射工具
```

---

## 附录C: 设计决策说明

### C.1 为什么使用轮询而非WebSocket

1. **实现简单**: 轮询更易于实现和调试
2. **兼容性好**: 无需额外的WebSocket服务器配置
3. **任务特性**: 分析任务通常在几分钟内完成，轮询足以满足需求
4. **资源消耗可控**: 通过条件判断可在任务完成后停止轮询

### C.2 ChaosBlade集成方式

选择URL参数跳转而非API集成的原因:
1. **解耦**: 风险分析与ChaosBlade模块保持松耦合
2. **灵活性**: 用户可在跳转后手动调整实验参数
3. **权限控制**: 演练创建权限由ChaosBlade模块独立管理

### C.3 阶段5结果展示优先级

将阶段5作为重点展示的原因:
1. **业务价值最高**: 综合了所有层级的风险分析
2. **可操作性强**: 直接关联可执行的演练场景
3. **用户关注点**: 最终用户主要关心综合结论和建议