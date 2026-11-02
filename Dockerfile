# Backend image: docker build -t todo-server .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# Dependencies change less often than sources, so they are downloaded into a cached layer of their own.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew resolveDependencies --no-daemon --console=plain
COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain \
    && java -Djarmode=tools -jar build/libs/application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
# Layered jar: a code change only rebuilds the small application layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
# JSON (Elastic Common Schema) logs so collectors can index fields such as requestId. Swagger UI and the spec are off;
# set API_DOCS_ENABLED=true to publish them.
ENV APP_ENV=production \
    API_DOCS_ENABLED=false \
    PORT=8080 \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
