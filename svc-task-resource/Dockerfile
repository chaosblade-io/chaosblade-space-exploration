# Multi-stage build for svc-task-resource (Spring Boot / Java 21)
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
# Copy entire repo as context; we build only this module with -pl
COPY . /workspace
RUN mvn -B -q -DskipTests -pl svc-task-resource -am package

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
WORKDIR /app
COPY --from=builder /workspace/svc-task-resource/target/*.jar /app/app.jar
EXPOSE 8101
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

