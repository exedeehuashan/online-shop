import {expect,test,type Page} from '@playwright/test';
import axeCore from 'axe-core';

const id='11111111-1111-1111-1111-111111111111';
const order=(status='DRAFT',version=0)=>({id,customerId:'synthetic-user',customerName:'Ada',status,items:[{productId:'p1',sku:'SKU-1',name:'Tea',unitPrice:{amount:'10.00',currency:'NZD'},quantity:1,lineTotal:{amount:'10.00',currency:'NZD'}}],total:{amount:'10.00',currency:'NZD'},shippingAddress:{recipient:'Ada',line1:'1 Queen St',line2:'',city:'Auckland',region:'',postalCode:'1010',countryCode:'NZ',phone:''},cancellationReason:null,version,createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-01T00:00:00Z'});
const pageOf=(content:unknown[])=>({content,page:0,size:20,totalElements:content.length,totalPages:content.length?1:0});
const cart=JSON.stringify({version:2,items:[{productId:'p1',skuId:'s1',sku:'SKU-1',name:'Tea',unitPrice:{amount:'10.00',currency:'NZD'},quantity:1,selectedAttributes:[]}]});

async function install(page:Page,permissions=['order:read:self','order:read:any','order:manage','rbac:manage','catalog:write']){
 await page.addInitScript(value=>sessionStorage.setItem('northstar.cart.v2',value),cart);
 await page.addInitScript({content:axeCore.source});
 await page.route('**/api/uat/token',r=>r.fulfill({json:{access_token:'acceptance-only-memory-token'}}));
 await page.route('**/api/v1/me',r=>r.fulfill({json:{id:'u1',displayName:'Ada',permissions}}));
 await page.route('**/api/v2/me/orders?**',r=>r.fulfill({json:pageOf([order()])}));
 await page.route(`**/api/v2/me/orders/${id}`,r=>r.fulfill({json:order()}));
 await page.route(`**/api/v2/me/orders/${id}/confirmation`,r=>r.fulfill({json:order('CONFIRMED',1)}));
 await page.route(`**/api/v2/me/orders/${id}/cancellation`,r=>r.fulfill({json:order('CANCELLED',2)}));
 await page.route('**/api/v2/me/orders',r=>r.fulfill({status:201,json:order()}));
 await page.route('**/api/v2/admin/orders?**',r=>r.fulfill({json:pageOf([order()])}));
 await page.route(`**/api/v2/admin/orders/${id}`,r=>r.fulfill({json:order()}));
 await page.route(`**/api/v2/admin/orders/${id}/confirmation`,r=>r.fulfill({json:order('CONFIRMED',1)}));
 await page.route(`**/api/v2/admin/orders/${id}/cancellation`,r=>r.fulfill({json:order('CANCELLED',2)}));
 await page.route('**/api/v1/admin/users?**',r=>r.fulfill({json:[{id:'u2',displayName:'Target',issuer:'uat',subject:'target',status:'ACTIVE',version:0}]}));
 await page.route('**/api/v1/admin/roles?**',r=>r.fulfill({json:[{id:'r1',code:'qa_mutable',name:'QA mutable',status:'ACTIVE',systemRole:false,version:0}]}));
 await page.route('**/api/v1/admin/permissions?**',r=>r.fulfill({json:[{id:'p1',code:'catalog:read',description:'Read catalog',status:'ACTIVE',version:0}]}));
 await page.route('**/api/v1/admin/roles/*/permissions/*',r=>r.fulfill({status:204}));
 await page.route('**/api/v1/products',r=>r.fulfill({status:201,json:{id:'p2',name:'Created'}}));
}
async function signIn(page:Page,path:string){await page.goto(path);await page.getByRole('button',{name:'Sign in'}).click();await expect(page).toHaveURL(new RegExp(`${path}$`))}
async function go(page:Page,path:string){await page.evaluate(next=>{history.pushState({},'',next);dispatchEvent(new PopStateEvent('popstate'))},path);await expect(page).toHaveURL(new RegExp(`${path.replaceAll('/','\\/')}$`))}
async function axe(page:Page,label:string){await expect(page.locator('main h1')).toBeVisible();const violations=await page.evaluate(async()=>((await (globalThis as typeof globalThis&{axe:{run:()=>Promise<{violations:Array<{id:string}>}>}}).axe.run()).violations.map(v=>v.id)));expect(violations,`${label} axe violations`).toEqual([])}

test('T25/T29 route, state, dialog, 320px and zoom axe matrix',async({page})=>{
 await install(page);await page.setViewportSize({width:320,height:800});await signIn(page,'/checkout');
 const routes=['/cart','/checkout','/account/orders',`/account/orders/${id}`,'/admin/orders',`/admin/orders/${id}`,'/admin/access/users','/admin/access/roles','/admin/access/permissions','/admin/products'];
 for(const route of routes){await go(page,route);await axe(page,route);expect(await page.evaluate(()=>document.documentElement.scrollWidth),`${route} reflow`).toBeLessThanOrEqual(320)}
 await go(page,'/checkout');await page.getByRole('button',{name:'Submit order'}).click();await expect(page.getByRole('alert')).toBeFocused();await axe(page,'checkout validation');
 await go(page,`/admin/orders/${id}`);await page.getByRole('button',{name:'Confirm order'}).click();await axe(page,'admin confirmation dialog');await page.keyboard.press('Escape');
 await page.getByRole('button',{name:'Cancel order'}).click();await axe(page,'admin cancellation dialog');await page.keyboard.press('Escape');
 for(const zoom of ['2','4']){await page.evaluate(value=>{document.documentElement.style.zoom=value},zoom);await axe(page,`${Number(zoom)*100} percent zoom`);await expect(page.getByRole('heading',{name:'Admin order detail'})).toBeVisible()}
});

test('T25 loading state is deterministic and axe-clean',async({page})=>{
 await install(page);await page.route('**/api/v2/me/orders?**',async()=>{await new Promise(()=>{})});await page.goto('/account/orders');await page.getByRole('button',{name:'Sign in'}).click();await expect(page.getByRole('status',{name:'Loading orders'})).toBeVisible();await axe(page,'loading');
});

test('T25 loading, empty, service error, expired and permission-denied states are axe-clean',async({page})=>{
 await install(page,['order:read:self']);await signIn(page,'/account/orders');await go(page,'/admin/orders');await expect(page.getByRole('heading',{name:'You don’t have access'})).toBeVisible();await axe(page,'permission denied');
 await page.reload();await expect(page.getByRole('heading',{name:'Sign in required'})).toBeVisible();await axe(page,'expired/reloaded memory session');
 await page.unroute('**/api/v2/me/orders?**');await page.route('**/api/v2/me/orders?**',r=>r.fulfill({json:pageOf([])}));await page.reload();await page.getByRole('button',{name:'Sign in'}).click();await expect(page.getByRole('heading',{name:'You don’t have access'})).toBeVisible();await go(page,'/account/orders');await expect(page.getByRole('heading',{name:'No orders yet'})).toBeVisible();await axe(page,'empty');
 await page.unroute('**/api/v2/me/orders?**');await page.route('**/api/v2/me/orders?**',r=>r.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({detail:'Unavailable'})}));await page.reload();await page.getByRole('button',{name:'Sign in'}).click();await expect(page.getByRole('alert')).toBeVisible();await axe(page,'service error');
});

test('T26 keyboard-only checkout, account, admin order and access journeys',async({page})=>{
 await install(page);await signIn(page,'/checkout');
 for(const [label,value] of [['recipient','Ada'],['line1','1 Queen St'],['city','Auckland'],['postal Code','1010']] as const){await page.getByLabel(label,{exact:false}).focus();await page.keyboard.type(value)}
 await page.getByRole('button',{name:'Submit order'}).focus();await page.keyboard.press('Enter');await expect(page).toHaveURL(new RegExp(`/account/orders/${id}$`));
 await page.getByRole('button',{name:'Confirm order'}).focus();await page.keyboard.press('Enter');await expect(page.getByRole('status')).toContainText('confirmed');
 await go(page,`/admin/orders/${id}`);await page.getByRole('button',{name:'Confirm order'}).focus();await page.keyboard.press('Enter');await page.getByRole('dialog').getByRole('button',{name:'Confirm order'}).focus();await page.keyboard.press('Enter');await expect(page.getByRole('status')).toContainText('confirmed');
 await go(page,'/admin/access/roles');await expect(page.getByLabel('Role').locator('option')).toHaveCount(2);await page.getByLabel('Role').focus();await page.keyboard.press('End');await page.keyboard.press('Enter');await expect(page.getByLabel('Role')).toHaveValue('r1');await page.getByLabel('Permission').focus();await page.keyboard.press('End');await page.keyboard.press('Enter');await expect(page.getByLabel('Permission')).toHaveValue('p1');await page.getByRole('button',{name:'Grant permission'}).focus();await page.keyboard.press('Enter');await expect(page.getByRole('status')).toContainText('updated');
});

test('T30 reproducible screen-reader semantics and announcements',async({page})=>{
 await install(page);await signIn(page,'/checkout');await page.getByRole('button',{name:'Submit order'}).click();
 await expect(page.getByRole('alert')).toContainText('Complete the required shipping address fields');await expect(page.getByLabel('recipient (required)')).toHaveAttribute('aria-invalid','true');
 await go(page,`/account/orders/${id}`);await page.getByRole('button',{name:'Confirm order'}).click();await expect(page.getByRole('status')).toHaveAttribute('aria-live','polite');
 await go(page,'/admin/access/roles');await page.getByLabel('Role').selectOption('r1');await page.getByLabel('Permission').selectOption('p1');await page.getByRole('button',{name:'Grant permission'}).click();await expect(page.getByRole('status')).toContainText('Role permission updated');
 await page.reload();await go(page,'/admin/orders');await expect(page.getByRole('heading',{name:'Sign in required'})).toBeVisible();
});
