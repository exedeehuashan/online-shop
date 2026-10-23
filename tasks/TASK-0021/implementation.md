# TASK-0021 Implementation Evidence

## Issue #29 — catalog expand schema and legacy backfill

- **Branch:** `feat/issue-29-catalog-schema`
- **Approved task baseline:** `8a626c6aa5f9422762f4e2097162cf7f3eb4a354`
- **Foundation dependency/base:** `813a28e0e4ec4f2ef73e67f0c1677be41ae6e82a` (merged PR #45)
- **Scope:** GitHub Issue #29 only. No catalog domain switch, v2 HTTP API, UI, inventory ledger, legacy contract deletion, deployment, or production/UAT mutation is included.

### Delivered

- Added an additive Flyway expand migration while preserving the existing `products` table and its v1 persistence contract.
- Added normalized `product`, `product_sku`, typed attribute definition/value and assignment tables, deterministic variant signatures, categories, product-category assignment, and product/SKU-scoped images.
- Added lifecycle, timestamp, audit actor, optimistic version, normalized uniqueness, money/currency/quantity, typed-value, default-SKU, primary-image, URL, and variant-combination constraints.
- Added explicit `CASCADE`, `RESTRICT`, and scoped `SET NULL` foreign-key actions. No cross-context foreign key or query was introduced.
- Added a deterministic legacy preflight/backfill procedure with bounded batches, an advisory transaction lock, stable UUID/slug/SKU/default-variant mapping, invalid-row issue reporting, and count/checksum reconciliation.
- Added a separate Flyway backfill migration. Rerunning the procedure is conflict-safe and reconciliation detects drift rather than overwriting expanded data.
- Reconciliation serializes each row as a structured JSON array and hashes its hex-encoded UTF-8 bytes, so free-text delimiters, null markers, row boundaries, and character encoding cannot alias distinct field values. Invalid preflight still records current expanded counts/checksum, and `sku_count` is the actual total SKU count.
- Deleting a SKU deterministically demotes any primary images attached to it before the declared scoped `SET NULL` foreign-key action retains them at product scope. This avoids creating an accidental second product-level primary image.

### TDD and developer verification

- Testcontainers tests cover a fresh install, Flyway validation, exact v1-to-v3 upgrade, empty and populated baselines, bounded rerun/reconciliation, delimiter-shift drift detection, meaningful invalid-preflight metadata, unchanged legacy columns, deterministic UUID/SKU mapping, typed values, immutable valued-attribute identity, normalized keys, one default SKU, one primary image per scope, constraint-safe SKU-image detachment, SKU/image ownership, lifecycle/version checks, and restrictive deletion.
- The existing Spring Boot catalog API integration test runs the previous v1 JPA application against the expanded schema and verifies create/list/detail/problem behavior.
- Focused migration verification: `TESTCONTAINERS_RYUK_DISABLED=true mvn -B -pl backend/catalog-api -am test -DskipTests=false -Dtest=CatalogMigrationTest -Dsurefire.failIfNoSpecifiedTests=false` — **PASS** (6 migration tests).
- `make test-unit` — **PASS**: foundation/OpenAPI drift gate; all 13 Maven modules and 71 backend tests; frontend OpenAPI generation check, typecheck, lint, 26 unit tests, 3 accessibility tests, production build, and production dependency audit (0 vulnerabilities).
- `make test-integration test-contracts` — **PASS**: 70 Order/Catalog PostgreSQL integration tests plus foundation, OpenAPI, generated-client, and contract assertions.
- `git diff --check` — **PASS**.
- GitHub Actions results are recorded in the PR before final handoff.

### Safety boundaries

- No existing table or column is dropped, renamed, or rewritten.
- No database outside disposable Testcontainers schemas was accessed or changed.
- No production deployment, public exposure, Docker UAT mutation, legacy cleanup, or hard deletion was performed.

### Operational readiness and rollback caveats

- Issue #29 produces reconciliation evidence but does not switch catalog reads/writes. The later issue #30 switch must fail readiness unless `catalog_legacy_reconciliation.status = 'READY'`; this change deliberately does not add that switch/runtime gate.
- Before any switch, operators must run the reconciliation procedure and verify `READY`, matching legacy/product counts and checksums, and review the actual `sku_count` plus an empty migration-issue table.
- A rollback-before-switch can run the previous application against the additive schema. If that application writes legacy `products` rows after V3 has already run, operators must call `catalog_preflight_and_backfill_legacy_products(...)` again and re-verify `READY` before a later switch because Flyway will not rerun V3 automatically.
- Rollback after a future switch remains a stop-writers plus verified-backup restore operation into a new isolated volume under ADR 0005; no lossy undo or production action is part of this issue.
