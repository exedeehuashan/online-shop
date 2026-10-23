# ADR 0001: Bounded-context and database ownership

- Status: Accepted
- Date: 2026-10-23
- Baseline: `8a626c6aa5f9422762f4e2097162cf7f3eb4a354`

## Context

Order and Catalog already form separate hexagonal modules and own separate PostgreSQL databases. Identity/Authorization needs the same isolation without weakening existing snapshot behavior.

## Decision

Retain a modular monolith with independently owned Order, Catalog, and Identity/Authorization contexts. Identity uses `identity-domain`, `identity-application`, `identity-adapters-persistence`, and `identity-api` modules and a separately owned database.

Each context may access only its own persistence ports and database. Cross-context database joins, table reads, foreign keys, shared ORM entities, and direct repository dependencies are prohibited. Context references are opaque logical identifiers. Interactions cross application/API ports or asynchronous contracts. Order continues to own immutable catalog snapshots.

## Consequences

Database integrity is local to an owner; cross-context consistency is explicit and may be eventual. Module and dependency checks fail on forbidden identity dependencies. A gateway/BFF or context merge requires a later ADR and approval.
