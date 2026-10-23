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

### TDD and developer verification

- Testcontainers tests cover a fresh install, Flyway validation, exact v1-to-v3 upgrade, empty and populated baselines, bounded rerun/reconciliation, invalid-data preflight and correction, unchanged legacy columns, deterministic UUID/SKU mapping, typed values, immutable valued-attribute identity, normalized keys, one default SKU, one primary image per scope, SKU/image ownership, lifecycle/version checks, and restrictive deletion.
- The existing Spring Boot catalog API integration test runs the previous v1 JPA application against the expanded schema and verifies create/list/detail/problem behavior.
- Focused migration verification: `mvn -pl backend/catalog-api -am test -DskipTests=false -Dtest=CatalogMigrationTest -Dsurefire.failIfNoSpecifiedTests=false` — **PASS** (4 migration tests).
- `make test-unit` — **PASS**: foundation/OpenAPI drift gate; all 13 Maven modules and 69 backend tests; frontend OpenAPI generation check, typecheck, lint, 26 unit tests, 3 accessibility tests, production build, and production dependency audit (0 vulnerabilities).
- `make test-integration test-contracts` — **PASS**: 68 Order/Catalog PostgreSQL integration tests plus foundation, OpenAPI, generated-client, and contract assertions.
- `git diff --check` — **PASS**.
- GitHub Actions results are recorded in the PR before final handoff.

### Safety boundaries

- No existing table or column is dropped, renamed, or rewritten.
- No database outside disposable Testcontainers schemas was accessed or changed.
- No production deployment, public exposure, Docker UAT mutation, legacy cleanup, or hard deletion was performed.
