#!/bin/sh
set -eu
umask 077

die() { printf 'ERROR: %s\n' "$*" >&2; exit 2; }
say() { printf '%s\n' "$*" >&2; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"; }
valid_name() { printf '%s' "$1" | grep -Eq '^[a-z][a-z0-9-]{5,47}$'; }
check_name() { [ -n "$2" ] || die "$1 is required"; valid_name "$2" || die "unsafe $1"; }
exists() { docker ps -a --filter "label=com.docker.compose.project=$1" --format '{{.ID}}' | grep -q . || docker volume ls --filter "label=com.docker.compose.project=$1" -q | grep -q .; }
assert_target_new() { check_name target-project "$1"; [ "$1" != online-shop-uat ] || die 'reserved target'; ! exists "$1" || die 'target already exists'; }

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
BASE="$ROOT/compose.yaml"
SECURITY="$ROOT/compose.order-security.yaml"
HARDENED="$ROOT/compose.uat-hardened.yaml"
compose_base() { project=$1; shift; docker compose -p "$project" -f "$BASE" "$@"; }
compose_source() { project=$1; shift; docker compose -p "$project" -f "$BASE" -f "$SECURITY" "$@"; }
compose_hardened() { project=$1; shift; docker compose -p "$project" -f "$BASE" -f "$SECURITY" -f "$HARDENED" "$@"; }
container() { compose_base "$1" ps -q postgres; }
db_exec() { docker exec "$(container "$1")" psql -XAt -v ON_ERROR_STOP=1 -U postgres -d "$2" -c "$3"; }

critical_tables() {
  case "$1" in
    orders) printf '%s\n' orders order_items order_idempotency outbox_events order_audit_events;;
    catalog) printf '%s\n' products product product_attribute product_attribute_value product_attribute_assignment product_sku product_sku_attribute_value category product_category product_image catalog_legacy_migration_issue catalog_legacy_reconciliation;;
    identity) printf '%s\n' sys_user sys_role sys_permission sys_user_role sys_role_permission security_audit_event;;
    *) die "no critical-table allow-list for database: $1";;
  esac
}

database_inventory() {
  project=$1 db=$2 output=$3 critical='[]'
  snapshot=$(date -u +%FT%TZ)
  for table in $(critical_tables "$db"); do
    [ "$(db_exec "$project" "$db" "select to_regclass('public.$table') is not null;")" = t ] || die "missing critical table: $db.public.$table"
    row=$(db_exec "$project" "$db" "with canonical as (select to_jsonb(t)::text value from public.$table t), summary as (select count(*)::bigint row_count,coalesce(string_agg(value,E'\\n' order by value collate \"C\"),'') value from canonical) select json_build_object('name','$table','rowCount',row_count,'checksumAlgorithm','postgres-jsonb-c-v1+sha256','checksum',encode(sha256(convert_to(value,'UTF8')),'hex')) from summary;")
    critical=$(jq -cn --argjson a "$critical" --argjson b "$row" '$a+[$b]')
  done
  base=$(db_exec "$project" "$db" "select json_build_object('database',current_database(),'owner',(select pg_get_userbyid(datdba) from pg_database where datname=current_database()),'serverVersion',version(),'flyway',coalesce((select json_agg(x order by installed_rank) from (select installed_rank,version,description,success from flyway_schema_history)x),'[]'::json),'tables',coalesce((select json_agg(json_build_object('name',tablename,'owner',tableowner) order by tablename) from pg_tables where schemaname='public'),'[]'::json),'grants',coalesce((select json_agg(x order by table_name,grantee,privilege_type) from (select table_name,grantee,privilege_type from information_schema.role_table_grants where table_schema='public')x),'[]'::json));")
  jq -cn --argjson base "$base" --argjson critical "$critical" --arg snapshot "$snapshot" '$base+{snapshotAt:$snapshot,criticalTables:$critical}' >"$output.tmp"
  mv "$output.tmp" "$output"
}

compare_inventory_files() {
  expected=$1 actual=$2
  [ -f "$expected" ] || die "expected inventory missing: $expected"
  [ -f "$actual" ] || die "actual inventory missing: $actual"
  a=$(jq -cS '.criticalTables|if type=="array" and length>0 then . else error("missing criticalTables") end' "$expected") || die 'invalid expected inventory'
  b=$(jq -cS '.criticalTables|if type=="array" and length>0 then . else error("missing criticalTables") end' "$actual") || die 'invalid actual inventory'
  [ "$a" = "$b" ] || die "critical-table semantic mismatch: $(jq -r .database "$expected")"
}

wait_healthy() {
  cid=$1 label=$2 i=0
  while :; do
    state=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)
    [ "$state" = healthy ] && return
    case "$state" in exited|dead) die "$label exited before healthy";; esac
    i=$((i+1)); [ "$i" -lt 120 ] || die "$label not healthy"; sleep 1
  done
}

wait_responding() {
  cid=$1 label=$2 i=0
  while ! docker exec "$cid" curl -sS -o /dev/null http://127.0.0.1:8080/actuator/health 2>/dev/null; do
    state=$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || true); [ "$state" = running ] || die "$label exited before responding"
    i=$((i+1)); [ "$i" -lt 120 ] || die "$label not responding"; sleep 1
  done
}

ACTION=${1:-}; shift || true
SOURCE= TARGET= EVIDENCE= MANIFEST= CONFIRM= EXPECTED_INVENTORY= ACTUAL_INVENTORY= NONCE=
CI_GENERATED=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --source-project) SOURCE=${2:-}; shift 2;; --target-project) TARGET=${2:-}; shift 2;;
    --evidence-dir) EVIDENCE=${2:-}; shift 2;; --manifest) MANIFEST=${2:-}; shift 2;;
    --expected-inventory) EXPECTED_INVENTORY=${2:-}; shift 2;; --actual-inventory) ACTUAL_INVENTORY=${2:-}; shift 2;;
    --confirm) CONFIRM=${2:-}; shift 2;; --ci-generated) CI_GENERATED=true; shift;; --nonce) NONCE=${2:-}; shift 2;;
    *) die "unknown argument: $1";;
  esac
done
[ -n "$ACTION" ] || die 'action is required'
for tool in docker jq sha256sum git; do need "$tool"; done

inventory() {
  check_name source-project "$SOURCE"; exists "$SOURCE" || die 'source does not exist'
  [ -n "$EVIDENCE" ] || die 'evidence-dir is required'; mkdir -p "$EVIDENCE"
  cfg=$(mktemp "$EVIDENCE/.compose.XXXXXX"); compose_source "$SOURCE" config >"$cfg"
  cid=$(container "$SOURCE"); [ -n "$cid" ] || die 'source postgres is absent'; wait_healthy "$cid" 'source postgres'
  for db in orders catalog identity; do database_inventory "$SOURCE" "$db" "$EVIDENCE/$db-inventory.json"; done
  images='[]'
  for sid in $(docker ps -q --filter "label=com.docker.compose.project=$SOURCE"); do
    image_ref=$(docker inspect -f '{{.Config.Image}}' "$sid")
    image_json=$(docker image inspect "$image_ref" 2>/dev/null || true); digests='[]'
    [ -z "$image_json" ] || digests=$(printf '%s' "$image_json" | jq '.[0].RepoDigests // []')
    item=$(docker inspect "$sid" | jq '.[0]|{service:(.Config.Labels["com.docker.compose.service"]),imageId:.Image,imageReference:.Config.Image}' | jq --argjson digests "$digests" '.+{repoDigests:$digests}')
    images=$(jq -cn --argjson a "$images" --argjson b "$item" '$a+[$b]')
  done
  volumes=$(docker volume ls --filter "label=com.docker.compose.project=$SOURCE" --format '{{.Name}}' | jq -Rsc 'split("\n")|map(select(length>0))|sort')
  roles=$(db_exec "$SOURCE" postgres "select coalesce(json_agg(x order by rolname),'[]'::json) from (select rolname,rolcanlogin,rolsuper,rolcreatedb,rolcreaterole from pg_roles where rolname !~ '^pg_' and rolname <> 'postgres')x;")
  jq -n --arg schema online-shop-uat/v2 --arg source "$SOURCE" --arg git "$(git -C "$ROOT" rev-parse HEAD)" --arg created "$(date -u +%FT%TZ)" --arg digest "$(sha256sum "$cfg"|awk '{print $1}')" --arg compose "$(docker compose version --short)" --arg pg "$(docker exec "$cid" postgres --version)" --argjson images "$images" --argjson volumes "$volumes" --argjson roles "$roles" '{schemaVersion:$schema,sourceProject:$source,gitSha:$git,createdAt:$created,composeVersion:$compose,composeDigest:$digest,postgresVersion:$pg,images:$images,volumes:$volumes,roles:$roles,backupComplete:false}' >"$EVIDENCE/manifest.json.tmp"
  mv "$EVIDENCE/manifest.json.tmp" "$EVIDENCE/manifest.json"; rm -f "$cfg"
}

write_bootstrap() {
  cat >"$EVIDENCE/role-bootstrap.sql.tmp" <<'SQL'
DO $$BEGIN CREATE ROLE order_migration LOGIN PASSWORD 'order_migration_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
DO $$BEGIN CREATE ROLE catalog_migration LOGIN PASSWORD 'catalog_migration_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
DO $$BEGIN CREATE ROLE identity_migration LOGIN PASSWORD 'identity_migration_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
DO $$BEGIN CREATE ROLE order_runtime LOGIN PASSWORD 'order_runtime_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
DO $$BEGIN CREATE ROLE catalog_runtime LOGIN PASSWORD 'catalog_runtime_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
DO $$BEGIN CREATE ROLE identity_runtime LOGIN PASSWORD 'identity_runtime_local_only'; EXCEPTION WHEN duplicate_object THEN NULL; END$$;
SQL
  mv "$EVIDENCE/role-bootstrap.sql.tmp" "$EVIDENCE/role-bootstrap.sql"
}

backup() {
  inventory; cid=$(container "$SOURCE"); write_bootstrap
  for db in orders catalog identity; do
    start=$(date -u +%FT%TZ); docker exec "$cid" pg_dump -U postgres -Fc --no-owner --no-acl "$db" >"$EVIDENCE/$db.dump.tmp"; mv "$EVIDENCE/$db.dump.tmp" "$EVIDENCE/$db.dump"
    docker exec -i "$cid" pg_restore --list <"$EVIDENCE/$db.dump" >/dev/null
    end=$(date -u +%FT%TZ); jq --arg db "$db" --arg start "$start" --arg end "$end" '.databaseSnapshots=(.databaseSnapshots//{})|.databaseSnapshots[$db]={startedAt:$start,completedAt:$end}' "$EVIDENCE/manifest.json" >"$EVIDENCE/manifest.next"; mv "$EVIDENCE/manifest.next" "$EVIDENCE/manifest.json"
  done
  (cd "$EVIDENCE" && sha256sum role-bootstrap.sql *.dump *-inventory.json >checksums.sha256.tmp && mv checksums.sha256.tmp checksums.sha256)
  jq '.backupComplete=true|.sealedAt=(now|todateiso8601)' "$EVIDENCE/manifest.json" >"$EVIDENCE/manifest.sealed"; mv "$EVIDENCE/manifest.sealed" "$EVIDENCE/manifest.json"
  (cd "$EVIDENCE" && sha256sum manifest.json >manifest.sha256.tmp && mv manifest.sha256.tmp manifest.sha256)
  say "sealed backup: $EVIDENCE/manifest.json"
}

verify_manifest() {
  [ -f "$MANIFEST" ] || die 'manifest missing'; dir=$(CDPATH= cd -- "$(dirname "$MANIFEST")" && pwd)
  [ "$(jq -r .schemaVersion "$MANIFEST")" = online-shop-uat/v2 ] || die 'unsupported manifest'
  [ "$(jq -r .backupComplete "$MANIFEST")" = true ] || die 'backup is not sealed'
  (cd "$dir" && sha256sum -c manifest.sha256 >/dev/null && sha256sum -c checksums.sha256 >/dev/null) || die 'backup checksum mismatch'
  EVIDENCE=$dir
}

verify_semantics() {
  project=$1
  for db in orders catalog identity; do actual=$(mktemp "$EVIDENCE/.$db-verify.XXXXXX"); database_inventory "$project" "$db" "$actual"; compare_inventory_files "$EVIDENCE/$db-inventory.json" "$actual"; rm -f "$actual"; done
}

validate_metadata() {
  project=$1
  for spec in 'orders order_migration order_runtime' 'catalog catalog_migration catalog_runtime' 'identity identity_migration identity_runtime'; do
    set -- $spec; db=$1 owner=$2 runtime=$3
    [ "$(db_exec "$project" postgres "select pg_get_userbyid(datdba) from pg_database where datname='$db';")" = "$owner" ] || die "$db database owner mismatch"
    [ "$(db_exec "$project" postgres "select has_database_privilege('$runtime','$db','CONNECT');")" = t ] || die "$runtime lacks CONNECT on $db"
    [ "$(db_exec "$project" "$db" "select count(*) > 0 and bool_and(tableowner='$owner') from pg_tables where schemaname='public';")" = t ] || die "$db table ownership mismatch"
    [ "$(db_exec "$project" "$db" "select count(*) from flyway_schema_history where success;")" -gt 0 ] || die "$db Flyway history invalid"
  done
}

validate_security() {
  project=$1 cid=$(container "$1")
  for db in orders catalog identity; do
    case "$db" in orders) runtime=order_runtime other=catalog;; catalog) runtime=catalog_runtime other=identity;; identity) runtime=identity_runtime other=orders;; esac
    docker exec -e PGPASSWORD="${runtime}_local_only" "$cid" psql -XAt -v ON_ERROR_STOP=1 -U "$runtime" -d "$db" -c 'select 1' >/dev/null
    if docker exec -e PGPASSWORD="${runtime}_local_only" "$cid" psql -XAt -v ON_ERROR_STOP=1 -U "$runtime" -d "$db" -c 'create table privilege_escape(id int)' >/dev/null 2>&1; then die "$runtime can execute DDL"; fi
    if docker exec -e PGPASSWORD="${runtime}_local_only" "$cid" psql -XAt -v ON_ERROR_STOP=1 -U "$runtime" -d "$db" -c 'grant pg_read_all_data to current_user' >/dev/null 2>&1; then die "$runtime can grant roles"; fi
    if docker exec -e PGPASSWORD="${runtime}_local_only" "$cid" psql -XAt -v ON_ERROR_STOP=1 -U "$runtime" -d "$other" -c 'select 1' >/dev/null 2>&1; then die "$runtime can connect across contexts"; fi
  done
}

restore() {
  assert_target_new "$TARGET"; verify_manifest
  compose_hardened "$TARGET" up -d postgres; cid=$(container "$TARGET"); [ -n "$cid" ] || die 'target postgres absent'; wait_healthy "$cid" 'target postgres'
  docker exec -i "$cid" psql -XAt -v ON_ERROR_STOP=1 -U postgres -d postgres <"$EVIDENCE/role-bootstrap.sql"
  for db in orders catalog identity; do
    case "$db" in orders) owner=order_migration; runtime=order_runtime;; catalog) owner=catalog_migration; runtime=catalog_runtime;; identity) owner=identity_migration; runtime=identity_runtime;; esac
    docker exec "$cid" dropdb -U postgres "$db"; docker exec "$cid" createdb -U postgres -O "$owner" "$db"
    docker exec "$cid" psql -U postgres -d postgres -c "REVOKE CONNECT ON DATABASE $db FROM PUBLIC; GRANT CONNECT ON DATABASE $db TO $owner, $runtime;" >/dev/null
    docker exec -i "$cid" pg_restore -U postgres -d "$db" --role="$owner" --no-owner --no-acl <"$EVIDENCE/$db.dump"
    docker exec "$cid" psql -U postgres -d "$db" -c "ALTER SCHEMA public OWNER TO $owner; REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO $runtime; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO $runtime; GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public TO $runtime; ALTER DEFAULT PRIVILEGES FOR ROLE $owner IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO $runtime; ALTER DEFAULT PRIVILEGES FOR ROLE $owner IN SCHEMA public GRANT USAGE,SELECT,UPDATE ON SEQUENCES TO $runtime;" >/dev/null
  done
  verify_semantics "$TARGET"; validate_metadata "$TARGET"; validate_security "$TARGET"; say "restored isolated target: $TARGET"
}

verify() { check_name target-project "$TARGET"; exists "$TARGET" || die 'target does not exist'; verify_manifest; cid=$(container "$TARGET"); [ -n "$cid" ] || die 'target postgres absent'; wait_healthy "$cid" 'target postgres'; verify_semantics "$TARGET"; validate_metadata "$TARGET"; validate_security "$TARGET"; say "verified target: $TARGET"; }

fresh_install() {
  assert_target_new "$TARGET"
  compose_hardened "$TARGET" up -d --build
  for service in postgres order-api catalog-api identity-api web uat-auth; do sid=$(compose_hardened "$TARGET" ps -q "$service"); [ -n "$sid" ] && wait_healthy "$sid" "$service"; done
  validate_metadata "$TARGET"; validate_security "$TARGET"; say "fresh hardened install ready: $TARGET"
}

upgrade() {
  check_name target-project "$TARGET"; exists "$TARGET" || die 'target does not exist'; verify_manifest
  compose_hardened "$TARGET" run --rm migrate-orders migrate
  compose_hardened "$TARGET" run --rm migrate-catalog migrate
  compose_hardened "$TARGET" run --rm migrate-identity migrate
  network="${TARGET}_app"; old=''
  for spec in 'order-api orders order_runtime SPRING_DATASOURCE' 'catalog-api catalog catalog_runtime CATALOG_DB' 'identity-api identity identity_runtime IDENTITY_DB'; do
    set -- $spec; service=$1 db=$2 user=$3 prefix=$4; image=$(jq -r --arg s "$service" '.images[]|select(.service==$s)|.imageId' "$MANIFEST"); [ -n "$image" ] && [ "$image" != null ] || die "previous image identity missing: $service"
    name="$TARGET-previous-${service%-api}"; old="$old $name"
    case "$service" in order-api) docker run -d --name "$name" --label "online-shop.uat.owner=$TARGET" --network "$network" -e SPRING_FLYWAY_ENABLED=false -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/$db" -e SPRING_DATASOURCE_USERNAME="$user" -e SPRING_DATASOURCE_PASSWORD="${user}_local_only" "$image" >/dev/null;; catalog-api) docker run -d --name "$name" --label "online-shop.uat.owner=$TARGET" --network "$network" -e SPRING_FLYWAY_ENABLED=false -e CATALOG_DB_URL="jdbc:postgresql://postgres:5432/$db" -e CATALOG_DB_USER="$user" -e CATALOG_DB_PASSWORD="${user}_local_only" "$image" >/dev/null;; identity-api) docker run -d --name "$name" --label "online-shop.uat.owner=$TARGET" --network "$network" -e SPRING_FLYWAY_ENABLED=false -e IDENTITY_SECURITY_ENABLED=false -e IDENTITY_DB_URL="jdbc:postgresql://postgres:5432/$db" -e IDENTITY_DB_USER="$user" -e IDENTITY_DB_PASSWORD="${user}_local_only" "$image" >/dev/null;; esac
    if [ "$service" = identity-api ]; then wait_responding "$name" "previous $service"; else wait_healthy "$name" "previous $service"; fi
  done
  docker rm -f $old >/dev/null
  compose_hardened "$TARGET" up -d --build
  for service in order-api catalog-api identity-api web uat-auth; do sid=$(compose_hardened "$TARGET" ps -q "$service"); [ -n "$sid" ] && wait_healthy "$sid" "$service"; done
  fixture=$(compose_hardened "$TARGET" ps -aq uat-rbac-fixture); [ -n "$fixture" ] || die 'RBAC fixture absent'
  i=0; while [ "$(docker inspect -f '{{.State.Status}}' "$fixture")" != exited ]; do i=$((i+1)); [ "$i" -lt 60 ] || die 'RBAC fixture did not finish'; sleep 1; done
  [ "$(docker inspect -f '{{.State.ExitCode}}' "$fixture")" = 0 ] || die 'RBAC fixture failed'
  validate_metadata "$TARGET"; validate_security "$TARGET"; say "expanded schema compatible; previous writers stopped; current switch healthy: $TARGET"
}

issue_ci_manifest() {
  check_name target-project "$TARGET"; case "$TARGET" in ci-task26-*) :;; *) die 'CI project must use ci-task26 prefix';; esac
  assert_target_new "$TARGET"; [ -n "$EVIDENCE" ] || die 'evidence-dir is required'; [ -n "$NONCE" ] || die 'CI nonce is required'
  printf '%s' "$NONCE" | grep -Eq '^[A-Za-z0-9_-]{16,128}$' || die 'unsafe CI nonce'
  mkdir -p "$EVIDENCE"; jq -n --arg p "$TARGET" --arg n "$NONCE" --arg t "$(date -u +%FT%TZ)" '{schemaVersion:"online-shop-uat-ci/v1",ciProject:$p,ciNonce:$n,issuedAt:$t}' >"$EVIDENCE/ci-ownership.json.tmp"; mv "$EVIDENCE/ci-ownership.json.tmp" "$EVIDENCE/ci-ownership.json"
  say "issued CI ownership manifest: $EVIDENCE/ci-ownership.json"
}

cleanup() {
  check_name target-project "$TARGET"; [ "$TARGET" != online-shop-uat ] || die 'reserved target'; exists "$TARGET" || die 'target does not exist'
  vols=$(docker volume ls --filter "label=com.docker.compose.project=$TARGET" --format '{{.Name}}'); [ -n "$vols" ] || die 'no labelled target volume'
  say "cleanup project: $TARGET"; say "cleanup volumes: $vols"
  if [ "$CI_GENERATED" = true ]; then
    [ -f "$MANIFEST" ] || die 'CI run manifest missing'; [ -n "$NONCE" ] || die 'CI nonce missing'
    [ "$(jq -r .ciProject "$MANIFEST")" = "$TARGET" ] || die 'CI project provenance mismatch'; [ "$(jq -r .ciNonce "$MANIFEST")" = "$NONCE" ] || die 'CI nonce mismatch'
    for id in $(docker ps -aq --filter "label=com.docker.compose.project=$TARGET"); do [ "$(docker inspect -f '{{index .Config.Labels "online-shop.uat.ci-nonce"}}' "$id")" = "$NONCE" ] || die 'foreign container label'; done
    for vol in $vols; do [ "$(docker volume inspect -f '{{index .Labels "online-shop.uat.ci-nonce"}}' "$vol")" = "$NONCE" ] || die 'foreign volume label'; done
  else [ "$CONFIRM" = "$TARGET" ] || die 'typed confirmation mismatch'; fi
  UAT_RUN_OWNER="$TARGET" UAT_RUN_NONCE="${NONCE:-manual}" compose_hardened "$TARGET" down --volumes --remove-orphans
}

case "$ACTION" in
  preflight) check_name source-project "$SOURCE"; assert_target_new "$TARGET"; say 'preflight passed (credentials redacted)';;
  inventory) inventory;; backup) backup;; restore|recover) restore;; fresh-install) fresh_install;; upgrade|switch) upgrade;; verify) verify;; issue-ci-manifest) issue_ci_manifest;;
  compare-inventory) compare_inventory_files "$EXPECTED_INVENTORY" "$ACTUAL_INVENTORY";;
  stop) check_name target-project "$TARGET"; compose_hardened "$TARGET" down;; cleanup) cleanup;;
  *) die 'usage: uat-drill.sh preflight|inventory|backup|restore|fresh-install|upgrade|verify|recover|stop|issue-ci-manifest|cleanup ...';;
esac
