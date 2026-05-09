# Multi-stage build: produces a slim, non-root runtime image.
# Build stage uses Maven with JDK 21; runtime uses a distroless JRE.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && \
    mkdir -p /workspace/extracted && \
    java -Djarmode=tools -jar target/sda-telemetry-demo-*.jar extract --layers --launcher --destination /workspace/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
