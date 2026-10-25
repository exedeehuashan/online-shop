# Isolated local UAT backup, restore and recovery

This runbook is **local-only**. Never use a production endpoint or an existing target. Dump files may
contain personal data: keep the ignored `.uat-evidence/` directory mode-restricted and delete it
according to local policy. Commands never print passwords.

Use unique DNS-safe names and explicit collision-free loopback ports. `online-shop-uat` is forbidden
as a target. PostgreSQL and Identity have no host port; application ports in the Compose files bind
only to `127.0.0.1`.

For a fresh disposable target, the hardened overlay runs explicit one-shot `migrate-orders`,
`migrate-catalog`, and `migrate-identity` jobs with independent `*_migration` logins before the
applications. It disables application Flyway and
switches them to DML-only `*_runtime` roles. The checked-in passwords are conspicuously local-only;
override all of them in a mode-0600 ignored environment file for shared developer hosts.

PostgreSQL is the documented hardening exception: its official entrypoint must initialize and change
ownership of a new data directory, so the overlay does not drop every capability or make its root
filesystem read-only. It remains network-internal, uses `no-new-privileges`, and has bounded tmpfs.

```sh
src=task26-source-20261025
target=task26-target-20261025
recovery=task26-recovery-20261025
mkdir -m 700 -p .uat-evidence/run-20261025
./scripts/uat-drill.sh preflight --source-project "$src" --target-project "$target"
./scripts/uat-drill.sh backup --source-project "$src" --evidence-dir .uat-evidence/run-20261025
./scripts/uat-drill.sh restore --target-project "$target" --manifest .uat-evidence/run-20261025/manifest.json
./scripts/uat-drill.sh upgrade --target-project "$target" --manifest .uat-evidence/run-20261025/manifest.json
./scripts/uat-drill.sh verify --target-project "$target" --manifest .uat-evidence/run-20261025/manifest.json
```

Validate Flyway histories, frozen v1 probes, current contracts, authenticated UAT and runtime role
negative DDL checks before switching writers. Never run old and new writers concurrently. Restart
with `docker compose -p "$target" restart`; normal stop preserves data:

```sh
./scripts/uat-drill.sh stop --target-project "$target"
./scripts/uat-drill.sh recover --target-project "$recovery" --manifest .uat-evidence/run-20261025/manifest.json
```

Recovery always restores the sealed backup into a second new project; there is no down migration.
Inventory uses a fixed critical-table allow-list for each database. It records counts and SHA-256
checksums of canonical JSONB rows ordered with PostgreSQL's `C` collation; row values are never
written to evidence or logs. A missing allow-listed table fails closed. Restore, verify and recovery
recompute these values and reject any count or checksum difference.

Restore rejects unsealed, incompatible, missing, or checksum-invalid evidence. A backup is only a
candidate until independent restore and semantic comparisons pass.

Cleanup prints and deletes only Compose-labelled exact targets and needs typed confirmation:

```sh
./scripts/uat-drill.sh cleanup --target-project "$target" --confirm "$target"
```

CI cleanup additionally requires a pre-resource, run-issued ownership manifest and a strong nonce;
the same nonce must label every Compose container and volume. For example, issue the manifest with
`issue-ci-manifest`, export matching `UAT_RUN_OWNER`/`UAT_RUN_NONCE` while creating the stack, then
pass both `--manifest` and `--nonce` to `cleanup --ci-generated`. A name prefix alone is rejected.
Capture resolved
Compose configuration, image identities, manifest/checksums, health, contracts, counts and before /
after Docker inventories as test evidence.
