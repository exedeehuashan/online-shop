#!/bin/sh
set -eu
tmp="$(mktemp)"
order_api_environment="$(mktemp)"
trap 'rm -f "$tmp" "$order_api_environment"' EXIT
docker compose -f compose.yaml -f compose.order-security.yaml config > "$tmp"
for setting in ORDER_SECURITY_ENABLED ORDER_OIDC_JWKS_URI ORDER_OIDC_ISSUER ORDER_OIDC_AUDIENCE ORDER_IDENTITY_BASE_URL; do
  grep -q "${setting}:" "$tmp" || { echo "missing delivered Order security setting: $setting" >&2; exit 1; }
done
awk '
  $0 == "  order-api:" { in_order_api = 1; next }
  in_order_api && /^  [^ ]/ { exit }
  in_order_api && $0 == "    environment:" { in_environment = 1; next }
  in_order_api && in_environment && /^    [^ ]/ { exit }
  in_order_api && in_environment { print }
' "$tmp" > "$order_api_environment"

if ! grep -q '^      ORDER_SECURITY_ENABLED: "true"$' "$order_api_environment"; then
  echo 'order-api environment must set ORDER_SECURITY_ENABLED to "true"' >&2
  echo 'resolved order-api environment:' >&2
  if [ -s "$order_api_environment" ]; then
    sed 's/^/  /' "$order_api_environment" >&2
  else
    echo '  <missing order-api service or environment mapping>' >&2
  fi
  exit 1
fi
grep -q 'host_ip: 127.0.0.1' "$tmp"
grep -q 'ORDER_SECURITY_ENABLED' docs/order-security.md
if grep -Eq 'BEGIN (RSA |)PRIVATE KEY|access_token:[[:space:]]+[^$]' compose.order-security.yaml docs/order-security.md; then
  echo 'static authentication secret found in delivered profile' >&2; exit 1
fi
echo 'Authenticated Order delivered-stack configuration contract passed'
