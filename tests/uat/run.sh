#!/bin/sh
set -eu
catalog="http://127.0.0.1:${CATALOG_API_PORT:-8082}/api/v1/products"
orders="http://127.0.0.1:${ORDER_API_PORT:-8081}/api/v1/customers/uat-customer/orders"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
curl -fsS -H 'Content-Type: application/json' -d '{"name":"UAT Synthetic Tea","description":"TASK-0020 fixture","price":"12.30","currency":"NZD","stockQuantity":7}' "$catalog" >"$tmp/product.json"
pid="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/product.json")"
[ -n "$pid" ]; curl -fsS "$catalog/$pid" | grep -q '"price":"12.30"'; curl -fsS "$catalog?page=0&size=20" | grep -q "$pid"
payload='{"customerName":"UAT Synthetic","items":[{"productId":"product-uat","sku":"SKU-UAT","name":"Fixture","unitPrice":{"amount":10.00,"currency":"NZD"},"quantity":1}],"shippingAddress":{"recipient":"UAT Synthetic","line1":"1 Test Street","line2":"","city":"Auckland","region":"Auckland","postalCode":"1010","countryCode":"NZ","phone":""}}'
curl -fsS -D "$tmp/h" -H 'Content-Type: application/json' -H 'Idempotency-Key: task-0020-smoke' -d "$payload" "$orders" >"$tmp/order.json"
oid="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/order.json")"; [ -n "$oid" ]
curl -fsS "$orders/$oid" | grep -q '"status":"DRAFT"'; curl -fsS "$orders?page=0&size=20" | grep -q "$oid"
curl -fsS -H 'If-Match: "0"' -X POST "$orders/$oid/confirmation" | grep -q '"status":"CONFIRMED"'
cancel_orders="http://127.0.0.1:${ORDER_API_PORT:-8081}/api/v1/customers/uat-cancel/orders"
curl -fsS -H 'Content-Type: application/json' -H 'Idempotency-Key: task-0020-cancel' -d "$payload" "$cancel_orders" >"$tmp/cancel-order.json"
cancel_id="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/cancel-order.json")"; [ -n "$cancel_id" ]
curl -fsS -H 'Content-Type: application/json' -H 'If-Match: "0"' -d '{"reason":"synthetic UAT cancellation"}' "$cancel_orders/$cancel_id/cancellation" | grep -q '"status":"CANCELLED"'
echo "UAT smoke passed: catalog create/list/detail and order create/list/detail/confirm/cancel."
