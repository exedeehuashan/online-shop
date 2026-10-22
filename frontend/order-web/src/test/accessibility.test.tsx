import type {ReactNode} from 'react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {fireEvent,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {axe} from 'vitest-axe';
import {afterEach,expect,it,vi} from 'vitest';
import {OrdersList} from '../routes/OrdersList';
import {NewOrder} from '../routes/NewOrder';
import {OrderDetail} from '../routes/OrderDetail';
import {buildOrderPage} from '../features/orders/fixtures/orders';

vi.mock('@tanstack/react-router',async importOriginal=>({...await importOriginal<typeof import('@tanstack/react-router')>(),useNavigate:()=>vi.fn(),useParams:()=>({orderId:'11111111-1111-1111-1111-111111111111'})}));
const renderView=(component:ReactNode)=>render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>{component}</QueryClientProvider>);
afterEach(()=>vi.unstubAllGlobals());

it('has no automated accessibility violations in the primary list state',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>new Response(JSON.stringify(buildOrderPage()),{headers:{'Content-Type':'application/json'}})));const rendered=renderView(<OrdersList/>);await screen.findByText('DRAFT');expect((await axe(rendered.container)).violations).toEqual([]);});
it('has no violations in create validation state',async()=>{const rendered=renderView(<NewOrder/>);await userEvent.click(screen.getByRole('button',{name:'Submit order'}));expect((await axe(rendered.container)).violations).toEqual([]);});
it('has no violations in detail and cancellation dialog states',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>new Response(JSON.stringify(buildOrderPage().content[0]),{headers:{'Content-Type':'application/json'}})));const rendered=renderView(<OrderDetail/>);await screen.findByText('Ada Lovelace');await userEvent.click(screen.getByRole('button',{name:'Cancel order'}));fireEvent.change(screen.getByLabelText('Reason'),{target:{value:'changed mind'}});expect((await axe(rendered.container)).violations).toEqual([]);});
