# Order Web

Local/controlled-UAT React client for the Order API. It is **not an authorization boundary and must not be publicly or production exposed**.

```bash
npm ci
npm run api:check
npm run typecheck
npm run lint
npm test
npm run test:a11y
npm run build
npx playwright install chromium
npm run playwright
```

Run the backend on `http://127.0.0.1:8080`, then start Vite without `VITE_ORDER_API_URL`. The checked-in Vite proxy forwards same-origin `/api` requests to the backend, avoiding any backend CORS/security change. If the backend uses another local port, set `ORDER_API_PROXY_TARGET` when starting Vite (for example `ORDER_API_PROXY_TARGET=http://127.0.0.1:18080 npm run dev`). `VITE_ORDER_CUSTOMER_ID` selects the controlled-UAT customer and defaults to `customer-1`.

`openapi/order-api.json` is the byte-for-byte `/v3/api-docs` export from the TASK-0024 candidate (provenance and SHA-256 are recorded in `openapi/PROVENANCE.md`). Generated operation/schema types are consumed by the API adapter. `api:check` verifies the pinned export checksum and regenerates the checked-in types, so backend-contract input drift or generated-output drift fails the gate.
