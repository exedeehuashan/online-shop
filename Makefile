.PHONY: test-unit test-integration test-contracts test-e2e test-uat test-performance compose-build compose-up compose-ready compose-logs compose-down compose-destroy
test-unit:
	mvn clean verify -DskipTests=false
	cd frontend/order-web && npm ci && npm run api:check && npm run typecheck && npm run lint && npm test && npm run test:a11y && npm run build && npm audit --omit=dev --audit-level=high
test-integration:
	./tests/integration/run.sh
test-contracts:
	./tests/contracts/run.sh
test-e2e:
	./tests/e2e/run.sh
test-uat:
	./tests/uat/run.sh
test-performance:
	./tests/performance/run.sh
compose-build:
	docker compose build
compose-up:
	./scripts/uat.sh up
compose-ready:
	./scripts/uat.sh ready
compose-logs:
	docker compose logs --tail=200
compose-down:
	docker compose down --remove-orphans
compose-destroy:
	docker compose down --volumes --remove-orphans
