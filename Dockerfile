FROM gradle:9.5.1-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar -x test && \
    cp build/libs/*-SNAPSHOT.jar /app/application.jar

FROM build AS test
RUN gradle --no-daemon test

FROM alpine:3.23 AS telemetry
ARG OTEL_AGENT_VERSION=2.32.0
ARG OTEL_AGENT_SHA256=f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82
RUN wget -q -O /agent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar && \
    echo "${OTEL_AGENT_SHA256}  /agent.jar" | sha256sum -c -

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && chown app:app /app
COPY --from=build --chown=app:app /app/application.jar ./application.jar
COPY --from=telemetry --chown=app:app /agent.jar /otel/opentelemetry-javaagent.jar
ARG APP_VERSION=local
ENV APP_VERSION=${APP_VERSION}
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
