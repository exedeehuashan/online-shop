import fs from 'node:fs';
import crypto from 'node:crypto';
const manifest=JSON.parse(fs.readFileSync('tests/contracts/openapi-checksums.json','utf8'));
for(const document of manifest.documents) {
 const actual=crypto.createHash('sha256').update(fs.readFileSync(document.path)).digest('hex');
 if(actual!==document.sha256) throw new Error(`OpenAPI checksum drift: ${document.path}`);
 if(!document.source) throw new Error(`OpenAPI provenance missing: ${document.path}`);
}
const order=JSON.parse(fs.readFileSync('frontend/order-web/openapi/order-api.json','utf8'));
const catalog=JSON.parse(fs.readFileSync('tests/contracts/catalog-api.json','utf8'));
const catalogV2=JSON.parse(fs.readFileSync('tests/contracts/catalog-api-v2.json','utf8'));
const identity=JSON.parse(fs.readFileSync('tests/contracts/identity-api.json','utf8'));
for (const [name,doc,path] of [['order',order,'/api/v1/customers/{customerId}/orders'],['catalog',catalog,'/api/v1/products']]) {
 if (!doc.openapi?.startsWith('3.') || !doc.paths?.[path]) throw new Error(`${name} OpenAPI contract missing ${path}`);
}
const expectedCatalogResponses={
 '/api/v1/products':{post:['201','400','422'],get:['200','422']},
 '/api/v1/products/{productId}':{get:['200','400','404']}
};
for(const [path,methods] of Object.entries(expectedCatalogResponses)) for(const [method,codes] of Object.entries(methods)) {
 const actual=Object.keys(catalog.paths?.[path]?.[method]?.responses||{}).sort();
 if(JSON.stringify(actual)!==JSON.stringify(codes.sort())) throw new Error(`catalog contract drift: ${method.toUpperCase()} ${path} responses ${actual}`);
}
for (const path of ['/api/v2/products','/api/v2/products/{productId}','/api/v2/products/by-slug/{slug}']) {
 if (!catalogV2.paths?.[path]?.get || Object.keys(catalogV2.paths[path]).some(method => method !== 'get')) throw new Error(`catalog v2 read contract missing or exposes mutation: ${path}`);
}
if (catalogV2.paths['/api/v2/products'].get.parameters.find(p=>p.name==='size')?.schema?.maximum !== 100) throw new Error('catalog v2 size bound drift');
if (!catalogV2.components?.schemas?.Problem || !catalogV2.components?.schemas?.Image) throw new Error('catalog v2 schemas missing');
const problem=fs.readFileSync('docs/problem-details.md','utf8');
for(const code of ['MALFORMED_REQUEST','PRODUCT_NOT_FOUND']) if(!problem.includes(code)) throw new Error(`problem code missing: ${code}`);
for (const path of ['/api/v1/me','/api/v1/admin/users','/api/v1/admin/roles','/api/v1/admin/permissions','/api/v1/admin/users/{userId}/roles/{roleId}','/api/v1/admin/roles/{roleId}/permissions/{permissionId}'])
 if (!identity.paths?.[path]) throw new Error(`identity OpenAPI contract missing ${path}`);
if (JSON.stringify(identity.security)!==JSON.stringify([{oidcBearer:[]}])) throw new Error('identity contract must require bearer authentication globally');
const migration=fs.readFileSync('backend/order-adapters-persistence/src/main/resources/db/migration/V2__harden_order_outbox.sql','utf8');
for(const marker of ['event_version','correlation_id','aggregate_version']) if(!migration.includes(marker)) throw new Error(`outbox envelope marker missing: ${marker}`);
console.log('Contract assertions passed');
