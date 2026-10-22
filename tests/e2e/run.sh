#!/bin/sh
set -eu
cd frontend/order-web
npm ci
npx playwright install chromium
npm run playwright
