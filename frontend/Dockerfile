# Stage 1: Build
FROM docker.m.daocloud.io/library/node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci

COPY . .
RUN npm run build

# Stage 2: Serve with nginx
FROM docker.m.daocloud.io/library/nginx:alpine

ARG APP_VERSION=0.2.2
LABEL org.opencontainers.image.title="aiphoto Frontend" \
      org.opencontainers.image.version="${APP_VERSION}"

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
