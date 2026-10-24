#!/bin/sh
set -eu
node scripts/check-foundation.mjs
node tests/contracts/verify.mjs
./tests/contracts/order-security-config.sh
./tests/contracts/nginx-routing.sh
cd frontend/order-web
npm ci
npm run api:check
