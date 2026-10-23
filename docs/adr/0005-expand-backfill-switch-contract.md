# ADR 0005: Expand/backfill/switch/contract migrations

- Status: Accepted
- Date: 2026-10-23

## Context

Catalog and identity evolution must remain rollback-safe and must not destroy the existing UAT database.

## Decision

Database evolution follows expand, preflight, restartable backfill, verify, switch, observe, and contract phases. Expand changes remain compatible with the previous application. Backfill reports invalid data and proves row counts/checksums before switching. Legacy storage remains available and read-only through the compatibility window.

Contract/drop work is a later release requiring usage evidence, a verified backup/restore, and new human approval. Lossy undo SQL is prohibited. Migration testing uses a restored, uniquely named disposable volume with loopback-only ports, never the existing named UAT volume first.

## Consequences

Rollback before switch redeploys the previous application against the compatible expanded schema. Rollback after switch stops writers and restores the verified backup into a new isolated volume. No schema or deployment change is made by this ADR.
