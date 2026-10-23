# Playwright E2E

Runs deterministic synthetic Order journeys using role/label selectors, request interception and condition-based assertions; there are no fixed sleeps. Prerequisites: Node.js 22 and Playwright Chromium (`npx playwright install chromium`). Run `make test-e2e`. HTML/JUnit reports are generated in ignored directories. Traceability: #9, #17 and stories #13–#16.

The root runner reuses the application-owned specs in `frontend/order-web/e2e`; API-level Catalog UAT is automated in `tests/uat` because a Catalog merchandising UI is outside scope.
