# Performance baseline

k6 scenarios cover the normal API mix, idempotent replay, hot-customer/hot-product reads, and optimistic conflicts. Thresholds are intentionally observational until the environment baseline is approved.

Prerequisites: healthy local Compose stack, k6, Docker. Run `make test-performance`. `ORDER_API`, `CATALOG_API`, `ORDER_API_PORT`, and `CATALOG_API_PORT` can point the repeatable gate at a non-default environment. The runner records overall and Catalog v2 P95, error rate, container resources, and Order outbox backlog. k6 fails unless checks exceed 99%, HTTP errors stay below 1%, and Catalog v2 P95 stays below the practical local/CI ceiling of 1000 ms. All identities are synthetic; credentials and request bodies are excluded. Kafka metrics are not applicable because publishing is outside scope. Traceability: #10 and #17.
