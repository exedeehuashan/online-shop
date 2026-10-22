import { describe,expect,it } from 'vitest';
import { mergeItemsBySku,validateCancelReason,validateDraft } from './orders';
import { buildDraftItem } from '../fixtures/orders';
describe('mergeItemsBySku',()=>{
  it('normalizes and merges duplicates while retaining first-row order',()=>{const merged=mergeItemsBySku([buildDraftItem({sku:' sku-1 ',quantity:2}),buildDraftItem({sku:'OTHER'}),buildDraftItem({sku:'SKU-1',quantity:3})]);expect(merged.map(x=>[x.sku,x.quantity])).toEqual([['sku-1',5],['OTHER',1]]);});
  it('rejects conflicting snapshots',()=>expect(()=>mergeItemsBySku([buildDraftItem(),buildDraftItem({name:'Different'})])).toThrow(/conflicting/i));
  it.each([0,-1,1.5,Number.MAX_SAFE_INTEGER])('rejects invalid or overflowing quantities: %s',(quantity)=>expect(()=>mergeItemsBySku([buildDraftItem({quantity}),buildDraftItem({quantity:1})])).toThrow());
});
describe('validation',()=>{
  it('validates required draft data',()=>expect(validateDraft({customerName:'',items:[],shippingAddress:{recipient:'',line1:'',line2:'',city:'',region:'',postalCode:'',countryCode:'',phone:''}})).toHaveProperty('customerName'));
  it('validates cancellation reason',()=>{expect(validateCancelReason('')).toMatch(/required/i);expect(validateCancelReason('x'.repeat(501))).toMatch(/500/);expect(validateCancelReason('changed mind')).toBeNull();});
});
