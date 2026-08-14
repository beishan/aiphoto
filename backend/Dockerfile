# Stage 1: Build with Maven
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 AS build-base

WORKDIR /app

COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml
RUN mvn dependency:go-offline -B

COPY src ./src

FROM build-base AS test
RUN mvn test -B

FROM build-base AS build
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

ARG APP_VERSION=0.2.1
ENV APP_VERSION=${APP_VERSION}
LABEL org.opencontainers.image.title="aiphoto Backend" \
      org.opencontainers.image.version="${APP_VERSION}"

# Install FFmpeg for video thumbnail generation
RUN apk add --no-cache ffmpeg

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
