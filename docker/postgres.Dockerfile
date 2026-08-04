FROM docker.m.daocloud.io/pgvector/pgvector:pg16

COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
