# ADR 0004: API versioning and compatibility

- Status: Accepted
- Date: 2026-10-23

## Context

Catalog and Order v1 clients must continue working while product identity and authenticated ownership evolve.

## Decision

Current `/api/v1/products` and `/api/v1/customers/{customerId}/orders` representations are frozen. Additive compatible changes may remain within a major version; breaking representation or identity changes require a new major path. v1 remains supported for at least one released version after its replacement and may be removed only with usage evidence and separate approval.

Checked-in OpenAPI documents are the contract source and carry byte-level SHA-256 provenance. Generated client types and checksum manifests are drift checked. APIs use RFC 9457 `application/problem+json`, stable machine codes, and propagated or generated correlation IDs. Mutations use quoted ETags with `If-Match`; pagination is deterministic and bounded as specified in [API conventions](../conventions/api.md).

## Consequences

Existing problem codes, idempotency behavior, and quoted `If-Match` semantics remain unchanged. This ADR introduces no endpoint or behavior change.
