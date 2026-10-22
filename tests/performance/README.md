# Performance baseline

k6 scenarios cover the normal API mix, idempotent replay, hot-customer/hot-product reads, and optimistic conflicts. Thresholds are intentionally observational until the environment baseline is approved.

Prerequisites: healthy local Compose stack, k6, Docker. Run `make test-performance`. The runner writes ignored JSON summaries and a Markdown report containing workload, P95, error rate, host/container resources, and Order outbox backlog. All identities are synthetic; credentials and request bodies are excluded. Kafka metrics are not applicable because publishing is outside scope. Traceability: #10 and #17.
