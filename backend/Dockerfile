ARG BACKEND_MAVEN_BASE_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
ARG BACKEND_JRE_BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

# Stage 1: Build with Maven
FROM ${BACKEND_MAVEN_BASE_IMAGE} AS build-base

WORKDIR /app

COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml
RUN --mount=type=cache,id=aiphoto-maven,target=/root/.m2/repository,sharing=locked \
    mvn dependency:go-offline -B

COPY src ./src

FROM build-base AS test
RUN --mount=type=cache,id=aiphoto-maven,target=/root/.m2/repository,sharing=locked \
    mvn test -B

FROM build-base AS build
RUN --mount=type=cache,id=aiphoto-maven,target=/root/.m2/repository,sharing=locked \
    mvn clean package -Dmaven.test.skip=true -B

# Stage 2: Run
FROM ${BACKEND_JRE_BASE_IMAGE}

# Keep system dependencies before release metadata so a version bump reuses FFmpeg.
RUN apk add --no-cache ffmpeg

ARG APP_VERSION=0.19.11
ENV APP_VERSION=${APP_VERSION}
LABEL org.opencontainers.image.title="aiphoto Backend" \
      org.opencontainers.image.version="${APP_VERSION}"

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
