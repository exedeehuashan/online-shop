import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const read = async (path) => readFile(new URL(path, root), 'utf8');

const manifest = JSON.parse(await read('tests/contracts/openapi-checksums.json'));
if (manifest.baseline !== '8a626c6aa5f9422762f4e2097162cf7f3eb4a354') {
  throw new Error('Foundation baseline changed without approval');
}
for (const document of manifest.documents) {
  const bytes = await readFile(new URL(document.path, root));
  const actual = createHash('sha256').update(bytes).digest('hex');
  if (actual !== document.sha256) {
    throw new Error(`OpenAPI drift: ${document.path} expected ${document.sha256}, received ${actual}`);
  }
  if (!document.source) throw new Error(`OpenAPI provenance missing: ${document.path}`);
}

const pom = await read('pom.xml');
for (const module of ['identity-domain', 'identity-application', 'identity-adapters-persistence', 'identity-api']) {
  if (!pom.includes(`<module>backend/${module}</module>`)) throw new Error(`Identity module missing: ${module}`);
}

const decisions = (await Promise.all([
  read('docs/adr/0001-bounded-context-and-database-ownership.md'),
  read('docs/adr/0002-external-oidc-and-deny-by-default-rbac.md'),
  read('docs/adr/0004-api-versioning-and-compatibility.md'),
  read('docs/adr/0005-expand-backfill-switch-contract.md'),
])).join('\n');
for (const invariant of ['Cross-context database joins', 'must never store passwords', 'at least one released version', 'new human approval']) {
  if (!decisions.includes(invariant)) throw new Error(`Foundation decision missing invariant: ${invariant}`);
}
console.log('Foundation and OpenAPI drift checks passed');
