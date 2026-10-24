import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {createMemoryHistory,RouterProvider} from '@tanstack/react-router';
import {render} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axeCore from 'axe-core';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {createAppRouter} from '../app/router';
import {buildOrderPage} from '../features/orders/fixtures/orders';

const response=(body:unknown)=>new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}});
const mount=async(path:string)=>{const router=createAppRouter(createMemoryHistory({initialEntries:[path]}));await router.load();return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><RouterProvider router={router}/></QueryClientProvider>)};
const expectDocumentAccessible=async()=>expect((await axeCore.run(document)).violations).toEqual([]);
afterEach(()=>vi.unstubAllGlobals());
beforeEach(()=>{document.documentElement.lang='en';document.title='Northstar market'});

it.each(['/','/products','/products/green-tea','/forbidden','/offline','/not-a-route'])('has no document-level shell violations on %s',async path=>{const rendered=await mount(path);if(path==='/products')await rendered.findByText('Green Tea');if(path.includes('green-tea'))await rendered.findByRole('heading',{name:'Green Tea'});await expectDocumentAccessible()});
it('has no document-level violations in the primary list state',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response(buildOrderPage())));const rendered=await mount('/account/orders');await rendered.findByText('DRAFT');await expectDocumentAccessible()});
it('has no document-level violations in create validation state',async()=>{const rendered=await mount('/checkout');await userEvent.click(await rendered.findByRole('button',{name:'Submit order'}));await expectDocumentAccessible()});
it('has no document-level violations in detail and cancellation dialog states',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response(buildOrderPage().content[0])));const rendered=await mount('/account/orders/11111111-1111-1111-1111-111111111111');await rendered.findByText('Ada Lovelace');await userEvent.click(rendered.getByRole('button',{name:'Cancel order'}));await expectDocumentAccessible()});
