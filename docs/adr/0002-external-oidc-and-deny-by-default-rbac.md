# ADR 0002: External OIDC and deny-by-default RBAC

- Status: Accepted
- Date: 2026-10-23

## Context

The application needs authentication and authorization while the approved identity schema is not a credential store.

## Decision

Authentication is an external OpenID Connect/OAuth 2.1 provider boundary. APIs will validate issuer, audience, signature, validity times, and accepted algorithms before resolving immutable `(issuer, subject)` to an active application user. Provider selection and provisioning are deferred.

Authorization is deny by default. Named, immutable `resource:action` permission codes are granted through active, unexpired roles; there are no wildcards, role inheritance, or implicit grants. Object scope remains an application check. UI visibility never substitutes for API enforcement.

Identity tables must never store passwords, password hashes, access or refresh tokens, client secrets, private keys, security answers, or other credential material. Frontends must not persist bearer tokens in local storage.

## Consequences

An invalid or absent token is a 401; an authenticated principal lacking an active local user or permission is a 403. Authorization changes require security audit records. Runtime enforcement and provider configuration are later issues.
