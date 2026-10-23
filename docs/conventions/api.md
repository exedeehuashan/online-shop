# API and contract conventions

These conventions apply to new APIs. Existing v1 behavior is frozen unless an issue explicitly authorizes a compatible correction.

## Problems and correlation

- Errors use RFC 9457 and `Content-Type: application/problem+json`.
- `type` is a stable documentation URI; `code` is the stable uppercase snake-case machine identifier. Clients do not branch on localized `title` or `detail`.
- Accept a valid `X-Correlation-ID` of 1–128 visible ASCII characters; otherwise generate an opaque value. Return it on every response and attach it to problem details and audit events. Never place credentials or PII in it.
- Validation problems use a deterministic `violations` array without echoing secrets.

## Concurrency and caching

- Mutable resources expose a strong quoted ETag derived from their opaque identity and optimistic version, for example `"product-<id>-v3"`.
- Mutations of existing resources require a quoted `If-Match`; missing, malformed, or stale preconditions use documented RFC 9457 responses. Wildcard preconditions are not accepted unless a contract explicitly allows them.

## Pagination, filtering, and names

- Default page size is 20 and maximum is 100. Negative positions, over-limit sizes, unknown sort fields, and unknown filters are rejected rather than silently coerced.
- Results have a unique deterministic tie-breaker (`id`) and responses contain stable page/cursor metadata. Cursor contents are opaque to clients.
- External identifiers are opaque. Database and JSON field names follow the approved contract; database names are lower snake case. Codes are trimmed ASCII lowercase using `resource:action`; SKU and ISO currency values are normalized uppercase. Email canonicalization is Unicode-aware application logic, never locale-dependent database lowercasing.

## OpenAPI provenance and versioning

- Checked-in OpenAPI is the contract source. Every document has a recorded source, baseline commit, SHA-256, and export method in `tests/contracts/openapi-checksums.json` or its colocated provenance record.
- `node scripts/check-foundation.mjs`, `make test-contracts`, and frontend `npm run api:check` reject unexplained byte or generated-type drift.
- Compatible additions may stay within a major version. Breaking changes use a new major path. v1 replacements retain a minimum one-release compatibility window and require usage evidence plus approval before removal.
