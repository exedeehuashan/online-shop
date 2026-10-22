import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {render,screen} from '@testing-library/react';import userEvent from '@testing-library/user-event';import {afterEach,describe,expect,it,vi} from 'vitest';import {OrdersList} from '../routes/OrdersList';import {buildOrderPage} from '../features/orders/fixtures/orders';
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':status>=400?'application/problem+json':'application/json'}});
const renderList=()=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><OrdersList/></QueryClientProvider>);
afterEach(()=>vi.unstubAllGlobals());
describe('orders list states',()=>{
 it('shows an accessible loading state then orders',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>{await new Promise(r=>setTimeout(r,20));return response(buildOrderPage())}));renderList();expect(screen.getByRole('status',{name:'Loading orders'})).toBeInTheDocument();expect(await screen.findByText('DRAFT')).toBeInTheDocument();});
 it('links an empty state to creation',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response(buildOrderPage({orders:[]}))));renderList();expect(await screen.findByRole('heading',{name:'No orders yet'})).toBeInTheDocument();expect(screen.getByRole('link',{name:'Create an order'})).toHaveAttribute('href','/orders/new');});
 it('makes failures explicitly retryable',async()=>{let calls=0;vi.stubGlobal('fetch',vi.fn(async()=>++calls===1?response({status:400,code:'UNAVAILABLE',detail:'down'},400):response(buildOrderPage())));renderList();await userEvent.click(await screen.findByRole('button',{name:'Retry'}));expect(await screen.findByText('DRAFT')).toBeInTheDocument();});
});
