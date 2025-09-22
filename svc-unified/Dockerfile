# 统一微服务镜像 Dockerfile
# 支持通过环境变量启动不同的微服务

# 构建阶段
FROM eclipse-temurin:21-jdk AS builder

# 安装Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制Maven配置文件
COPY pom.xml .
COPY svc-unified/pom.xml svc-unified/
COPY common/common-core/pom.xml common/common-core/
COPY common/common-test/pom.xml common/common-test/
COPY svc-task-resource/pom.xml svc-task-resource/
COPY svc-task-executor/pom.xml svc-task-executor/
COPY svc-fault-scheduler/pom.xml svc-fault-scheduler/
COPY svc-result-processor/pom.xml svc-result-processor/
COPY svc-reqrsp-proxy/pom.xml svc-reqrsp-proxy/
COPY svc-topo/pom.xml svc-topo/

# 复制源代码
COPY . .

# 先安装所有依赖模块到本地仓库，然后构建统一应用
RUN mvn clean install -DskipTests && \
    mvn clean package -pl svc-unified -am -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre AS runtime

# 安装curl用于健康检查
RUN apt-get update && \
    apt-get install -y curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 创建非root用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 复制构建的JAR文件
COPY --from=builder /app/svc-unified/target/svc-unified-*.jar app.jar

# 设置文件权限
RUN chown -R appuser:appuser /app
USER appuser

# 设置JVM参数
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

# 默认服务名称（可通过环境变量覆盖）
ENV SERVICE_NAME=task-resource

# 默认端口（可通过环境变量覆盖）
ENV SERVER_PORT=8080

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --service=$SERVICE_NAME --server.port=$SERVER_PORT"]
