FROM postgres:16.6-alpine
COPY docker/postgres-init.sh /docker-entrypoint-initdb.d/10-online-shop.sh
