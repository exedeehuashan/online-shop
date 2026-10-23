#!/bin/sh
set -eu
psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE order_app LOGIN PASSWORD 'order_dev_only';
CREATE DATABASE orders OWNER order_app;
CREATE ROLE catalog_app LOGIN PASSWORD 'catalog_dev_only';
CREATE DATABASE catalog OWNER catalog_app;
SQL
