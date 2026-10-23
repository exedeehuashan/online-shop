# Integration suite

Runs every test tagged `docker` (Order persistence/API, Catalog API and migrations, Identity persistence) against disposable PostgreSQL 16 Testcontainers. It covers atomic outbox envelopes, concurrency/optimistic conflicts, immutable product/address snapshots, opaque public UUIDs vs internal IDs, migrations, and Catalog create/list/detail. Docker is mandatory: absence fails the suite rather than skipping.

Prerequisites: Java 17, Maven 3.9, and Docker. Run `make test-integration`. Synthetic fixtures only. JUnit XML is written beneath module `target/surefire-reports`; generated reports are ignored. Traceability: #1, #9, #17 and stories #13–#16.

## Tagging rule

A test that needs Docker (any `@Testcontainers` class) must also carry `@Tag("docker")`. The unit job and `make test-unit` run `-DexcludedGroups=docker` and must work without Docker; this suite runs `-Dgroups=docker` across all modules. An untagged Testcontainers test fails the unit job with "Could not find a valid Docker environment", which is how CatalogMigrationTest and IdentityPersistenceTest were found.
