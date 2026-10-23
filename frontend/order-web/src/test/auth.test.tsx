import {render,screen,waitFor} from '@testing-library/react';
import {expect,it,vi} from 'vitest';
import {AuthProvider,useAuth} from '../app/auth';
import {api} from '../features/orders/api/client';

function AuthStatus(){const auth=useAuth();return <output>{auth.status}</output>}
it.each([[401,'expired'],[403,'forbidden']] as const)('wires API status %s into usable auth state',async(status,expected)=>{vi.stubGlobal('fetch',vi.fn(async()=>new Response('{',{status,headers:{'Content-Type':'application/problem+json'}})));render(<AuthProvider value={{status:'authenticated',displayName:'Ada',permissions:new Set(['order:manage'])}}><AuthStatus/></AuthProvider>);await expect(api.detail('customer','order')).rejects.toMatchObject({code:'MALFORMED_RESPONSE'});await waitFor(()=>expect(screen.getByText(expected)).toBeInTheDocument())});
