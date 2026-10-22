import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { URL } from 'node:url';

const expected = '1d8f91bddbe6fdca9d3702c8aaba96747f69f793824f6da4985af11e971a43d3';
const document = await readFile(new URL('../openapi/order-api.json', import.meta.url));
const actual = createHash('sha256').update(document).digest('hex');
if (actual !== expected) {
  throw new Error(`OpenAPI export checksum mismatch: expected ${expected}, received ${actual}. Re-export from the pinned backend commit and update provenance deliberately.`);
}
