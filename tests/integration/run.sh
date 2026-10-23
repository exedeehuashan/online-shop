#!/bin/sh
set -eu
docker info >/dev/null
# Every test tagged "docker" (Testcontainers), in all modules.
exec mvn -B verify -Dgroups=docker -Dsurefire.failIfNoSpecifiedTests=false
