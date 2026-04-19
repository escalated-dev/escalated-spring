# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-alpine AS builder

RUN apk add --no-cache git

WORKDIR /package
COPY . .

# Append the host-app subproject to settings.gradle.kts and build it
RUN sed -i 's/\r$//' gradlew && \
    echo "include(\"docker:host-app\")" >> settings.gradle.kts && \
    echo "rootProject.name = \"escalated\"" >> settings.gradle.kts && \
    ./gradlew :docker:host-app:bootJar --no-daemon 2>&1 | tail -30

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache postgresql-client
WORKDIR /app
COPY --from=builder /package/docker/host-app/build/libs/app.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
