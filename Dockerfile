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

FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852 AS mega-sdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake ninja-build pkg-config git ca-certificates \
    libcrypto++-dev libsodium-dev libsqlite3-dev libcurl4-openssl-dev libicu-dev libssl-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
RUN git init mega-sdk && cd mega-sdk \
    && git remote add origin https://github.com/meganz/sdk.git \
    && git fetch --depth 1 origin 76771e3b2603bbe0d1b61d0e70861e345ae96af5 \
    && git checkout --detach FETCH_HEAD
COPY native/mega /build/bridge
RUN --mount=type=cache,id=projectflow-mega-noble-v10.22,target=/build/out \
    cmake -S bridge -B out -G Ninja -DCMAKE_BUILD_TYPE=Release -DMEGA_SDK_SOURCE=/build/mega-sdk \
    && cmake --build out --parallel 2 --target projectflow-mega \
    && mkdir -p /artifact && cp out/projectflow-mega /artifact/projectflow-mega \
    && cmake -DROOT=/build/mega-sdk -DDESTINATION=/artifact/notices -P bridge/licenses.cmake
RUN /artifact/projectflow-mega --self-test

FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl libcrypto++8t64 libsodium23 libsqlite3-0 libicu74 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
ENV TZ=UTC JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
COPY --from=artifact /artifact/app.jar app.jar
COPY --from=mega-sdk /artifact/projectflow-mega /usr/local/bin/projectflow-mega
COPY --from=mega-sdk /artifact/notices /usr/share/doc/projectflow-mega
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=12 \
    CMD curl --fail --silent --max-time 3 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
