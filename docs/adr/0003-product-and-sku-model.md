# ADR 0003: Product and SKU catalog model

- Status: Accepted
- Date: 2026-10-23

## Context

The legacy product row combines merchandising identity, price, currency, and aggregate stock, while commercial variants require independently purchasable choices.

## Decision

A Product is the merchandising concept and a SKU is its sellable, priced, stocked variant. Typed attribute definitions and values may describe Products or select SKU variants. Categories and image metadata support browsing. `available_quantity` is an optimistic interim balance, not a reservation, ledger, or oversell guarantee.

Catalog roots are archived rather than hard-deleted. Orders keep snapshots and never acquire a runtime Catalog database foreign key. UUIDv4 remains the compatibility default until a UUIDv7 dependency is separately approved.

## Consequences

The v1 representation will be adapted from a default SKU during migration. Schema, migrations, domain behavior, and APIs are intentionally deferred to subsequent issues.
