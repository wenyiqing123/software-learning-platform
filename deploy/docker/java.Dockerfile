FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --home-dir /app appuser

ARG JAR_FILE
ARG APP_PORT
COPY --chown=10001:0 ${JAR_FILE} /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0 -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

EXPOSE ${APP_PORT}
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
