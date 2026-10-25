#!/bin/sh
set -eu
S=./scripts/uat-drill.sh
expect_fail() { if "$@" >/tmp/task26-test.out 2>&1; then echo "unexpected success: $*" >&2; exit 1; fi; }
expect_fail "$S" preflight
expect_fail "$S" preflight --source-project valid-source --target-project online-shop-uat
expect_fail "$S" preflight --source-project valid-source --target-project '../escape'
expect_fail "$S" cleanup --target-project online-shop-uat --confirm online-shop-uat
expect_fail "$S" restore --target-project valid-target --manifest /missing
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
cat >"$tmp/expected.json" <<'EOF'
{"database":"orders","criticalTables":[{"name":"orders","rowCount":2,"checksumAlgorithm":"postgres-jsonb-c-v1+sha256","checksum":"abc"}]}
EOF
cat >"$tmp/stable.json" <<'EOF'
{"criticalTables":[{"checksum":"abc","checksumAlgorithm":"postgres-jsonb-c-v1+sha256","rowCount":2,"name":"orders"}],"database":"orders"}
EOF
"$S" compare-inventory --expected-inventory "$tmp/expected.json" --actual-inventory "$tmp/stable.json"
sed 's/"abc"/"def"/' "$tmp/stable.json" >"$tmp/mismatch.json"
expect_fail "$S" compare-inventory --expected-inventory "$tmp/expected.json" --actual-inventory "$tmp/mismatch.json"
printf '{"database":"orders","criticalTables":[]}\n' >"$tmp/missing.json"
expect_fail "$S" compare-inventory --expected-inventory "$tmp/expected.json" --actual-inventory "$tmp/missing.json"
grep -q 'order_audit_events' "$S"
grep -q 'catalog_legacy_reconciliation' "$S"
grep -q 'security_audit_event' "$S"
grep -q 'order by value collate' "$S"
grep -q -- '--volumes' "$S"
grep -q 'down;;' "$S"

# Executable regression for D1: the project is consumed by the wrapper and is not
# forwarded as an extra Docker Compose argument.
mkdir "$tmp/bin"
cat >"$tmp/bin/docker" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >>"$TASK26_DOCKER_LOG"
exit 0
EOF
chmod +x "$tmp/bin/docker"
TASK26_DOCKER_LOG="$tmp/docker.log" PATH="$tmp/bin:$PATH" "$S" stop --target-project valid-target
grep -q 'compose -p valid-target -f .*/compose.yaml -f .*/compose.order-security.yaml -f .*/compose.uat-hardened.yaml down' "$tmp/docker.log"
! grep -q 'compose -p valid-target .* valid-target down' "$tmp/docker.log"

expect_fail "$S" issue-ci-manifest --target-project valid-target --evidence-dir "$tmp/ci" --nonce 1234567890123456
expect_fail "$S" issue-ci-manifest --target-project ci-task26-safe --evidence-dir "$tmp/ci" --nonce short
echo 'drill safety tests passed'
