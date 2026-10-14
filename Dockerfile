ARG ARTIFACT_STAGE=build
FROM node:24.20.0-bookworm-slim@sha256:ba849c60be29959425b8734d57b8b4b7d56f98edd9504c9af091d5281095a71e AS frontend
WORKDIR /build
COPY package.json package-lock.json workspace-vite.config.mjs ./
COPY frontend frontend
COPY scripts/build-workspace.mjs scripts/build-workspace.mjs
RUN npm ci --ignore-scripts && npm run build:workspace && npm run test:workspace

FROM eclipse-temurin:21-jdk-jammy@sha256:e0c60c487345d1dc9d0fc7b6f0496f3cc941e5132e09296cc17a6decc71b902b AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY src src
COPY --from=frontend /build/frontend/dist frontend/dist
# The Node stage already performed strict type/build/unit checks for these exact UI inputs.
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -Dworkspace.prebuilt=true clean verify \
    && mkdir /artifact && cp target/task-management-app-0.0.1-SNAPSHOT.jar /artifact/app.jar

# Opt-in path for a caller that has already verified this exact JAR and revision.
FROM scratch AS verified
COPY target/task-management-app-0.0.1-SNAPSHOT.jar /artifact/app.jar

FROM ${ARTIFACT_STAGE} AS artifact

FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
ENV TZ=UTC JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
COPY --from=artifact /artifact/app.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=12 \
    CMD curl --fail --silent --max-time 3 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
