#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
CREATE ROLE order_runtime LOGIN PASSWORD 'order_runtime_local_only';
CREATE ROLE catalog_runtime LOGIN PASSWORD 'catalog_runtime_local_only';
CREATE ROLE identity_runtime LOGIN PASSWORD 'identity_runtime_local_only';
SQL
for pair in 'orders order_app order_runtime' 'catalog catalog_app catalog_runtime' 'identity identity_app identity_runtime'; do
  set -- $pair
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$1" <<SQL
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE $1 TO $3;
GRANT USAGE ON SCHEMA public TO $3;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $3;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO $3;
ALTER DEFAULT PRIVILEGES FOR ROLE $2 IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $3;
ALTER DEFAULT PRIVILEGES FOR ROLE $2 IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO $3;
SQL
done
