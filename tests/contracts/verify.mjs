import fs from 'node:fs';
const order=JSON.parse(fs.readFileSync('frontend/order-web/openapi/order-api.json','utf8'));
const catalog=JSON.parse(fs.readFileSync('tests/contracts/catalog-api.json','utf8'));
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
const problem=fs.readFileSync('docs/problem-details.md','utf8');
for(const code of ['MALFORMED_REQUEST','PRODUCT_NOT_FOUND']) if(!problem.includes(code)) throw new Error(`problem code missing: ${code}`);
const migration=fs.readFileSync('backend/order-adapters-persistence/src/main/resources/db/migration/V2__harden_order_outbox.sql','utf8');
for(const marker of ['event_version','correlation_id','aggregate_version']) if(!migration.includes(marker)) throw new Error(`outbox envelope marker missing: ${marker}`);
console.log('Contract assertions passed');
