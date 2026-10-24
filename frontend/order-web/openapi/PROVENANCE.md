# OpenAPI provenance

- Backend baseline: `5c7c5ce528a4c1ac3853b7151cb0c38fc61419e5`
- Candidate branch: `feat/issue-35-order-v2` (TASK-0024)
- Source endpoint: `GET /v3/api-docs`
- Export file: `order-api.json` (raw response bytes; no normalization)
- SHA-256: `98c02540ed53cc5a001dae8b90ed195e19d2ceb879b0f454b6d7d03f1db5a0f7`
- Size: 11876 bytes

The API was started from the exact backend commit and the response body was saved directly. `npm run api:check` verifies this checksum before regenerating types.

## Catalog v2

- Source: `tests/contracts/catalog-api-v2.json`, Issue #31 approved contract
- Baseline: `502f468c48ab29fe6c050b61c289dbaac89f4f5e`
- SHA-256: `54acd4083be499fa9f19ada4639c8f2b80539d8b6a15e6a2a0e268b511c6f424`

The frontend copy is byte-identical to the authoritative repository contract.
