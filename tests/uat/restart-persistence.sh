#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
DRILL="$ROOT/scripts/uat-drill.sh"
BASE="$ROOT/compose.yaml"
SECURITY="$ROOT/compose.order-security.yaml"
HARDENED="$ROOT/compose.uat-hardened.yaml"
PROJECT=${1:-}
printf '%s' "$PROJECT" | grep -Eq '^[a-z][a-z0-9-]{5,47}$' || {
  echo 'usage: restart-persistence.sh <existing-disposable-project>' >&2; exit 2
}
[ "$PROJECT" != online-shop-uat ] || { echo 'refusing reserved project' >&2; exit 2; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

inventory() {
  phase=$1
  "$DRILL" inventory --source-project "$PROJECT" --evidence-dir "$tmp/$phase" >/dev/null
}

wait_finished() {
  fixture=$(docker compose -p "$PROJECT" -f "$BASE" -f "$SECURITY" -f "$HARDENED" ps -aq uat-rbac-fixture)
  [ -n "$fixture" ] || { echo 'RBAC fixture absent after restart' >&2; exit 1; }
  i=0
  while [ "$(docker inspect -f '{{.State.Status}}' "$fixture")" != exited ]; do
    i=$((i + 1)); [ "$i" -lt 120 ] || { echo 'RBAC fixture did not finish after restart' >&2; exit 1; }
    sleep 1
  done
  [ "$(docker inspect -f '{{.State.ExitCode}}' "$fixture")" = 0 ] || { echo 'RBAC fixture failed after restart' >&2; exit 1; }
}

inventory before
# This is the documented whole-stack restart, resolved with the same three Compose
# files that created the hardened target. It deliberately includes completed jobs.
docker compose -p "$PROJECT" -f "$BASE" -f "$SECURITY" -f "$HARDENED" restart
wait_finished
inventory after

for db in orders catalog identity; do
  case "$db" in
    orders) append_only=order_audit_events;;
    identity) append_only=security_audit_event;;
    *) append_only=__none__;;
  esac
  before_count=$(jq -r --arg table "$append_only" '.criticalTables[]|select(.name==$table)|.rowCount' "$tmp/before/$db-inventory.json")
  after_count=$(jq -r --arg table "$append_only" '.criticalTables[]|select(.name==$table)|.rowCount' "$tmp/after/$db-inventory.json")
  if [ -n "$before_count" ]; then
    [ "$after_count" -ge "$before_count" ] || { echo "$db append-only audit count decreased" >&2; exit 1; }
    jq --arg table "$append_only" '.criticalTables |= map(select(.name != $table))' "$tmp/before/$db-inventory.json" >"$tmp/before/$db-stable.json"
    jq --arg table "$append_only" '.criticalTables |= map(select(.name != $table))' "$tmp/after/$db-inventory.json" >"$tmp/after/$db-stable.json"
  else
    cp "$tmp/before/$db-inventory.json" "$tmp/before/$db-stable.json"
    cp "$tmp/after/$db-inventory.json" "$tmp/after/$db-stable.json"
  fi
  "$DRILL" compare-inventory \
    --expected-inventory "$tmp/before/$db-stable.json" \
    --actual-inventory "$tmp/after/$db-stable.json"
done

echo "stable critical-table checksums survived whole-stack restart: $PROJECT"
