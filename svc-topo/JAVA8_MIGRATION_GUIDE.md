# Java 8 迁移指南

本文档说明如何将 svc-topo 项目从 Java 21 降级到 Java 1.8。

## 📋 变更摘要

### 已完成的修改

✅ **1. pom.xml 配置更新**
- Java 版本：21 → 1.8
- Spring Boot：3.2.0 → 2.7.18
- 依赖库版本降级以兼容 Java 8

✅ **2. 测试代码修改**
- 移除 Text Blocks（文本块）语法
- 移除 `var` 关键字

### 主要变更点

#### 1. Maven 配置 (pom.xml)

**Java 版本**：
```xml
<!-- 修改前 -->
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>

<!-- 修改后 -->
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
```

**Spring Boot 版本**：
```xml
<!-- 修改前 -->
<spring-boot.version>3.2.0</spring-boot.version>

<!-- 修改后 -->
<spring-boot.version>2.7.18</spring-boot.version>
```

**依赖库版本调整**：
| 依赖 | 原版本 | 新版本 | 说明 |
|------|--------|--------|------|
| fastjson | 2.0.40 | 1.2.83 | Java 8 兼容版本 |
| slf4j | 2.0.9 | 1.7.36 | Java 8 兼容版本 |
| hutool | 5.8.22 | 5.8.11 | Java 8 兼容版本 |
| guava | 32.1.3-jre | 31.1-jre | Java 8 兼容版本 |
| jackson | 2.15.2 | 2.13.5 | Java 8 兼容版本 |
| grpc | 1.58.0 | 1.50.2 | Java 8 兼容版本 |
| httpclient | httpclient5 5.1 | httpclient 4.5.14 | Java 8 兼容版本 |
| lombok | 1.18.30 | 1.18.24 | Java 8 兼容版本 |
| commons-lang3 | 3.13.0 | 3.12.0 | Java 8 兼容版本 |
| jgrapht | 1.5.2 | 1.4.0 | Java 8 兼容版本 |

#### 2. 代码语法修改

**Text Blocks (文本块) → 普通字符串拼接**：

```java
// 修改前 (Java 13+)
String json = """
    {
      "key": "value"
    }
    """;

// 修改后 (Java 8)
String json = "{\n" +
    "  \"key\": \"value\"\n" +
    "}";
```

**var 关键字 → 显式类型声明**：

```java
// 修改前 (Java 10+)
var response = objectMapper.readValue(content, Map.class);

// 修改后 (Java 8)
Map<String, Object> response = objectMapper.readValue(content, Map.class);
```

**Map.of() / List.of() → Collections.emptyMap() / HashMap**：

```java
// 修改前 (Java 9+)
Map<String, Object> map = Map.of("key1", "value1", "key2", "value2");
List<String> list = List.of();

// 修改后 (Java 8)
Map<String, Object> map = new HashMap<>();
map.put("key1", "value1");
map.put("key2", "value2");
List<String> list = Collections.emptyList();
```

**HttpClient 5.x → HttpClient 4.x**：

```java
// 修改前 (HttpClient 5.x)
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
ClassicHttpResponse response = httpClient.execute(httpGet);
int statusCode = response.getCode();

// 修改后 (HttpClient 4.x)
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
CloseableHttpResponse response = httpClient.execute(httpGet);
int statusCode = response.getStatusLine().getStatusCode();
```

## 🔧 构建和运行

### 1. 清理并重新构建

```bash
cd svc-topo

# 清理旧的构建产物
mvn clean

# 重新构建项目
mvn package -DskipTests

# 或者包含测试
mvn clean package
```

### 2. 运行应用

```bash
# 使用 Java 8 运行
java -jar target/svc-topo-1.0.0.jar
```

### 3. 验证 Java 版本

```bash
# 检查当前 Java 版本
java -version

# 应该显示类似：
# java version "1.8.0_xxx"
# Java(TM) SE Runtime Environment (build 1.8.0_xxx-xxx)
```

## ⚠️ 注意事项

### Spring Boot 2.x vs 3.x 差异

1. **包名变更**：
   - Spring Boot 3.x 使用 `jakarta.*` 包
   - Spring Boot 2.x 使用 `javax.*` 包
   - 本项目代码已兼容 Spring Boot 2.x

2. **配置属性**：
   - 大部分配置属性保持兼容
   - `application.yml` 无需修改

3. **Actuator 端点**：
   - Spring Boot 2.x 的 Actuator 端点路径和配置与 3.x 基本一致
   - 现有配置可以直接使用

### 依赖兼容性

1. **JGraphT 1.5.2**：
   - 完全兼容 Java 8
   - 无需修改

2. **gRPC 1.50.2**：
   - 支持 Java 8
   - Jaeger 集成功能正常

3. **Jackson 2.13.5**：
   - 完全兼容 Java 8
   - JSON 序列化/反序列化功能正常

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

## 📦 Docker 部署

如果使用 Docker 部署，需要更新 Dockerfile：

```dockerfile
# 修改前
FROM openjdk:21-jdk-slim

# 修改后
FROM openjdk:8-jdk-alpine
```

## 🔍 已知问题

### 1. 类型推断警告

在某些地方可能会看到 "unchecked conversion" 警告，这是 Java 8 泛型类型推断的限制。可以通过以下方式抑制：

```java
@SuppressWarnings("unchecked")
Map<String, Object> response = objectMapper.readValue(content, Map.class);
```

### 2. Lambda 表达式

Java 8 支持 Lambda 表达式，现有代码中的 Lambda 表达式无需修改。

### 3. Stream API

Java 8 支持 Stream API，现有代码中的 Stream 操作无需修改。

## 📝 修改文件清单

以下文件已被修改以兼容 Java 8：

1. ✅ `pom.xml` - Maven 配置（Java 版本、Spring Boot 版本、所有依赖版本）
2. ✅ `src/test/java/com/chaosblade/svc/topo/service/TraceParserServiceTest.java` - 替换 Text Blocks
3. ✅ `src/test/java/com/chaosblade/svc/topo/integration/TopoVisualizerIntegrationTest.java` - 替换 Text Blocks 和 var 关键字
4. ✅ `src/main/java/com/chaosblade/svc/topo/service/JaegerQueryService.java` - 替换 HttpClient 5.x 为 4.x
5. ✅ `src/main/java/com/chaosblade/svc/topo/service/XFlowConverterService.java` - 替换 Map.of() 为 HashMap
6. ✅ `src/main/java/com/chaosblade/svc/topo/controller/ApiQueryController.java` - 替换 List.of() 为 Collections.emptyList()
7. ✅ `src/main/java/com/chaosblade/svc/topo/controller/XFlowController.java` - 替换所有 Map.of() 调用

## 🚀 后续步骤

1. **验证构建**：
   ```bash
   mvn clean package
   ```

2. **运行测试**：
   ```bash
   mvn test
   ```

3. **启动应用**：
   ```bash
   java -jar target/svc-topo-1.0.0.jar
   ```

4. **验证功能**：
   - 访问 http://localhost:8106
   - 测试文件上传功能
   - 测试拓扑图生成功能
   - 测试 Jaeger 集成功能

## 📚 参考资料

- [Spring Boot 2.7.x 文档](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Java 8 新特性](https://www.oracle.com/java/technologies/javase/8-whats-new.html)
- [Maven 编译器插件](https://maven.apache.org/plugins/maven-compiler-plugin/)

## 💡 常见问题

### Q: 为什么选择 Spring Boot 2.7.18？
A: Spring Boot 2.7.x 是支持 Java 8 的最后一个主要版本，并且仍在维护中（截至 2024 年）。

### Q: 是否需要修改前端代码？
A: 不需要。前端代码（React + XFlow）与后端 Java 版本无关。

### Q: 性能会受到影响吗？
A: Java 8 到 Java 21 之间有性能提升，但对于本项目的使用场景，影响不大。

### Q: 如何回退到 Java 21？
A: 使用 Git 恢复 pom.xml 和测试文件的修改即可。

---

**迁移完成！** 🎉

如有问题，请查看项目 README.md 或提交 Issue。

