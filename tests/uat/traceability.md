# Acceptance traceability

| Issue / criterion | Evidence |
|---|---|
| #1 create/detail/page | `CatalogApiTest`, `tests/uat/run.sh` |
| #1 decimal/Flyway/PostgreSQL | `ProductTest`, `CatalogApiTest` Testcontainer |
| #1 consistent errors | `CatalogApiTest`, checked Catalog OpenAPI |
| #9 API/outbox/concurrency/snapshots/dual IDs | existing Order application/persistence/API tests via `tests/integration/run.sh` |
| #9 stable Playwright | `frontend/order-web/e2e/orders.spec.ts`, `tests/e2e/run.sh` |
| #10 UAT mapping | `scenarios.md`, this matrix, smoke runner |
| #10 mix/replay/hot customer/hot product/conflict | k6 scripts/config in `tests/performance` |
| #10 P95/errors/resources/backlog | `tests/performance/run.sh` and report template |
| #11 images/non-root/health | Dockerfiles, Compose config and CI inspection |
| #11 PostgreSQL default | `compose.yaml`; no optional service required |
| #11 ordered CI/manual performance | `.github/workflows/quality.yml`, `performance.yml` |
| #13 all criteria | Order domain/application/API tests + create/replay smoke |
| #14 all criteria | Order persistence/API tests + list/detail smoke |
| #15 all criteria | state/optimistic-lock tests + confirmation smoke |
| #16 all criteria | state/validation tests + cancellation smoke |
| #17 reproducible, synthetic, secret-free | all five suite READMEs/runners; independent tester still required |
