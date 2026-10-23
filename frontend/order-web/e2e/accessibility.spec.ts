import {expect,test} from '@playwright/test';
import axeCore from 'axe-core';

const orderPage={content:[],page:0,size:20,totalElements:0,totalPages:0};

test('real shell is axe-clean and all visible targets are at least 44px at 320px',async({page})=>{
  await page.setViewportSize({width:320,height:800});
  await page.addInitScript({content:axeCore.source});
  await page.route('**/api/v1/customers/**/orders?**',route=>route.fulfill({json:orderPage}));
  for(const path of ['/','/products','/forbidden','/offline','/not-a-route','/account/orders']){
    await page.goto(path);
    await expect(page.locator('main')).toBeVisible();
    const violations=await page.evaluate(async()=>{const axe=(globalThis as typeof globalThis&{axe:{run:(context:Document)=>Promise<{violations:Array<{id:string}>}>}}).axe;return (await axe.run(document)).violations.map(violation=>violation.id)});
    expect(violations,`axe violations at ${path}`).toEqual([]);
    const undersized=await page.locator('a[href],button,input,select,textarea').evaluateAll(elements=>elements.filter(element=>{const rect=element.getBoundingClientRect();const style=getComputedStyle(element);return style.visibility!=='hidden'&&style.display!=='none'&&rect.width>0&&rect.height>0&&(rect.width<44||rect.height<44)}).map(element=>{const rect=element.getBoundingClientRect();return {text:element.getAttribute('aria-label')??element.textContent?.trim(),width:rect.width,height:rect.height}}));
    expect(undersized,`undersized targets at ${path}`).toEqual([]);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(320);
  }
});
