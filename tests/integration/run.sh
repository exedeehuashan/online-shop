#!/bin/sh
set -eu
docker info >/dev/null
exec mvn -B -pl backend/order-adapters-persistence,backend/order-api,backend/catalog-api -am verify
