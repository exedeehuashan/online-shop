# TASK-0021 Implementation Evidence

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

### Pull request handoff

- Implementation commit: `a057218aaf48656b8acd0031590e62ccd26f3077`
- Pull request: https://github.com/exedeehuashan/online-shop/pull/48 (`Closes #33`)
- Target: `main`
- Merge, deployment, public exposure, and production/UAT mutation were not performed.
