#!/bin/sh
set -eu
catalog="http://127.0.0.1:${CATALOG_API_PORT:-8082}/api/v1/products"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
run_id="$(date +%s)-$$"
orders="http://127.0.0.1:${ORDER_API_PORT:-8081}/api/v1/customers/uat-$run_id/orders"
curl -fsS -H 'Content-Type: application/json' -d '{"name":"UAT Synthetic Tea","description":"TASK-0020 fixture","price":"12.30","currency":"NZD","stockQuantity":7}' "$catalog" >"$tmp/product.json"
pid="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/product.json")"
[ -n "$pid" ]; curl -fsS "$catalog/$pid" | grep -q '"price":"12.30"'; curl -fsS "$catalog?page=0&size=20" | grep -q "$pid"
status="$(curl -sS -o "$tmp/invalid-product.json" -w '%{http_code}' -H 'Content-Type: application/json' -d '{}' "$catalog")"
[ "$status" = 422 ]; grep -q '"code":"INVALID_PRODUCT"' "$tmp/invalid-product.json"
status="$(curl -sS -o "$tmp/missing-product.json" -w '%{http_code}' "$catalog/00000000-0000-0000-0000-000000000000")"
[ "$status" = 404 ]; grep -q '"code":"PRODUCT_NOT_FOUND"' "$tmp/missing-product.json"
payload='{"customerName":"UAT Synthetic","items":[{"productId":"product-uat","sku":"SKU-UAT","name":"Fixture","unitPrice":{"amount":10.00,"currency":"NZD"},"quantity":1}],"shippingAddress":{"recipient":"UAT Synthetic","line1":"1 Test Street","line2":"","city":"Auckland","region":"Auckland","postalCode":"1010","countryCode":"NZ","phone":""}}'
curl -fsS -D "$tmp/h" -H 'Content-Type: application/json' -H "Idempotency-Key: task-0020-smoke-$run_id" -d "$payload" "$orders" >"$tmp/order.json"
oid="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/order.json")"; [ -n "$oid" ]
curl -fsS "$orders/$oid" | grep -q '"status":"DRAFT"'; curl -fsS "$orders?page=0&size=20" | grep -q "$oid"
curl -fsS -H 'If-Match: "0"' -X POST "$orders/$oid/confirmation" | grep -q '"status":"CONFIRMED"'
cancel_orders="http://127.0.0.1:${ORDER_API_PORT:-8081}/api/v1/customers/uat-cancel-$run_id/orders"
curl -fsS -H 'Content-Type: application/json' -H "Idempotency-Key: task-0020-cancel-$run_id" -d "$payload" "$cancel_orders" >"$tmp/cancel-order.json"
cancel_id="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$tmp/cancel-order.json")"; [ -n "$cancel_id" ]
curl -fsS -H 'Content-Type: application/json' -H 'If-Match: "0"' -d '{"reason":"synthetic UAT cancellation"}' "$cancel_orders/$cancel_id/cancellation" | grep -q '"status":"CANCELLED"'
echo "UAT smoke passed: catalog create/list/detail and order create/list/detail/confirm/cancel."
