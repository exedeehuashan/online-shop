#!/bin/sh
set -eu
case "${1:-}" in
 up) docker compose up -d --build ;;
 ready)
  i=0; while [ "$i" -lt 60 ]; do
    unhealthy="$(docker compose ps --format json | grep -Ev '"Health":"healthy"|"Service":"postgres"' || true)"
    [ -z "$unhealthy" ] && curl -fsS "http://127.0.0.1:${WEB_PORT:-8080}/health" >/dev/null && exit 0
    i=$((i+1)); sleep 2
  done
  docker compose ps; exit 1 ;;
 smoke) exec ./tests/uat/run.sh ;;
 *) echo "usage: $0 {up|ready|smoke}" >&2; exit 2 ;;
esac
