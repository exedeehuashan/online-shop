#!/bin/sh
set -eu
command -v k6 >/dev/null; docker info >/dev/null
mkdir -p tests/performance/reports
k6 run --summary-export tests/performance/reports/k6-summary.json tests/performance/scenarios.js
container_ids="$(docker compose ps -q)"
# shellcheck disable=SC2086
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' $container_ids > tests/performance/reports/container-resources.txt
backlog="$(docker compose exec -T postgres psql -U order_app -d orders -Atc 'select count(*) from outbox_events where published_at is null')"
p95="$(node -e "const m=require('./tests/performance/reports/k6-summary.json').metrics.http_req_duration; const v=m.values?.['p(95)'] ?? m['p(95)']; if(v==null) process.exit(1); process.stdout.write(String(v))")"
errors="$(node -e "const m=require('./tests/performance/reports/k6-summary.json').metrics.http_req_failed; const v=m.values?.rate ?? m.value ?? m.rate; if(v==null) process.exit(1); process.stdout.write(String(v))")"
{
 echo '# Local performance baseline'; echo; echo "- Workload: normal mix, replay, hot access, optimistic conflict (45 seconds)"; echo "- HTTP duration P95 (ms): ${p95:-see JSON}"; echo "- HTTP error rate: ${errors:-see JSON}"; echo "- Outbox backlog: $backlog"; echo '- Container resources:'; sed 's/^/  - /' tests/performance/reports/container-resources.txt; echo '- Interpretation: observational only; no approved capacity threshold.';
} > tests/performance/reports/baseline.md
cat tests/performance/reports/baseline.md
