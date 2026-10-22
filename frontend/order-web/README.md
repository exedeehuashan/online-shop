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

Set `VITE_ORDER_API_URL` for a local API origin and `VITE_ORDER_CUSTOMER_ID` for the controlled-UAT customer. Defaults are same-origin and `customer-1`.

`openapi/order-api.json` is the deterministic `/v3/api-docs` export provenance-pinned to backend commit `9f14c194dfc0ece7e94f9f09a0f8b629e16a7e2d`. `api:check` regenerates the checked-in client types and fails on drift.
