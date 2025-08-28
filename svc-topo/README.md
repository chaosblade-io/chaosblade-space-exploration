# Java Topology Visualizer

基于OpenTelemetry trace数据的微服务拓扑可视化工具

## 🚀 项目特性

- **📁 Trace文件解析**: 支持OpenTelemetry Jaeger格式的trace-*.json文件上传和解析
- **🎯 图结构转换**: 使用JGraphT库将trace数据转换为符合topo_schema_design.md规范的内存图结构
- **🌐 Web界面**: 现代化的Web界面，支持文件上传、实时渲染和交互操作
- **📊 统计分析**: 提供详细的拓扑统计信息和RED指标展示
- **💾 多格式导出**: 支持JSON等多种格式导出
- **🔄 前后端分离架构**: 前端使用React + XFlow，后端使用Spring Boot，通过RESTful API通信

## 🏗️ 技术架构

``mermaid
graph TB
subgraph "前端层"
UI[React + XFlow UI]
end

    subgraph "API网关"
        PROXY[开发代理]
    end
    
    subgraph "后端层"
        BACKEND[Spring Boot API]
    end
    
    subgraph "服务层"
        PARSER[Trace解析服务]
        CONVERTER[拓扑转换服务]
    end
    
    subgraph "模型层"
        ENTITY[实体模型]
        TOPOLOGY[拓扑图模型]
    end
    
    subgraph "工具层"
        JGRAPHT[JGraphT图库]
        JACKSON[JSON处理]
    end
    
    UI --> PROXY
    PROXY --> BACKEND
    BACKEND --> PARSER
    PARSER --> CONVERTER
```

## 🛠️ 技术栈

- **后端框架**: Spring Boot 3.x
- **前端框架**: React 18 + XFlow
- **图数据结构**: JGraphT 1.5.x
- **JSON处理**: Jackson
- **UI组件库**: Ant Design
- **构建工具**: Maven + Vite
- **测试框架**: JUnit 5, Mockito

## 📋 系统要求

- Java 17+
- Node.js 16+
- Maven 3.6+
- 内存: 最少2GB
- 磁盘空间: 500MB

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd chaosblade-topo-visualizer
```

### 2. 构建项目

```bash
# 构建整个项目（包括前端和后端）
make build

# 或者分别构建
make build-frontend  # 仅构建前端
make                 # 构建后端
```

### 3. 运行应用

```bash
# 运行整个应用
make run

# 开发模式（前后端分离运行）
make dev
```

### 4. 访问应用

- 生产模式: http://localhost:8080
- 开发模式前端: http://localhost:3000
- 开发模式后端API: http://localhost:8080/api/

## 📖 使用指南

### 上传Trace文件

1. 点击"选择Trace文件"按钮或直接拖拽文件到上传区域
2. 选择trace-*.json格式的OpenTelemetry文件
3. 点击"上传并生成拓扑图"按钮
4. 系统会自动解析文件并生成可视化图形

### 导出功能

- **JSON格式**: 导出完整的拓扑数据结构

## 🔧 API接口

### Trace文件处理

```
POST /api/trace/upload          # 上传并处理trace文件
POST /api/trace/upload/batch    # 批量上传并处理trace文件
POST /api/trace/parse           # 仅解析trace文件
POST /api/trace/generate        # 基于JSON内容生成拓扑图
POST /api/trace/validate        # 验证trace文件格式
GET  /api/trace/formats         # 获取支持的文件格式
GET  /api/trace/health          # 健康检查
```

### 可视化接口

```
POST /api/visualization/statistics         # 获取统计信息
POST /api/visualization/export/json        # 导出JSON
```

### XFlow可视化接口

```
GET  /api/xflow/topology                   # 获取XFlow格式拓扑数据
POST /api/xflow/refresh                    # 刷新拓扑数据
GET  /api/xflow/nodes/{nodeId}             # 获取节点详情
POST /api/xflow/layout                     # 应用布局算法
```

## 📊 数据模型

项目基于三级实体模型设计：

- **1级实体**: Namespace、Service、ExternalService、Middleware
- **2级实体**: Pod、Instance、Host
- **3级实体**: RPC、RPCGroup

支持的关系类型：
- CONTAINS (包含关系)
- DEPENDS_ON (依赖关系)
- RUNS_ON (运行关系)
- INVOKES (调用关系)
- INSTANTIATED_BY (实例化关系)

## 🧪 测试

### 运行单元测试

```bash
mvn test
```

### 运行集成测试

```bash
mvn integration-test
```

### 测试覆盖率

```bash
mvn jacoco:report
```

## 📁 项目结构

```
chaosblade-topo-visualizer/
├── frontend/                               # 前端项目（React + XFlow）
│   ├── src/                                # 前端源码
│   │   ├── components/                     # UI组件
│   │   ├── config/                         # 配置文件
│   │   ├── services/                       # API服务
│   │   ├── styles/                         # 样式文件
│   │   ├── types/                          # TypeScript类型定义
│   │   ├── App.tsx                         # 主应用组件
│   │   └── main.tsx                        # 入口文件
│   ├── index.html                          # HTML模板
│   ├── package.json                        # 前端依赖
│   ├── vite.config.ts                      # Vite配置
│   └── tsconfig.json                       # TypeScript配置
├── src/main/java/com/topo/visualizer/
│   ├── TopoVisualizerApplication.java      # 应用启动类
│   ├── controller/                         # 控制器层
│   │   ├── TraceUploadController.java      # 文件上传控制器
│   │   ├── TopoVisualizationController.java # 可视化控制器
│   │   └── HomeController.java             # 主页控制器
│   ├── service/                            # 服务层
│   │   ├── TraceParserService.java         # Trace解析服务
│   │   └── TopologyConverterService.java   # 拓扑转换服务
│   ├── model/                              # 数据模型
│   │   ├── entity/                         # 实体模型
│   │   ├── trace/                          # Trace数据模型
│   │   └── topology/                       # 拓扑模型
│   ├── config/                             # 配置类
│   └── util/                               # 工具类
├── src/main/resources/
│   ├── static/                             # 静态资源
│   │   └── frontend/dist/                  # 前端构建产物
│   ├── templates/                          # 模板文件
│   └── application.yml                     # 应用配置
└── src/test/                               # 测试代码
```

## 🔄 开发模式

项目支持前后端分离的开发模式：

1. **后端开发**:
   ```bash
   mvn spring-boot:run
   ```
   后端服务运行在 http://localhost:8080

2. **前端开发**:
   ```bash
   cd frontend
   npm run dev
   ```
   前端开发服务器运行在 http://localhost:3000，并通过代理访问后端API

## 🛡️ 错误处理

应用包含完整的错误处理机制：

- 文件格式验证
- 文件大小限制（最大50MB）
- JSON格式检查
- 网络异常处理
- 渲染错误恢复

## 📈 性能考虑

- 支持大文件异步处理
- JGraphT提供高性能图算法
- 前端缓存优化
- 内存使用监控

## 🤝 贡献指南

1. Fork项目
2. 创建特性分支: `git checkout -b feature/AmazingFeature`
3. 提交更改: `git commit -m 'Add some AmazingFeature'`
4. 推送分支: `git push origin feature/AmazingFeature`
5. 提交Pull Request

## 📄 许可证

本项目采用MIT许可证 - 详情请见 [LICENSE](LICENSE) 文件

## 🙋 支持与反馈

如有问题或建议，请：

1. 查看[Wiki文档](wiki)
2. 提交[Issue](issues)
3. 参与[讨论](discussions)

## 🎯 路线图

- [ ] 支持更多trace格式（Zipkin、OpenTracing等）
- [ ] 实时trace数据流处理
- [ ] 分布式拓扑分析
- [ ] 性能瓶颈识别
- [ ] 告警和监控集成
- [ ] 多租户支持

---

## 📚 相关文档

- [OpenTelemetry官方文档](https://opentelemetry.io/)
- [JGraphT文档](https://jgrapht.org/)
- [Spring Boot指南](https://spring.io/guides/gs/spring-boot/)

**享受拓扑可视化的乐趣！** 🎉
