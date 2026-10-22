FROM node:22.14.0-alpine AS build
WORKDIR /src
COPY frontend/order-web/package*.json ./
RUN npm ci
COPY frontend/order-web ./
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.27.4-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
