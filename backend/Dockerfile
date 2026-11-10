FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 shromikai
COPY --from=build /app/build/libs/shromikai-backend.jar app.jar
USER shromikai
EXPOSE 8080
# No heap dumps: memory may hold contract text (R-02).
ENTRYPOINT ["java", "-XX:-HeapDumpOnOutOfMemoryError", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
