FROM openjdk:21-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制 JAR 文件到容器中
COPY ./target/svc-topo-1.0.0.jar ./svc-topo.jar

# 暴露端口
EXPOSE 8106

# 设置环境变量，用于配置 Jaeger 和服务名称
# 如果环境变量未指定，则使用 application.yml 中的默认配置
ENV JaegerHost=""
ENV JaegerPort=""
ENV EntryService=""

# 启动应用
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "svc-topo.jar"]