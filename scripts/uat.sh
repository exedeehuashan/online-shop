#!/bin/sh
set -eu
case "${1:-}" in
 up) docker compose up -d --build ;;
 ready)
  i=0; while [ "$i" -lt 60 ]; do
    ids="$(docker compose ps -q)"
    running="$(docker compose ps --status running -q | wc -l | tr -d ' ')"
    unhealthy=0
    [ "$running" = 4 ] || unhealthy=1
    for id in $ids; do [ "$(docker inspect -f '{{.State.Health.Status}}' "$id")" = healthy ] || unhealthy=1; done
    [ "$unhealthy" = 0 ] && curl -fsS "http://127.0.0.1:${WEB_PORT:-8080}/health" >/dev/null && exit 0
    i=$((i+1)); sleep 2
  done
  docker compose ps; exit 1 ;;
 smoke) exec ./tests/uat/run.sh ;;
 *) echo "usage: $0 {up|ready|smoke}" >&2; exit 2 ;;
esac
