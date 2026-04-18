# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-alpine AS builder

RUN apk add --no-cache git postgresql-client

WORKDIR /package
COPY . .
RUN ./gradlew bootJar --no-daemon || ./gradlew build --no-daemon

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache postgresql-client
COPY --from=builder /package/build/libs/*.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
