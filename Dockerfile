FROM gradle:9.5.1-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar -x test && \
    cp build/libs/*-SNAPSHOT.jar /app/application.jar

FROM build AS test
RUN gradle --no-daemon test

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && chown app:app /app
COPY --from=build --chown=app:app /app/application.jar ./application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
