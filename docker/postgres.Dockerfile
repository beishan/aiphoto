ARG POSTGRES_BASE_IMAGE=docker.m.daocloud.io/pgvector/pgvector:pg16
FROM ${POSTGRES_BASE_IMAGE}

COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
