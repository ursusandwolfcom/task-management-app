FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852 AS sdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake ninja-build pkg-config git ca-certificates \
    libcrypto++-dev libsodium-dev libsqlite3-dev libcurl4-openssl-dev libicu-dev libssl-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
# Official SDK v10.22.0, pinned to its immutable commit.
RUN git init mega-sdk && cd mega-sdk \
    && git remote add origin https://github.com/meganz/sdk.git \
    && git fetch --depth 1 origin 76771e3b2603bbe0d1b61d0e70861e345ae96af5 \
    && git checkout --detach FETCH_HEAD
COPY native/mega /build/bridge
RUN --mount=type=cache,id=projectflow-mega-noble-v10.22,target=/build/out \
    cmake -S bridge -B out -G Ninja -DCMAKE_BUILD_TYPE=Release -DMEGA_SDK_SOURCE=/build/mega-sdk \
    && cmake --build out --parallel 2 --target projectflow-mega \
    && mkdir -p /artifact && cp out/projectflow-mega /artifact/projectflow-mega
RUN /artifact/projectflow-mega --version && /artifact/projectflow-mega --self-test

FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852 AS bridge
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libcrypto++8t64 libsodium23 libsqlite3-0 libcurl4t64 libicu74 libssl3t64 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=sdk /artifact/projectflow-mega /usr/local/bin/projectflow-mega
USER 10001:10001
ENTRYPOINT ["/usr/local/bin/projectflow-mega"]
