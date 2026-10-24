import type {components} from '../api/generated';
export type ProductDetail=components['schemas']['ProductDetail'];export type Sku=components['schemas']['Sku'];export type Selection=Record<string,string>;
const skuMap=(sku:Sku)=>Object.fromEntries(sku.attributes.flatMap(a=>a.values.map(v=>[a.definition.code,v.id])));
export function initialSelection(product:ProductDetail):Selection{const sku=product.skus.find(s=>s.id===product.defaultSkuId);return sku?skuMap(sku):{}}
export function resolveSku(product:ProductDetail,selection:Selection){const matches=product.skus.filter(s=>{const map=skuMap(s);return product.variantAxes.every(axis=>selection[axis.definition.code]===map[axis.definition.code])});return matches.length===1?matches[0]:undefined}
export function optionAvailable(product:ProductDetail,selection:Selection,code:string,valueId:string){return product.skus.some(s=>{if(s.availability!=='IN_STOCK')return false;const map=skuMap(s);return Object.entries({...selection,[code]:valueId}).every(([key,value])=>!value||map[key]===value)})}
