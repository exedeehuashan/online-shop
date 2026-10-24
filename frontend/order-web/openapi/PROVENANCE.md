# OpenAPI provenance

- Backend repository commit: `9f14c194dfc0ece7e94f9f09a0f8b629e16a7e2d`
- Source endpoint: `GET /v3/api-docs`
- Export file: `order-api.json` (raw response bytes; no normalization)
- SHA-256: `1d8f91bddbe6fdca9d3702c8aaba96747f69f793824f6da4985af11e971a43d3`
- Size: 8049 bytes

The API was started from the exact backend commit and the response body was saved directly. `npm run api:check` verifies this checksum before regenerating types.

## Catalog v2

- Source: `tests/contracts/catalog-api-v2.json`, Issue #31 approved contract
- Baseline: `502f468c48ab29fe6c050b61c289dbaac89f4f5e`
- SHA-256: `54acd4083be499fa9f19ada4639c8f2b80539d8b6a15e6a2a0e268b511c6f424`

The frontend copy is byte-identical to the authoritative repository contract.
