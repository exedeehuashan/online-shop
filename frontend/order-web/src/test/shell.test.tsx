import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {createMemoryHistory,RouterProvider} from '@tanstack/react-router';
import {afterEach,expect,it} from 'vitest';
import {createAppRouter} from '../app/router';

const mount=async(path:string)=>{const router=createAppRouter(createMemoryHistory({initialEntries:[path]}));await router.load();render(<RouterProvider router={router}/>);return router};
afterEach(()=>document.body.focus());

it('renders accessible commercial shell landmarks and skip link',async()=>{
  await mount('/forbidden');
  expect(screen.getByRole('link',{name:'Skip to content'})).toHaveAttribute('href','#main-content');
  expect(screen.getByRole('banner')).toBeInTheDocument();
  expect(screen.getByRole('navigation',{name:'Primary'})).toBeInTheDocument();
  expect(screen.getByRole('main')).toHaveAttribute('id','main-content');
  expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  expect(screen.getByText('Controlled UAT environment · Test data only')).toBeInTheDocument();
});

it.each([
  ['/orders','/account/orders'],
  ['/orders/new?campaign=spring#items','/checkout?campaign=spring#items'],
  ['/orders/11111111-1111-1111-1111-111111111111?from=email#timeline','/account/orders/11111111-1111-1111-1111-111111111111?from=email#timeline'],
])('redirects legacy order path %s without losing deep-link intent',async(from,to)=>{
  const router=await mount(from);
  await waitFor(()=>expect(router.state.location.href).toBe(to));
});

it('provides keyboard reachable navigation',async()=>{
  await mount('/not-a-route');
  await userEvent.tab();
  expect(screen.getByRole('link',{name:'Skip to content'})).toHaveFocus();
});
