# Multi-stage build for svc-task-resource (Spring Boot / Java 21)
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# 接收代理参数
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY=localhost,127.0.0.1

# 设置代理环境变量
ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}
ENV http_proxy=${HTTP_PROXY}
ENV https_proxy=${HTTPS_PROXY}
ENV no_proxy=${NO_PROXY}

WORKDIR /workspace

# 利用Docker缓存机制，先复制pom文件下载依赖
COPY pom.xml /workspace/
COPY svc-task-resource/pom.xml /workspace/svc-task-resource/

# 下载依赖（会通过代理）
RUN mvn -B -q dependency:resolve -pl svc-task-resource -am

# 复制源码并编译
COPY . /workspace
RUN mvn -B -q -DskipTests -pl svc-task-resource -am package

# 运行时镜像
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

COPY --from=builder /workspace/svc-task-resource/target/*.jar /app/app.jar

EXPOSE 8101

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]