import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import type {ReactNode} from 'react';
import {render,screen,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {AuthProvider,type AuthState} from '../app/auth';
import {buildOrder} from '../features/orders/fixtures/orders';
import {AccessPermissions,AdminOrderDetail} from '../routes/Admin';

vi.mock('@tanstack/react-router',async importOriginal=>({...await importOriginal<typeof import('@tanstack/react-router')>(),useParams:()=>({orderId:buildOrder().id})}));
const state=(permissions:string[]):AuthState=>({status:'authenticated',id:'user-1',displayName:'Operator',permissions:new Set(permissions)});
const mount=(ui:ReactNode,permissions:string[])=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><AuthProvider value={state(permissions)}>{ui}</AuthProvider></QueryClientProvider>);
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':status>=400?'application/problem+json':'application/json'}});
afterEach(()=>vi.unstubAllGlobals());

describe('administration remediation',()=>{
 it('does not issue protected queries before the permission guard passes',async()=>{const fetcher=vi.fn<typeof fetch>();vi.stubGlobal('fetch',fetcher);const first=mount(<AdminOrderDetail/>,['order:read:self']);expect(screen.getByRole('heading',{name:'You don’t have access'})).toBeInTheDocument();first.unmount();mount(<AccessPermissions/>,['order:read:self']);expect(screen.getByRole('heading',{name:'You don’t have access'})).toBeInTheDocument();expect(fetcher).not.toHaveBeenCalled()});
 it('confirms admin actions explicitly and exposes conflict recovery with its reference',async()=>{const fetcher=vi.fn<typeof fetch>(async(_input,init)=>init?.method==='POST'?json({detail:'stale order',correlationId:'ref-409'},409):json(buildOrder()));vi.stubGlobal('fetch',fetcher);mount(<AdminOrderDetail/>,['order:read:any','order:manage']);await screen.findByText('Ada Lovelace');await userEvent.click(screen.getByRole('button',{name:'Confirm order'}));const dialog=screen.getByRole('dialog',{name:'Confirm order'});await userEvent.click(within(dialog).getByRole('button',{name:'Confirm order'}));expect(await screen.findByRole('alert')).toHaveTextContent('Support reference: ref-409');expect(screen.getByRole('button',{name:'Refresh order'})).toBeEnabled()});
 it('requires an operator cancellation reason',async()=>{vi.stubGlobal('fetch',vi.fn<typeof fetch>(async()=>json(buildOrder())));mount(<AdminOrderDetail/>,['order:read:any','order:manage']);await screen.findByText('Ada Lovelace');await userEvent.click(screen.getByRole('button',{name:'Cancel order'}));const dialog=screen.getByRole('dialog',{name:'Cancel order'});await userEvent.click(within(dialog).getByRole('button',{name:'Confirm cancellation'}));expect(within(dialog).getByLabelText('Reason')).toHaveAttribute('aria-invalid','true')});
});
