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

### Latest-main integration refresh

- Merged `origin/main` at `39a6a0b55eba4924b2094505bcbadadb475fcff8` after PRs #46 and #48 landed. The only manual conflict was this shared task artifact; both the Issue #29 and Issue #33 evidence sections were retained intact.
- `make test-unit` — **PASS**: foundation/OpenAPI drift, the full 13-module Maven reactor, merged catalog and identity regressions, frontend API drift/typecheck/lint/unit/accessibility/build checks, and production dependency audit.
- `make test-integration test-contracts` — **PASS**: Order/Catalog PostgreSQL integration tests plus foundation, OpenAPI, generated-client, and contract assertions.
- `git diff --check` — **PASS**.

### Safety boundaries

- No existing table or column is dropped, renamed, or rewritten.
- No database outside disposable Testcontainers schemas was accessed or changed.
- No production deployment, public exposure, Docker UAT mutation, legacy cleanup, or hard deletion was performed.

### Operational readiness and rollback caveats

- Issue #29 produces reconciliation evidence but does not switch catalog reads/writes. The later issue #30 switch must fail readiness unless `catalog_legacy_reconciliation.status = 'READY'`; this change deliberately does not add that switch/runtime gate.
- Before any switch, operators must run the reconciliation procedure and verify `READY`, matching legacy/product counts and checksums, and review the actual `sku_count` plus an empty migration-issue table.
- A rollback-before-switch can run the previous application against the additive schema. If that application writes legacy `products` rows after V3 has already run, operators must call `catalog_preflight_and_backfill_legacy_products(...)` again and re-verify `READY` before a later switch because Flyway will not rerun V3 automatically.
- Rollback after a future switch remains a stop-writers plus verified-backup restore operation into a new isolated volume under ADR 0005; no lossy undo or production action is part of this issue.

---

## Issue #33 — isolated identity/RBAC schema, bootstrap, and audit

- **Branch:** `feat/issue-33-identity-rbac`
- **Worktree:** `worktrees/task-0021-issue-33`
- **Approved foundation:** `813a28e0e4ec4f2ef73e67f0c1677be41ae6e82a`
- **Scope:** GitHub Issue #33 only. No OIDC enforcement, HTTP identity/admin routes, production users, deployment, or Issue #34 work is included.

### Delivered

- Activated the existing identity hexagonal module placeholders with framework-free domain/application types, JDBC/Flyway persistence, and a non-web bootstrap runtime.
- Added separately located identity Flyway migrations (`classpath:db/identity`) for `sys_user`, `sys_role`, `sys_permission`, `sys_user_role`, and `sys_role_permission` plus append-only `security_audit_event`.
- Added UUID identity, normalized uniqueness, lifecycle checks, audit/version fields, grant expiry, explicit FK deletion rules, reverse/lookup indexes, and an `effective_user_permission` view that excludes disabled, archived, and expired grants.
- Seeded six stable application-owned permission definitions. Database triggers reject permission-code mutation/deletion and system-role rename/deletion.
- Added explicit transactional bootstrap driven by environment-injected issuer/subject. It creates all seven approved grants across `customer`, `catalog_manager`, `order_manager`, and `access_admin`, binds the first administrator only to `access_admin`, rejects missing/unknown/inactive permission definitions before writes, safely reuses an existing `(issuer, subject)` principal, fails closed on system-role definition drift, is conflict-safe on rerun, and records a bootstrap security audit event using the reserved system actor.
- Added audited role and permission assignment/revocation operations with deterministic row locking and required actor/correlation context. Audit events contain actor, target, action, time, correlation ID, outcome, and safe reason only, and are immutable.
- Added a synthetic subject seed guarded by both an opt-in property and `local`/`uat` profile; it is disabled by default and explicitly fails if a production profile is also active.
- Added the separately owned `identity` database and `identity_app` role to disposable PostgreSQL initialization. Identity configuration references only the identity datasource.

### Security boundaries

- No password, token, secret, private-key, security-answer, or raw authorization data columns exist.
- No real user, credential, or PII seed exists. The normal permission migration creates no users.
- There are no catalog/order dependencies, cross-context foreign keys, or cross-database queries.
- OIDC/JWT enforcement remains deferred to Issue #34 as approved.

### TDD and verification

- Domain tests cover normalized permission constants and active/unexpired effective-grant semantics.
- Testcontainers/PostgreSQL tests cover fresh migration, the five requested tables, constraints/indexes, forbidden credential-column scan, immutable permission/system-role rules (including demotion rejection), transactional idempotent/fail-closed bootstrap, existing-principal reuse, effective grant filtering, append-only audit, and concurrent duplicate grants.
- Focused verification: `mvn -pl backend/identity-domain,backend/identity-application,backend/identity-adapters-persistence,backend/identity-api -am test` — **PASS** (10 tests).
- `git diff --check` and `node scripts/check-foundation.mjs` — **PASS**.
- Full repository: `mvn clean verify -DskipTests=false` — **PASS** (13 modules, 74 tests).
- Contracts: `./tests/contracts/run.sh` — **PASS**.
- Frontend regression: API drift check, typecheck, lint, 26 unit tests, 3 accessibility tests, production build, and high-severity production dependency audit — **PASS** (0 production vulnerabilities at the configured threshold; npm reported one low-severity advisory during install).
- CI path coverage now includes `backend/identity-*/**` in the PostgreSQL integration workflow. Remote CI results will be verified against the pushed candidate SHA before handoff.

### Independent QA remediation

- Added identity migration V3 with explicit `principal_id`, `role_id`, and `permission_id` audit
  fields, a database check constraint for authorization-event completeness, and lookup indexes.
  `ROLE_GRANTED`/`ROLE_REVOKED` now always capture principal and role;
  `PERMISSION_GRANTED`/`PERMISSION_REVOKED` always capture role and permission. Persistence tests
  assert reconstructability for all four actions.
- Serialized bootstrap reconciliation with a transaction-scoped PostgreSQL advisory lock. User and
  system-role creation now use conflict-neutral inserts followed by locked reads and exact invariant
  validation, without exception swallowing. Simultaneous fresh starts converge for both the same
  subject and different subjects.
- Bootstrap now fails closed when an existing principal is disabled. Audit reasons distinguish
  `BOOTSTRAP_CREATED`, `BOOTSTRAP_RECONCILED`, and `IDEMPOTENT_BOOTSTRAP`.
- The append-only audit trigger continues to reject application `UPDATE` and `DELETE`. `TRUNCATE`
  remains an owner-level operational action used only for disposable test cleanup; blocking it in
  the application schema would not protect against the table owner disabling or dropping the
  trigger and would impair isolated integration-test reset. No production access or deployment was
  performed.
- Remediation verification: focused identity suite **PASS** (13 tests); full Maven reactor **PASS**
  (77 tests); contracts **PASS**; PostgreSQL integration **PASS** (64 tests); foundation checks
  **PASS**; frontend typecheck, lint, 26 unit tests, 3 accessibility tests, production build, and
  high-severity production dependency audit **PASS** (0 vulnerabilities at the configured level).

### Pull request handoff

- Implementation commit: `a057218aaf48656b8acd0031590e62ccd26f3077`
- Pull request: https://github.com/exedeehuashan/online-shop/pull/48 (`Closes #33`)
- Target: `main`
- Merge, deployment, public exposure, and production/UAT mutation were not performed.

## Issue #34 — external OIDC/JWT enforcement and RBAC administration

- **Branch:** `feat/issue-34-oidc-rbac`
- **Baseline:** `39a6a0b` (merged Issues #27, #28, and #33)
- Added feature-flagged OAuth resource-server enforcement with required issuer, audience, validity-time, asymmetric algorithm, signature, and JWKS validation. Missing/invalid tokens are 401; valid external identities without one active local `(issuer, subject)` principal and exact permission failures are 403.
- Added bounded (default 30 seconds, maximum 5 minutes) effective-permission caching with immediate invalidation for role and permission changes; wildcard, inheritance, and explicit-deny behavior are absent.
- Added `/api/v1/me`, read-only administrative user/role/permission views, and audited role/permission grant and revocation routes protected by exact `rbac:manage` authority. Responses do not expose bearer or refresh tokens.
- Added correlation propagation and deterministic UUID audit correlation mapping, preserving accepted caller correlation identifiers on responses.
- Added feature-flagged catalog mutation protection. Catalog validates the same strict JWT boundary and obtains locally effective application permissions from the internal identity `/me` endpoint before allowing `catalog:write` operations. Existing public catalog reads remain available. The nginx public proxy was not changed and contains no identity route.
- Checked in the Identity OpenAPI contract and checksum/provenance validation.
- Added security/API tests covering 401/403 separation, malformed tokens, inactive local identities, exact-permission denial, safe `/me`, mutation audit context, cache TTL and invalidation, and unsafe OIDC/cache configuration rejection.
- No IdP was selected, no credential storage was introduced, and no UAT/production enablement, deployment, public exposure, or production mutation was performed.
- Developer verification: focused catalog/identity reactor **PASS**; full reactor verification **PASS** (13 modules, 86 tests, 0 failures/errors/skips); contracts/API drift **PASS**; foundation checks and `git diff --check` **PASS**. Contract verification reported the pre-existing frontend dependency audit state of one low-severity advisory.

### Issue #34 independent security QA remediation

- Remediated QA D1–D8 from the independent failure at `6c1f844ac083b8b4805558c865b373f34e1612ae` without altering the frozen response shapes or exposing identity routes publicly.
- Role grants now use a locked, idempotent upsert: unchanged grants remain no-ops, while permanent-to-expiring, expiring-to-permanent, and expired-to-renewed transitions update `granted_at`, `granted_by`, and `expires_at`, append an `EXPIRY_UPDATED` audit result, and retain immediate target-cache invalidation.
- Identity and catalog decoders require `exp`, reject missing `aud` without throwing, enforce issuer/audience/signature/RS algorithm/`nbf`/expiry, and default to an explicitly documented zero skew. Configured skew is bounded to 0–30 seconds.
- Identity admin parse/type errors map to RFC 9457 400, semantic expiry/page errors to 422, missing locked targets to 404, data conflicts to 409, and unexpected errors to 500. Internal error dispatch is permitted, while API authorization remains deny-by-default. Security problems include `application/problem+json`, correlation ID and instance; 401 responses also include `WWW-Authenticate: Bearer`.
- Admin lists accept backwards-compatible optional `page`/`size` parameters (defaults 0/20, maximum 100) and use deterministic SQL pagination. The permission cache now has a 10,000-entry default maximum with expired/earliest-expiry eviction in addition to its bounded TTL.
- Added real RSA/JWKS decoder tests in both services for correct and wrong signature, issuer, audience, missing claims, expiry, future `nbf`, and algorithm confusion; HTTP tests cover RFC 9457 security/admin errors; PostgreSQL tests cover expiry changes and renewal; cache tests cover entry bounds.
- Remediation verification: full 13-module Maven `clean verify` **PASS**; PostgreSQL/Testcontainers integration **PASS**; contracts and foundation drift checks **PASS**; frontend typecheck, lint, 46 tests, and production build **PASS**. The pre-existing npm audit state remains one low-severity advisory.
