ARG FRONTEND_NODE_BASE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
ARG FRONTEND_NGINX_BASE_IMAGE=docker.m.daocloud.io/library/nginx:alpine

# Normalize release-only fields in a separate stage. Dependencies downstream
# receive identical files when only the application version changes.
FROM ${FRONTEND_NODE_BASE_IMAGE} AS dependency-manifests

WORKDIR /app

COPY package.json package-lock.json ./
RUN node -e 'const fs = require("fs"); for (const name of ["package.json", "package-lock.json"]) { const data = JSON.parse(fs.readFileSync(name, "utf8")); data.version = "0.0.0"; if (data.packages && data.packages[""]) data.packages[""].version = "0.0.0"; fs.writeFileSync(name, JSON.stringify(data, null, 2) + "\n"); }'

# Stage 1: Build
FROM ${FRONTEND_NODE_BASE_IMAGE} AS build
WORKDIR /app
COPY --from=dependency-manifests /app/package.json /app/package-lock.json ./
RUN --mount=type=cache,id=aiphoto-npm,target=/root/.npm,sharing=locked \
    npm ci --registry=https://registry.npmmirror.com --prefer-offline --no-audit --no-fund

# Restore original manifests and release version for the actual application build.
COPY . .
RUN npm run build

# Stage 2: Serve with nginx
FROM ${FRONTEND_NGINX_BASE_IMAGE}

ARG APP_VERSION=0.18.41
LABEL org.opencontainers.image.title="aiphoto Frontend" \
      org.opencontainers.image.version="${APP_VERSION}"

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
