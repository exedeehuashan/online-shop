import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {createMemoryHistory,createRootRoute,createRoute,createRouter,Outlet,RouterProvider} from '@tanstack/react-router';
import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {buildOrderPage} from '../features/orders/fixtures/orders';
import {OrdersList} from '../routes/OrdersList';

const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':status>=400?'application/problem+json':'application/json'}});
const renderList=async()=>{const root=createRootRoute({component:()=> <Outlet/>});const list=createRoute({getParentRoute:()=>root,path:'/orders',component:OrdersList});const create=createRoute({getParentRoute:()=>root,path:'/orders/new',component:()=> <h1>Create order</h1>});const detail=createRoute({getParentRoute:()=>root,path:'/orders/$orderId',component:()=> <h1>Order detail</h1>});const router=createRouter({routeTree:root.addChildren([list,create,detail]),history:createMemoryHistory({initialEntries:['/orders']})});await router.load();render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><RouterProvider router={router}/></QueryClientProvider>);return router;};

afterEach(()=>vi.unstubAllGlobals());
describe('orders list states',()=>{
 it('shows an accessible loading state then orders',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>{await new Promise(r=>setTimeout(r,20));return response(buildOrderPage())}));await renderList();expect(screen.getByRole('status',{name:'Loading orders'})).toBeInTheDocument();expect(await screen.findByText('DRAFT')).toBeInTheDocument();});
 it('uses client-side routing from the empty state to creation',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response(buildOrderPage({orders:[]}))));const router=await renderList();expect(await screen.findByRole('heading',{name:'No orders yet'})).toBeInTheDocument();await userEvent.click(screen.getByRole('link',{name:'Create an order'}));await waitFor(()=>expect(router.state.location.pathname).toBe('/orders/new'));});
 it('uses client-side routing from an order card to its detail',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response(buildOrderPage())));const router=await renderList();await userEvent.click(await screen.findByRole('link',{name:/Order 11111111/}));await waitFor(()=>expect(router.state.location.pathname).toBe('/orders/11111111-1111-1111-1111-111111111111'));});
 it('makes failures explicitly retryable',async()=>{let calls=0;vi.stubGlobal('fetch',vi.fn(async()=>++calls===1?response({status:400,code:'UNAVAILABLE',detail:'down'},400):response(buildOrderPage())));await renderList();await userEvent.click(await screen.findByRole('button',{name:'Retry'}));expect(await screen.findByText('DRAFT')).toBeInTheDocument();});
});
