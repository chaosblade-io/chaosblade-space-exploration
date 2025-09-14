## Topo Schema Version 1.0

#### 实体

我们提供了一种基于层级的 Schema 设计。我们将实体分为三个层级，每个层级包含不同的实体类型，具体如下：

*   1 级：Namespace，Service(application)，ExternalService(ip:port)，Middleware(MQ, DB)
    
*   2 级：Pod(application\_inst)，Instance，Host
    
*   3 级：RPC，RPCGroup
    

**1级实体（抽象服务实体）**

| 实体类型                               | 描述                                                               | 主要属性                                             |
| -------------------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------- |
| Namespace                              | 命名空间，用于组织和隔离资源                                       | 命名空间名称                                         |
| Service (Application)                  | 服务/应用实体，代表一个可独立运行的服务                            | 服务ID、服务名称、应用类型、版本、镜像等             |
| ExternalService                        | 外部服务，表示依赖的外部系统（如第三方API）                        | IP地址、端口号、服务类型（HTTP/gRPC）等              |
| ~~ServiceGroup (ApplicationCategory)~~ | ~~服务组，代表一个微服务应用下的一组服务，可能跨多个 Namespace。~~ | ~~Group 名称~~                                       |
| Middleware                             | 中间件实体，包括各种基础组件                                       | 中间件类型（MQ、DB等）、名称、配置信息（链接信息）等 |

**2级实体（运行时实例实体）**

| 实体类型                  | 描述                                           | 主要属性                                                         |
| ------------------------- | ---------------------------------------------- | ---------------------------------------------------------------- |
| Pod (ApplicationInstance) | 应用实例，代表一个运行中的应用容器实例         | Pod ID、所属应用、Cluster IP、资源使用情况、RED 指标、状态信息等 |
| Instance                  | 中间件的具体实例，比如一个 DB 实例             | 实例ID、所属服务、IP 地址、端口号、状态信息等                    |
| Host                      | 主机实例，代表一个 ECS 主机，或者一个 K8s Node | 主机 ID（关联 ECS ID）、IP 地址、Region ID、状态信息等           |

**3级实体（接口与调用实体）**

| 实体类型 | 描述                                                                                                      | 主要属性                                   |
| -------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| RPC      | 远程过程调用，代表一个具体的服务接口。涵盖了 RESTful、gRPC  等不同协议                                    | 接口名称、所属服务、调用类型、协议、路径等 |
| RPCGroup | RPC分组，将具有相似性的接口进行分组聚合，例如按照 URL 分组。<br>可以内置 Group 规则，也可以自定义 Group。 | 分组类型、所属服务、接口列表、调用统计等   |

#### 实体间关系

我们归纳了常见的实体间关系：

| 关系类型 | 关系名称    | 源实体(级别)    | 目标实体(级别)       | 描述                         |
| -------- | ----------- | --------------- | -------------------- | ---------------------------- |
| 包含关系 | CONTAINS    | Namespace(1级)  | Service(1级)         | 命名空间包含多个服务         |
| 依赖关系 | DEPENDS\_ON | Service(1级)    | Service(1级)         | 服务依赖其他服务             |
| 依赖关系 | DEPENDS\_ON | Service(1级)    | ExternalService(1级) | 服务依赖外部服务             |
| 依赖关系 | DEPENDS\_ON | Service(1级)    | Middleware(1级)      | 服务依赖中间件               |
| 包含关系 | CONTAINS    | Service(1级)    | Pod(2级)             | 一个服务含有多个 Pod         |
| 运行关系 | RUNS\_ON    | Pod(2级)        | Host(2级)            | 一个 Pod 运行在一台主机上    |
| 包含关系 | CONTAINS    | Middleware(1级) | Instance(2级)        | 中间件有多个实例             |
| 运行关系 | RUNS\_ON    | Instance(2级)   | Host(2级)            | 一个 DB 实例运行在一台主机上 |
| 调用关系 | INVOKES     | Service(1级)    | RPC(3级)             | Pod 调用自身或其他 Pod 接口  |
| 包含关系 | CONTAINS    | RPCGroup(3级)   | RPC(3级)             | Group 包含多个 RPC           |

#### 实体属性特征

##### 共有属性

所有实体都具有以下基础属性：

*   唯一标识符（Entity ID）
    
*   实体名称
    
*   创建时间与最后更新时间
    
*   标签和元数据
    
*   状态信息
    

##### 等级特征

1.  **1级实体**：顶层抽象实体，代表逻辑服务或资源，具有全局视角
    
2.  **2级实体**：运行时实例，代表具体运行的组件，包含运行时信息
    
3.  **3级实体**：接口与调用实体，代表服务间交互的具体内容
    

这种分层设计能够清晰地表示从逻辑架构到物理部署再到接口调用的完整视图，便于进行系统监控、问题诊断和容量规划。

##### Node 属性

从 Graph 的角度，我们提供 Node 的通用属性，作为 Entity 的附加属性。

| 属性名             | 类型   | 必填 | 描述                                 |
| ------------------ | ------ | ---- | ------------------------------------ |
| nodeId             | string | 是   | 节点唯一标识符                       |
| entity             | object | 是   | 实体信息对象                         |
| entity.entityId    | string | 是   | 实体唯一标识符                       |
| entity.type        | string | 是   | 实体类型（如 Service）               |
| entity.displayName | string | 是   | 实体显示名称                         |
| entity.firstSeen   | number | 是   | 实体首次出现时间戳（毫秒）           |
| entity.lastSeen    | number | 是   | 实体最后出现时间戳（毫秒）           |
| entity.name        | string | 否   | 实体名称（根据实体类型可能有所不同） |
| entity.appId       | string | 否   | 应用ID（适用于应用相关实体）         |
|                    |        |      |                                      |
| entity.regionId    | string | 否   | 区域ID                               |
| attrs              | object | 是   | 节点属性对象                         |
| attrs.RED          | object | 否   | RED指标数据                          |
| attrs.RED.count    | number | 否   | 请求量计数                           |
| attrs.RED.error    | number | 否   | 错误数                               |
| attrs.RED.rt       | number | 否   | 响应时间                             |
| attrs.RED.status   | string | 否   | 状态（如 success）                   |

**Edge 属性**

从 Graph 的角度，我们提供 Edge 的通用属性，分别作为 RelationShip 的附加属性。

| 属性名           | 类型   | 必填 | 描述                     |
| ---------------- | ------ | ---- | ------------------------ |
| edgeId           | string | 是   | 边唯一标识符             |
| from             | string | 是   | 源节点ID                 |
| to               | string | 是   | 目标节点ID               |
| type             | string | 是   | 边类型（如 CALLS）       |
| firstSeen        | number | 是   | 边首次出现时间戳（毫秒） |
| lastSeen         | number | 是   | 边最后出现时间戳（毫秒） |
| attrs            | object | 是   | 边属性对象               |
| attrs.RED        | object | 否   | RED指标数据              |
| attrs.RED.count  | number | 否   | 请求量计数               |
| attrs.RED.error  | number | 否   | 错误数                   |
| attrs.RED.rt     | number | 否   | 响应时间                 |
| attrs.RED.status | string | 否   | 状态（如 success）       |


## Topo Schema Version 2.0

拓扑 schema 从 `topo-` 格式到 `topology_` 格式的重大修订变化。新版本 schema 提供了更丰富的统计信息、增强的指标体系和更好的扩展性设计。

| 版本 | 文件前缀    | 主要特点                                   |
| ---- | ----------- | ------------------------------------------ |
| v1.0 | `topo-`     | 基础拓扑结构，简单的节点和边定义           |
| v2.0 | `topology_` | 增强版本，包含统计信息、元数据和扩展性支持 |


### 1. 新增顶层结构字段

新版本在根级别增加了三个重要的顶层字段：

```json
{
  "statistics": { /* 拓扑统计信息 */ },
  "empty": false,
  "metadata": { /* 元数据信息 */ },
  "nodes": [...],
  "edges": [...]
}
```

#### 1.1 Statistics 统计信息（新增）

提供拓扑图的概览统计信息，帮助用户快速了解拓扑规模和组成：

```json
{
  "statistics": {
    "nodeCount": 16,
    "edgeCount": 17,
    "nodeTypeCount": {
      "SERVICE": 5,
      "NAMESPACE": 1,
      "RPC": 4,
      "HOST": 1,
      "RPC_GROUP": 5
    },
    "edgeTypeCount": {
      "CONTAINS": 9,
      "DEPENDS_ON": 4,
      "INVOKES": 4
    }
  }
}
```

**字段说明：**
- `nodeCount`: 节点总数
- `edgeCount`: 边总数
- `nodeTypeCount`: 按实体类型分组的节点统计
- `edgeTypeCount`: 按关系类型分组的边统计

#### 1.2 Empty 标识（新增）

布尔值，标识当前拓扑图是否为空：

```json
{
  "empty": false
}
```

#### 1.3 Metadata 元数据（新增）

提供拓扑图的描述性信息和版本控制：

```json
{
  "metadata": {
    "title": "OpenTelemetry Trace 拓扑图",
    "description": "从trace数据生成的服务拓扑结构",
    "version": "1.0",
    "createdAt": 1756114948748,
    "updatedAt": 1756114948748
  }
}
```

**字段说明：**
- `title`: 拓扑图标题
- `description`: 拓扑图描述
- `version`: Schema 版本号
- `createdAt`: 创建时间戳（毫秒）
- `updatedAt`: 更新时间戳（毫秒）

### 2. Node 节点结构增强

#### 2.1 新增字段

**entityType（新增）**
在节点顶层添加实体类型快速标识：

```json
{
  "entityType": "SERVICE",
  "nodeId": "svc-details.default",
  // ... 其他字段
}
```

**redMetrics（新增）**
在节点顶层添加 RED 指标的快速访问：

```json
{
  "redMetrics": {
    "errorRate": 0,
    "successRate": 100,
    "healthy": true,
    "count": 1,
    "error": 0,
    "rt": 0,
    "status": "success"
  }
}
```

#### 2.2 Entity 结构增强

**attributes 对象（新增）**
替代原有的部分字段，提供更结构化的属性存储：

```json
{
  "entity": {
    "entityId": "svc-details.default",
    "type": "SERVICE",
    "displayName": "应用【details.default】",
    "name": "details.default",
    "appId": "opentelemetry-demo@details.default",
    "regionId": "default",
    "firstSeen": 1756114948749,
    "lastSeen": 1756114948749,
    "attributes": {}  // 新增：扩展属性容器
  }
}
```

#### 2.3 Attrs 属性增强

**extensions 对象（新增）**
在 attrs 中新增扩展属性容器：

```json
{
  "attrs": {
    "RED": {
      "errorRate": 0,
      "successRate": 100,
      "healthy": true,
      "count": 1,
      "error": 0,
      "rt": 0,
      "status": "success"
    },
    "extensions": {}  // 新增：扩展属性容器
  }
}
```

#### 2.4 RED 指标增强

原有的 RED 指标基础上新增了以下字段：
- `errorRate`: 错误率百分比
- `successRate`: 成功率百分比
- `healthy`: 健康状态布尔值

### 3. Edge 边结构增强

#### 3.1 新增字段

**redMetrics（新增）**
边级别的 RED 指标快速访问：

```json
{
  "redMetrics": {
    "errorRate": 0,
    "successRate": 100,
    "healthy": true,
    "count": 1,
    "error": 0,
    "rt": 0,
    "status": "success"
  },
  "edgeId": "ns-default-svc-details.default-CONTAINS",
  "from": "ns-default",
  "to": "svc-details.default",
  "type": "CONTAINS"
}
```

#### 3.2 Attrs 属性增强

与节点类似，边的 attrs 中也新增了 extensions 容器：

```json
{
  "attrs": {
    "RED": {
      "errorRate": 0,
      "successRate": 100,
      "healthy": true,
      "count": 1,
      "error": 0,
      "rt": 0,
      "status": "success"
    },
    "extensions": {}  // 新增：扩展属性容器
  }
}
```


### 架构改进优势

#### 1. 更丰富的统计信息
- 提供拓扑概览，便于快速了解系统规模
- 支持按类型统计，有助于架构分析

#### 2. 双层指标访问
- `redMetrics` 提供顶层快速访问
- `attrs.RED` 提供详细的指标信息
- 提高了数据访问效率

#### 3. 扩展性增强
- 通过 `attributes` 和 `extensions` 支持更多自定义数据
- 为未来功能扩展预留了空间

#### 4. 元数据支持
- 提供版本控制能力
- 支持拓扑图的描述和标识

#### 5. 健康状态增强
- 新增 `healthy`、`errorRate`、`successRate` 字段
- 提供更直观的系统健康状态视图

#### 6. 协议无关性
- 统一支持 HTTP、gRPC 等多种协议
- 便于异构系统的拓扑可视化

### 总结

新版本 `topology_` schema 相比 `topo-` 版本，在以下方面实现了重大改进：

1. **结构化统计信息** - 提供拓扑概览能力
2. **增强的 RED 指标体系** - 更丰富的监控数据
3. **更好的扩展性设计** - 支持未来功能扩展
4. **完整的元数据支持** - 版本控制和描述信息
5. **更清晰的实体类型标识** - 快速访问实体信息
6. **协议无关性** - 统一支持多种通信协议

这些改进使得新版本更适合生产环境使用，提供了更好的监控能力、扩展性和用户体验。
