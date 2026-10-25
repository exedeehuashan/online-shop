#!/bin/sh
set -eu
umask 077

die() { printf 'ERROR: %s\n' "$*" >&2; exit 2; }
say() { printf '%s\n' "$*" >&2; }
valid_name() { printf '%s' "$1" | grep -Eq '^[a-z][a-z0-9-]{5,47}$'; }
check_name() { [ -n "$2" ] || die "$1 is required"; valid_name "$2" || die "unsafe $1"; }
compose() { docker compose -p "$1" -f "$ROOT/compose.yaml" "$@"; }
container() { compose "$1" ps -q postgres; }
exists() { docker ps -a --filter "label=com.docker.compose.project=$1" --format '{{.ID}}' | grep -q . || docker volume ls --filter "label=com.docker.compose.project=$1" -q | grep -q .; }
assert_target_new() { check_name target-project "$1"; [ "$1" != online-shop-uat ] || die 'reserved target'; ! exists "$1" || die 'target already exists'; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"; }
db_exec() { docker exec "$(container "$1")" psql -XAt -v ON_ERROR_STOP=1 -U postgres -d "$2" -c "$3"; }
critical_tables() {
  case "$1" in
    orders) printf '%s\n' orders order_items order_idempotency outbox_events order_audit_events;;
    catalog) printf '%s\n' products product product_attribute product_attribute_value product_attribute_assignment product_sku product_sku_attribute_value category product_category product_image catalog_legacy_migration_issue catalog_legacy_reconciliation;;
    identity) printf '%s\n' sys_user sys_role sys_permission sys_user_role sys_role_permission security_audit_event;;
    *) die "no critical-table allow-list for database: $1";;
  esac
}

# PostgreSQL jsonb gives a deterministic key order and escaping without emitting row values.
# C collation fixes row ordering; PostgreSQL 16's built-in sha256 hashes the canonical stream.
database_inventory() {
  project=$1 db=$2 output=$3 critical='[]'
  for table in $(critical_tables "$db"); do
    present=$(db_exec "$project" "$db" "select to_regclass('public.$table') is not null;")
    [ "$present" = t ] || die "missing critical table: $db.public.$table"
    row=$(db_exec "$project" "$db" "with canonical as (select to_jsonb(t)::text as value from public.$table t), summary as (select count(*)::bigint as row_count, coalesce(string_agg(value, E'\\n' order by value collate \"C\"),'') as value from canonical) select json_build_object('name','$table','rowCount',row_count,'checksumAlgorithm','postgres-jsonb-c-v1+sha256','checksum',encode(sha256(convert_to(value,'UTF8')),'hex')) from summary;")
    critical=$(jq -cn --argjson items "$critical" --argjson item "$row" '$items + [$item]')
  done
  base=$(db_exec "$project" "$db" "select json_build_object('database',current_database(),'serverVersion',version(),'flyway',coalesce((select json_agg(x order by installed_rank) from (select installed_rank,version,description,success from flyway_schema_history) x),'[]'::json),'tables',coalesce((select json_agg(json_build_object('name',tablename) order by tablename) from pg_tables where schemaname='public'),'[]'::json));")
  jq -cn --argjson base "$base" --argjson critical "$critical" '$base + {criticalTables:$critical}' >"$output.tmp"
  mv "$output.tmp" "$output"
}

compare_inventory_files() {
  expected=$1 actual=$2
  [ -f "$expected" ] || die "expected inventory missing: $expected"
  [ -f "$actual" ] || die "actual inventory missing: $actual"
  expected_semantic=$(jq -cS '.criticalTables | if type == "array" and length > 0 then . else error("missing criticalTables") end' "$expected") || die 'invalid expected critical-table inventory'
  actual_semantic=$(jq -cS '.criticalTables | if type == "array" and length > 0 then . else error("missing criticalTables") end' "$actual") || die 'invalid actual critical-table inventory'
  [ "$expected_semantic" = "$actual_semantic" ] || die "critical-table semantic mismatch: $(jq -r .database "$expected")"
}

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
ACTION=${1:-}; shift || true
SOURCE= TARGET= EVIDENCE= MANIFEST= CONFIRM= EXPECTED_INVENTORY= ACTUAL_INVENTORY= CI_GENERATED=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --source-project) SOURCE=${2:-}; shift 2;; --target-project) TARGET=${2:-}; shift 2;;
    --evidence-dir) EVIDENCE=${2:-}; shift 2;; --manifest) MANIFEST=${2:-}; shift 2;;
    --expected-inventory) EXPECTED_INVENTORY=${2:-}; shift 2;; --actual-inventory) ACTUAL_INVENTORY=${2:-}; shift 2;;
    --confirm) CONFIRM=${2:-}; shift 2;; --ci-generated) CI_GENERATED=true; shift;;
    *) die "unknown argument: $1";; esac
done
[ -n "$ACTION" ] || die 'action is required'
for t in docker jq sha256sum git; do need "$t"; done

inventory() {
  check_name source-project "$SOURCE"; exists "$SOURCE" || die 'source does not exist'
  [ -n "$EVIDENCE" ] || die 'evidence-dir is required'; mkdir -p "$EVIDENCE"
  cfg=$(mktemp "$EVIDENCE/.compose.XXXXXX"); compose "$SOURCE" config >"$cfg"
  cid=$(container "$SOURCE"); [ -n "$cid" ] || die 'source postgres is absent'
  state=$(docker inspect -f '{{.State.Health.Status}}' "$cid"); [ "$state" = healthy ] || die 'source postgres is not healthy'
  for db in orders catalog identity; do database_inventory "$SOURCE" "$db" "$EVIDENCE/$db-inventory.json"; done
  jq -n --arg schema 'online-shop-uat/v1' --arg source "$SOURCE" --arg git "$(git -C "$ROOT" rev-parse HEAD)" --arg created "$(date -u +%FT%TZ)" --arg digest "$(sha256sum "$cfg" | awk '{print $1}')" --arg pg "$(docker exec "$cid" postgres --version)" --arg volume "$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' "$cid")" '{schemaVersion:$schema,sourceProject:$source,gitSha:$git,createdAt:$created,composeDigest:$digest,postgresVersion:$pg,sourceVolume:$volume,backupComplete:false}' >"$EVIDENCE/manifest.json.tmp"
  mv "$EVIDENCE/manifest.json.tmp" "$EVIDENCE/manifest.json"; rm -f "$cfg"
}

backup() {
  inventory
  cid=$(container "$SOURCE")
  cat >"$EVIDENCE/roles.sql.tmp" <<'EOF'
CREATE ROLE order_app LOGIN; CREATE ROLE catalog_app LOGIN; CREATE ROLE identity_app LOGIN;
EOF
  mv "$EVIDENCE/roles.sql.tmp" "$EVIDENCE/roles.sql"
  for db in orders catalog identity; do docker exec "$cid" pg_dump -U postgres -Fc --no-owner --no-acl "$db" >"$EVIDENCE/$db.dump.tmp"; mv "$EVIDENCE/$db.dump.tmp" "$EVIDENCE/$db.dump"; docker exec -i "$cid" pg_restore --list <"$EVIDENCE/$db.dump" >/dev/null; done
  (cd "$EVIDENCE" && sha256sum roles.sql *.dump *-inventory.json >checksums.sha256.tmp && mv checksums.sha256.tmp checksums.sha256)
  jq '.backupComplete=true | .sealedAt=now|todateiso8601' "$EVIDENCE/manifest.json" >"$EVIDENCE/manifest.json.sealed" && mv "$EVIDENCE/manifest.json.sealed" "$EVIDENCE/manifest.json"
  say "sealed backup: $EVIDENCE/manifest.json"
}

verify_manifest() {
  [ -f "$MANIFEST" ] || die 'manifest missing'; dir=$(CDPATH= cd -- "$(dirname "$MANIFEST")" && pwd)
  [ "$(jq -r .schemaVersion "$MANIFEST")" = online-shop-uat/v1 ] || die 'unsupported manifest'
  [ "$(jq -r .backupComplete "$MANIFEST")" = true ] || die 'backup is not sealed'
  (cd "$dir" && sha256sum -c checksums.sha256 >/dev/null) || die 'backup checksum mismatch'
  EVIDENCE=$dir
}

verify_semantics() {
  project=$1
  for db in orders catalog identity; do
    actual=$(mktemp "$EVIDENCE/.$db-verify.XXXXXX")
    database_inventory "$project" "$db" "$actual"
    compare_inventory_files "$EVIDENCE/$db-inventory.json" "$actual"
    rm -f "$actual"
  done
}

restore() {
  assert_target_new "$TARGET"; verify_manifest
  compose "$TARGET" up -d --build postgres
  cid=$(container "$TARGET"); i=0; until [ "$(docker inspect -f '{{.State.Health.Status}}' "$cid")" = healthy ]; do i=$((i+1)); [ "$i" -lt 60 ] || die 'target postgres not healthy'; sleep 1; done
  for db in orders catalog identity; do docker exec "$cid" dropdb -U postgres "$db"; done
  for db in orders catalog identity; do case "$db" in orders) owner=order_app;; *) owner=${db}_app;; esac; docker exec "$cid" createdb -U postgres -O "$owner" "$db"; docker exec -i "$cid" pg_restore -U postgres -d "$db" --no-owner --no-acl <"$EVIDENCE/$db.dump"; done
  verify_semantics "$TARGET"
  say "restored isolated target: $TARGET"
}

verify() {
  check_name target-project "$TARGET"; exists "$TARGET" || die 'target does not exist'; verify_manifest
  cid=$(container "$TARGET"); [ -n "$cid" ] || die 'target postgres is absent'
  [ "$(docker inspect -f '{{.State.Health.Status}}' "$cid")" = healthy ] || die 'target postgres is not healthy'
  verify_semantics "$TARGET"
  say "verified critical-table semantics: $TARGET"
}

cleanup() {
  check_name target-project "$TARGET"; [ "$TARGET" != online-shop-uat ] || die 'reserved target'
  exists "$TARGET" || die 'target does not exist'
  vols=$(docker volume ls --filter "label=com.docker.compose.project=$TARGET" --format '{{.Name}}')
  [ -n "$vols" ] || die 'no labelled target volume'
  say "cleanup project: $TARGET"; say "cleanup volumes: $vols"
  if [ "$CI_GENERATED" = true ]; then case "$TARGET" in ci-task26-*) :;; *) die 'CI target was not generated by this workflow';; esac; else [ "$CONFIRM" = "$TARGET" ] || die 'typed confirmation mismatch'; fi
  compose "$TARGET" down --volumes --remove-orphans
}

case "$ACTION" in
 preflight) check_name source-project "$SOURCE"; assert_target_new "$TARGET"; say 'preflight passed (credentials redacted)';;
 inventory) inventory;; backup) backup;; restore|recover) restore;;
 verify) verify;;
 compare-inventory) compare_inventory_files "$EXPECTED_INVENTORY" "$ACTUAL_INVENTORY";;
 stop) check_name target-project "$TARGET"; compose "$TARGET" down;; cleanup) cleanup;;
 *) die 'usage: uat-drill.sh preflight|inventory|backup|restore|verify|recover|stop|cleanup ...';;
esac
