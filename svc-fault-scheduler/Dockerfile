# Runtime-only image: use prebuilt JAR (no Maven build stage)
FROM eclipse-temurin:21-jre

# Set Java 21 JAVA_HOME to requested path via symlink
ENV TEMURIN_HOME=/opt/java/openjdk
RUN mkdir -p /usr/lib/jvm && ln -s ${TEMURIN_HOME} /usr/lib/jvm/java-21-openjdk-amd64 || true
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:/app/config/

WORKDIR /app

RUN mkdir -p /app/config

# Copy prebuilt JAR from repository (built on host beforehand)
COPY svc-fault-scheduler/target/*.jar /app/app.jar

EXPOSE 8103

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]