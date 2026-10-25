#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
CREATE ROLE order_migration LOGIN PASSWORD 'order_migration_local_only';
CREATE ROLE catalog_migration LOGIN PASSWORD 'catalog_migration_local_only';
CREATE ROLE identity_migration LOGIN PASSWORD 'identity_migration_local_only';
CREATE ROLE order_runtime LOGIN PASSWORD 'order_runtime_local_only';
CREATE ROLE catalog_runtime LOGIN PASSWORD 'catalog_runtime_local_only';
CREATE ROLE identity_runtime LOGIN PASSWORD 'identity_runtime_local_only';
ALTER DATABASE orders OWNER TO order_migration;
ALTER DATABASE catalog OWNER TO catalog_migration;
ALTER DATABASE identity OWNER TO identity_migration;
REVOKE CONNECT ON DATABASE orders FROM PUBLIC;
REVOKE CONNECT ON DATABASE catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE identity FROM PUBLIC;
GRANT CONNECT ON DATABASE orders TO order_migration, order_runtime;
GRANT CONNECT ON DATABASE catalog TO catalog_migration, catalog_runtime;
GRANT CONNECT ON DATABASE identity TO identity_migration, identity_runtime;
SQL
for pair in 'orders order_migration order_runtime' 'catalog catalog_migration catalog_runtime' 'identity identity_migration identity_runtime'; do
  set -- $pair
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$1" <<SQL
ALTER SCHEMA public OWNER TO $2;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO $3;
ALTER DEFAULT PRIVILEGES FOR ROLE $2 IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $3;
ALTER DEFAULT PRIVILEGES FOR ROLE $2 IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO $3;
SQL
done
