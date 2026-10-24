import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { URL } from 'node:url';

const expected = '0e7f989b165110e402bf1359a07fa7281c1f4380371ab67158b9c973a6519f6a';
const document = await readFile(new URL('../openapi/order-api.json', import.meta.url));
const actual = createHash('sha256').update(document).digest('hex');
if (actual !== expected) {
  throw new Error(`OpenAPI export checksum mismatch: expected ${expected}, received ${actual}. Re-export from the pinned backend commit and update provenance deliberately.`);
}
const catalogExpected = '54acd4083be499fa9f19ada4639c8f2b80539d8b6a15e6a2a0e268b511c6f424';
const catalogDocument = await readFile(new URL('../openapi/catalog-api-v2.json', import.meta.url));
const catalogActual = createHash('sha256').update(catalogDocument).digest('hex');
if (catalogActual !== catalogExpected) {
  throw new Error(`Catalog OpenAPI export checksum mismatch: expected ${catalogExpected}, received ${catalogActual}.`);
}
