# Online Shop

Modular Java 17/Spring Boot Order and Catalog APIs with a React Order UI. Catalog and Order own separate databases and do not query each other's tables.

## Verify

- `make test-unit`: backend clean verify plus frontend drift/type/lint/unit/accessibility/build/audit gates.
- `make test-foundation`: ADR, convention, module-boundary, and checked-in OpenAPI provenance/drift gates.
- `make test-integration`, `make test-contracts`, `make test-e2e`, `make test-uat`, `make test-performance`: dedicated suites; see each `tests/*/README.md`.

## Local UAT (not safe for public exposure)

Prerequisites: Docker Compose v2, curl, free loopback ports 8080–8082. Override ports by copying `.env.example` to ignored `.env` and changing values.

```sh
docker compose config
make compose-build
make compose-up
make compose-ready
make test-uat
make compose-logs
make compose-down       # retains named database volume
make compose-destroy    # destructive: explicitly removes UAT volume
```

Endpoints: web `http://127.0.0.1:8080`, Order diagnostics `http://127.0.0.1:8081`, Catalog diagnostics/OpenAPI `http://127.0.0.1:8082/v3/api-docs`. All published ports bind only to loopback. Defaults are disposable local values, not production secrets. Never expose this unauthenticated stack publicly or load real customer data.
