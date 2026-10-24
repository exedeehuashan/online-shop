import {describe,expect,it} from 'vitest';
import {CartStore} from './cart';
const item={productId:'p1',skuId:'s1',sku:'SKU-1',name:'Tea',unitPrice:{amount:'12.50',currency:'NZD'},quantity:1,selectedAttributes:{size:'Large'}};
describe('CartStore',()=>{
 it('merges, edits, removes and clears SKU snapshots',()=>{const s=new CartStore();s.add(item);s.add(item);expect(s.read()[0].quantity).toBe(2);s.setQuantity('s1',4);expect(s.read()[0].quantity).toBe(4);s.remove('s1');expect(s.read()).toEqual([]);s.add(item);s.clear();expect(s.read()).toEqual([])});
 it('rejects unknown versions and stores no account data',()=>{let value=JSON.stringify({version:99,items:[item]});const storage={getItem(){return value},setItem(_k:string,v:string){value=v},removeItem(){value=''}} as unknown as Storage;const s=new CartStore(storage);expect(s.read()).toEqual([]);s.add(item);expect(storage.getItem('northstar.cart.v2')).not.toMatch(/token|customer|address|phone/i)});
});
