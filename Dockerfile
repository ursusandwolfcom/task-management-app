FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY src src
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
ENV TZ=UTC JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
COPY --from=build /build/target/task-management-app-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
