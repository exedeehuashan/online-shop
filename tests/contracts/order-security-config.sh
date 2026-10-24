#!/bin/sh
set -eu
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
docker compose -f compose.yaml -f compose.order-security.yaml config > "$tmp"
for setting in ORDER_SECURITY_ENABLED ORDER_OIDC_JWKS_URI ORDER_OIDC_ISSUER ORDER_OIDC_AUDIENCE ORDER_IDENTITY_BASE_URL; do
  grep -q "${setting}:" "$tmp" || { echo "missing delivered Order security setting: $setting" >&2; exit 1; }
done
grep -A20 '^  order-api:' "$tmp" | grep -q 'ORDER_SECURITY_ENABLED: "true"'
grep -q 'host_ip: 127.0.0.1' "$tmp"
grep -q 'ORDER_SECURITY_ENABLED' docs/order-security.md
if grep -Eq 'BEGIN (RSA |)PRIVATE KEY|access_token:[[:space:]]+[^$]' compose.order-security.yaml docs/order-security.md; then
  echo 'static authentication secret found in delivered profile' >&2; exit 1
fi
echo 'Authenticated Order delivered-stack configuration contract passed'
