# Multi-stage build for svc-task-executor (Spring Boot / Java 21)
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY . /workspace
RUN mvn -B -q -DskipTests -pl svc-task-executor -am package

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
WORKDIR /app
COPY --from=builder /workspace/svc-task-executor/target/*.jar /app/app.jar
EXPOSE 8102
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

