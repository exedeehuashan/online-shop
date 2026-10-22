#!/bin/sh
set -eu
command -v k6 >/dev/null; docker info >/dev/null
mkdir -p tests/performance/reports
k6 run --summary-export tests/performance/reports/k6-summary.json tests/performance/scenarios.js
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' > tests/performance/reports/container-resources.txt
backlog="$(docker compose exec -T postgres psql -U order_app -d orders -Atc 'select count(*) from outbox_events where published_at is null')"
p95="$(sed -n 's/.*"http_req_duration"[^}]*"p(95)":\([^,}]*\).*/\1/p' tests/performance/reports/k6-summary.json | head -1)"
errors="$(sed -n 's/.*"http_req_failed"[^}]*"rate":\([^,}]*\).*/\1/p' tests/performance/reports/k6-summary.json | head -1)"
{
 echo '# Local performance baseline'; echo; echo "- Workload: normal mix, replay, hot access, optimistic conflict (45 seconds)"; echo "- HTTP duration P95 (ms): ${p95:-see JSON}"; echo "- HTTP error rate: ${errors:-see JSON}"; echo "- Outbox backlog: $backlog"; echo '- Container resources:'; sed 's/^/  - /' tests/performance/reports/container-resources.txt; echo '- Interpretation: observational only; no approved capacity threshold.';
} > tests/performance/reports/baseline.md
cat tests/performance/reports/baseline.md
