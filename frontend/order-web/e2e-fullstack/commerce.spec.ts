import {expect,test,type APIRequestContext,type Page} from '@playwright/test';

let productSlug='';
async function token(request:APIRequestContext){const response=await request.get('http://127.0.0.1:18083/token');expect(response.ok()).toBeTruthy();return (await response.json()).access_token as string}
async function signIn(page:Page,path:string){await page.goto(path);await page.getByRole('button',{name:'Sign in'}).click();await expect(page).toHaveURL(new RegExp(path.replaceAll('/','\\/')));await expect(page.getByRole('heading',{name:'Sign in required'})).toHaveCount(0)}
async function spa(page:Page,path:string){await page.evaluate(next=>{history.pushState({},'',next);dispatchEvent(new PopStateEvent('popstate'))},path);await expect(page).toHaveURL(new RegExp(path.replaceAll('/','\\/')))}

test.beforeAll(async({request})=>{const bearer=await token(request);const name=`Browser Tea ${Date.now()}`;const created=await request.post('/api/v1/products',{headers:{Authorization:`Bearer ${bearer}`},data:{name,description:'Full-stack Playwright product',price:12.30,currency:'NZD',stockQuantity:10}});expect(created.status()).toBe(201);const id=(await created.json()).id as string;const catalog=await request.get('/api/v2/products?page=0&size=100');const body=await catalog.json() as {items:Array<{id:string;slug:string}>};productSlug=body.items.find(item=>item.id===id)!.slug});

test('T36 real variant to cart to checkout to self order actions',async({page})=>{
 await page.goto(`/products/${productSlug}`);
 await page.getByRole('button',{name:'Add to pending order'}).click();
 await page.getByRole('link',{name:'Cart',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Cart'})).toBeVisible();
 await page.getByRole('link',{name:'Checkout'}).click();
 await page.getByRole('button',{name:'Sign in'}).click();
 await expect(page).toHaveURL(/\/checkout$/);
 for(const [label,value] of [['recipient','Ada'],['line1','1 Queen Street'],['city','Auckland'],['postal Code','1010']] as const)await page.getByLabel(label,{exact:false}).fill(value);
 await page.getByRole('button',{name:'Submit order'}).click();
 await expect(page).toHaveURL(/\/account\/orders\/[0-9a-f-]+$/);
 await page.getByRole('button',{name:'Confirm order'}).click();
 await expect(page.getByRole('status')).toContainText('confirmed');
 await page.getByRole('button',{name:'Cancel order'}).click();
 await page.getByLabel('Reason').fill('Full-stack acceptance cancellation');
 await page.getByRole('button',{name:'Confirm cancellation'}).click();
 await expect(page.getByRole('status')).toContainText('cancelled');
});

test('T37 real admin order and user-role grant/revoke',async({page})=>{
 await signIn(page,'/admin/orders');
 const orderLink=page.getByRole('link',{name:/Order /}).first();await expect(orderLink).toBeVisible();await orderLink.click();await expect(page.getByRole('heading',{name:'Admin order detail'})).toBeVisible();
 await spa(page,'/admin/access/users');
 await page.getByLabel('User').selectOption({label:'Bootstrap administrator'});
 await page.getByLabel('Role').selectOption('20000000-0000-0000-0000-000000000001');
 await page.getByRole('button',{name:'Grant role'}).click();await expect(page.getByRole('status')).toContainText('updated');
 page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Revoke role'}).click();await expect(page.getByRole('status')).toContainText('updated');
});
