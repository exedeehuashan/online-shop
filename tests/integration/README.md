# Integration suite

Runs Order persistence/API and Catalog API against disposable PostgreSQL 16 Testcontainers. It covers atomic outbox envelopes, concurrency/optimistic conflicts, immutable product/address snapshots, opaque public UUIDs vs internal IDs, migrations, and Catalog create/list/detail. Docker is mandatory: absence fails the suite rather than skipping.

Prerequisites: Java 17, Maven 3.9, and Docker. Run `make test-integration`. Synthetic fixtures only. JUnit XML is written beneath module `target/surefire-reports`; generated reports are ignored. Traceability: #1, #9, #17 and stories #13–#16.
