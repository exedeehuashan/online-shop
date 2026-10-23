# Security, identity, and audit conventions

## Boundaries

- Authentication is external OIDC; authorization data is local. No identity table or application log stores passwords, tokens, secrets, private keys, security answers, or raw authorization headers.
- Permission codes are immutable normalized `resource:action` constants. Authorization is deny by default and object scope is enforced by the owning application.
- Each context has a separate least-privilege database owner/runtime role. Cross-context joins, reads, and foreign keys are forbidden.

## Actor and audit fields

- Mutable roots use UTC `created_at`, `created_by`, `updated_at`, `updated_by`, and non-negative optimistic `version`. Actor UUIDs are logical references, never cross-database foreign keys.
- System/bootstrap actors use an explicit reserved identity; null means genuinely unavailable, not a shared administrator.
- Security audit events include actor, target, action, UTC timestamp, correlation ID, outcome, and safe reason code. They exclude bearer tokens, cookies, credentials, request bodies, DB URLs, and sensitive PII.

## Normalization and lifecycle

- Normalize and validate at the application boundary before uniqueness checks. Role and permission codes are trimmed lowercase ASCII; SKUs and ISO 4217 currencies are uppercase; user identity is the exact immutable `(issuer, subject)` pair.
- Archive/disable principals and masters rather than deleting audit history. Role grants honor expiry and inactive user/role/permission state.
