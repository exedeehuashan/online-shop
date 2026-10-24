#!/bin/sh
set -eu
order="http://127.0.0.1:${ORDER_API_PORT:-8081}/api/v2/me/orders"
issuer="http://127.0.0.1:${UAT_AUTH_PORT:-8083}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

status="$(curl -sS -o "$tmp/unauthenticated.json" -w '%{http_code}' "$order")"
[ "$status" = 401 ]; grep -q '"code":"AUTHENTICATION_REQUIRED"' "$tmp/unauthenticated.json"
curl -fsS "$issuer/token" > "$tmp/token.json"
token="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$tmp/token.json")"
[ -n "$token" ]
auth="Authorization: Bearer $token"
payload='{"customerName":"UAT Synthetic","items":[{"productId":"product-uat","sku":"SKU-UAT","name":"Fixture","unitPrice":{"amount":10.00,"currency":"NZD"},"quantity":1}],"shippingAddress":{"recipient":"UAT Synthetic","line1":"1 Test Street","city":"Auckland","postalCode":"1010","countryCode":"NZ"}}'
key="task-0024-v2-$(date +%s)-$$"
curl -fsS -D "$tmp/create.headers" -H "$auth" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" -d "$payload" "$order" > "$tmp/order.json"
id="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/order.json")"; [ -n "$id" ]
grep -qi '^ETag: "0"' "$tmp/create.headers"
curl -fsS -H "$auth" "$order?page=0&size=20" | grep -q "$id"
curl -fsS -D "$tmp/detail.headers" -H "$auth" "$order/$id" | grep -q '"status":"DRAFT"'
curl -fsS -H "$auth" -H 'If-Match: "0"' -X POST "$order/$id/confirmation" | grep -q '"status":"CONFIRMED"'
curl -fsS -H "$auth" -H 'Content-Type: application/json' -H 'If-Match: "1"' -d '{"reason":"synthetic UAT cancellation"}' "$order/$id/cancellation" | grep -q '"status":"CANCELLED"'
echo "Authenticated Order v2 UAT passed: 401 default-denial and create/list/detail/confirm/cancel."
