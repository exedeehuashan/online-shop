import type {ReactNode} from 'react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {fireEvent,render} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axeCore from 'axe-core';
import {afterEach,expect,it,vi} from 'vitest';
import {OrdersList} from '../routes/OrdersList';
import {NewOrder} from '../routes/NewOrder';
import {OrderDetail} from '../routes/OrderDetail';
import {buildOrderPage} from '../features/orders/fixtures/orders';
import pageHtml from '../../index.html?raw';

vi.mock('@tanstack/react-router',async importOriginal=>({...await importOriginal<typeof import('@tanstack/react-router')>(),Link:({to,params,children,className}:{to:string;params?:Record<string,string>;children:ReactNode;className?:string})=><a href={params?Object.entries(params).reduce((path,[key,value])=>path.replace(`$${key}`,value),to):to} className={className}>{children}</a>,useNavigate:()=>vi.fn(),useParams:()=>({orderId:'11111111-1111-1111-1111-111111111111'})}));
const renderView=(component:ReactNode,options?:Parameters<typeof render>[1])=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>{component}</QueryClientProvider>,options);
afterEach(()=>vi.unstubAllGlobals());

it('has no document-level accessibility violations in the primary list state',async()=>{document.open();document.write(pageHtml);document.close();vi.stubGlobal('fetch',vi.fn(async()=>new Response(JSON.stringify(buildOrderPage()),{headers:{'Content-Type':'application/json'}})));const root=document.getElementById('root');expect(root).not.toBeNull();const rendered=renderView(<OrdersList/>,{container:root!});await rendered.findByText('DRAFT');expect(document.compatMode).toBe('CSS1Compat');expect(document.title).toBe('Order desk');expect(document.documentElement.lang).toBe('en');expect(document.doctype?.name).toBe('html');expect((await axeCore.run(document)).violations).toEqual([]);});
it('has no violations in create validation state',async()=>{const rendered=renderView(<NewOrder/>);await userEvent.click(rendered.getByRole('button',{name:'Submit order'}));expect((await axeCore.run(rendered.container)).violations).toEqual([]);});
it('has no violations in detail and cancellation dialog states',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>new Response(JSON.stringify(buildOrderPage().content[0]),{headers:{'Content-Type':'application/json'}})));const rendered=renderView(<OrderDetail/>);await rendered.findByText('Ada Lovelace');await userEvent.click(rendered.getByRole('button',{name:'Cancel order'}));fireEvent.change(rendered.getByLabelText('Reason'),{target:{value:'changed mind'}});expect((await axeCore.run(rendered.container)).violations).toEqual([]);});
