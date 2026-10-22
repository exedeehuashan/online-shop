import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {fireEvent,render,screen,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {NewOrder} from '../routes/NewOrder';

const navigate=vi.fn();
vi.mock('@tanstack/react-router',async importOriginal=>({...await importOriginal<typeof import('@tanstack/react-router')>(),useNavigate:()=>navigate}));
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':status>=400?'application/problem+json':'application/json'}});
const renderCreate=()=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><NewOrder/></QueryClientProvider>);
const fillRequired=()=>{for(const [label,value] of [['Customer name','Ada'],['Product reference','product-1'],['SKU','SKU-1'],['Product name','Phone'],['Unit price (NZD)','10.00'],['Recipient','Ada'],['Address line 1','1 Queen St'],['City','Auckland'],['Postal code','1010']] as const)fireEvent.change(screen.getByLabelText(label,{selector:'input'}),{target:{value}});};

afterEach(()=>{vi.unstubAllGlobals();navigate.mockReset();});
describe('create order',()=>{
  it('focuses a linked summary and associates every item error with its input',async()=>{renderCreate();fillRequired();fireEvent.change(screen.getByLabelText('Product reference'),{target:{value:''}});await userEvent.click(screen.getByRole('button',{name:'Submit order'}));const alert=screen.getByRole('alert');expect(alert.parentElement).toHaveFocus();expect(within(alert).getByRole('link',{name:'Product reference is required'})).toHaveAttribute('href','#productId-0');expect(screen.getByLabelText('Product reference')).toHaveAttribute('aria-invalid','true');expect(screen.getByLabelText('Product reference')).toHaveAccessibleDescription('Product reference is required');});
  it('rotates the idempotency key after add/remove and retains input on a retryable failure',async()=>{const keys:string[]=[];vi.stubGlobal('fetch',vi.fn(async(_url:string,init?:RequestInit)=>{keys.push(new Headers(init?.headers).get('Idempotency-Key')??'');return response({status:503,detail:'try later'},503)}));renderCreate();fillRequired();await userEvent.click(screen.getByRole('button',{name:'Add another item'}));for(const [label,value] of [['Product reference','product-2'],['SKU','SKU-2'],['Product name','Case'],['Unit price (NZD)','2.00']] as const)fireEvent.change(screen.getAllByLabelText(label)[1],{target:{value}});await userEvent.click(screen.getByRole('button',{name:'Submit order'}));expect(await screen.findByText(/Could not create order/)).toBeInTheDocument();await userEvent.click(screen.getByRole('button',{name:'Remove item 2'}));await userEvent.click(screen.getByRole('button',{name:'Submit order'}));expect(keys).toHaveLength(2);expect(keys[1]).not.toBe(keys[0]);expect(screen.getByLabelText('Customer name')).toHaveValue('Ada');});
  it('retains values and exposes retry for server validation',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>response({status:422,code:'DUPLICATE_SKU',detail:'duplicate'},422)));renderCreate();fillRequired();await userEvent.click(screen.getByRole('button',{name:'Submit order'}));expect(await screen.findByText(/Order validation failed: duplicate/)).toBeInTheDocument();expect(screen.getByLabelText('SKU')).toHaveValue('SKU-1');expect(screen.getByRole('button',{name:'Retry submission'})).toBeEnabled();});
});
