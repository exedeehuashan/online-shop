#!/bin/sh
set -eu
node tests/contracts/verify.mjs
cd frontend/order-web
npm ci
npm run api:check
