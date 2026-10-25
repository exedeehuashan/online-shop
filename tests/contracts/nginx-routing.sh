#!/bin/sh
set -eu

root_dir="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
suffix="$$"
network="catalog-routing-${suffix}"
catalog="catalog-marker-${suffix}"
order="order-marker-${suffix}"
identity="identity-marker-${suffix}"
auth="auth-marker-${suffix}"
gateway="catalog-gateway-${suffix}"

cleanup() {
  docker rm -f "$gateway" "$catalog" "$order" "$identity" "$auth" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker network create "$network" >/dev/null
docker run -d --name "$catalog" --network "$network" --network-alias catalog-api \
  -v "$root_dir/tests/contracts/fixtures/catalog-marker.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine >/dev/null
docker run -d --name "$order" --network "$network" --network-alias order-api \
  -v "$root_dir/tests/contracts/fixtures/order-marker.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine >/dev/null
docker run -d --name "$identity" --network "$network" --network-alias identity-api \
  -v "$root_dir/tests/contracts/fixtures/identity-marker.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine >/dev/null
docker run -d --name "$auth" --network "$network" --network-alias uat-auth \
  -v "$root_dir/tests/contracts/fixtures/auth-marker.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine >/dev/null
docker run -d --name "$gateway" --network "$network" -p 127.0.0.1::8080 \
  -v "$root_dir/docker/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine >/dev/null

port="$(docker port "$gateway" 8080/tcp | sed 's/.*://')"
base="http://127.0.0.1:${port}"
attempt=0
until curl -fsS "$base/health" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  test "$attempt" -lt 30
  sleep 1
done

assert_upstream() {
  method="$1"
  path="$2"
  expected="$3"
  actual="$(curl -fsSI -X "$method" "$base$path" | tr -d '\r' | awk -F ': ' 'tolower($1)=="x-upstream" {print $2}')"
  test "$actual" = "$expected" || { echo "$method $path routed to '$actual', expected '$expected'" >&2; exit 1; }
}

assert_upstream GET /api/v2/products catalog
assert_upstream GET /api/v2/products/00000000-0000-0000-0000-000000000001 catalog
assert_upstream GET /api/v2/products/by-slug/green-tea catalog
assert_upstream GET /api/v2/products/unknown-child catalog
assert_upstream HEAD /api/v2/products catalog
test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v2/products-extra")" = 404
test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v2/productsX")" = 404
assert_upstream GET /api/v1/products catalog
assert_upstream GET /api/v1/customers/customer/orders order
assert_upstream GET /api/v2/me/orders order
assert_upstream GET /api/v2/me/orders/00000000-0000-0000-0000-000000000001 order
assert_upstream GET /api/v2/admin/orders order
assert_upstream GET /api/v2/admin/orders/00000000-0000-0000-0000-000000000001 order
assert_upstream GET /api/v1/me identity
assert_upstream GET /api/v1/admin/users identity
assert_upstream GET /api/v1/admin/users/00000000-0000-0000-0000-000000000001/roles identity
assert_upstream GET /api/v1/admin/roles identity
assert_upstream GET /api/v1/admin/roles/00000000-0000-0000-0000-000000000001/permissions identity
assert_upstream GET /api/v1/admin/permissions identity
assert_upstream GET /api/uat/token auth
test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v2/orders")" = 404
test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v1/admin/unexpected")" = 404
echo 'Functional nginx routing assertions passed'
