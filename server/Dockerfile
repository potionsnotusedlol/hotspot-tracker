# Image of the API server. Build context: the server/ directory.
#   docker build -t hotspot-server server
# Base images are pinned by exact version and digest: change the tag and the digest together.

# Build stage: compiles the application and splits the jar into layers
FROM eclipse-temurin:25.0.5_8-jdk-alpine-3.24@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS build
WORKDIR /workspace

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

# Runtime stage: JRE only
FROM eclipse-temurin:25.0.5_8-jre-alpine-3.24@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
WORKDIR /app

# Least frequently changed layers first, so a code change rebuilds only the last one
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
