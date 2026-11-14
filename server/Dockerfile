# Image of the API server. Build context: the server/ directory.
#   docker build -t hotspot-server server
# Base images are pinned by exact version and digest: change the tag and the digest together.

# Build stage: compiles the application and splits the jar into layers
FROM eclipse-temurin:25.0.5_8-jdk-alpine-3.24@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS build
WORKDIR /workspace

# Extra Gradle/JVM options for the build, e.g. a proxy or -Djava.net.preferIPv4Stack=true
# on a network without IPv6 (pass with --build-arg GRADLE_OPTS=...)
ARG GRADLE_OPTS=""

# The wrapper and the build scripts change rarely, so this layer is cached between builds
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain --version

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar \
    && jar="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar')" \
    && java -Djarmode=tools -jar "$jar" extract --layers --destination /extracted \
    && mv "/extracted/application/$(basename "$jar")" /extracted/application/app.jar

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:25.0.5_8-jre-alpine-3.24@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app -h /app app
WORKDIR /app

# Least frequently changed layers first, so a code change rebuilds only the last one
COPY --from=build --chown=app:app /extracted/dependencies/ ./
COPY --from=build --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extracted/application/ ./

LABEL org.opencontainers.image.source="https://github.com/potionsnotusedlol/hotspot-tracker" \
      org.opencontainers.image.description="Wi-Fi Moscow API server"

USER 10001:10001
EXPOSE 8080

# Override in the container environment to tune the JVM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Healthy only when the actuator reports UP (the database and Redis checks are included)
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
