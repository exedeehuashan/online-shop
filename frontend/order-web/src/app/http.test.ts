import {afterEach,describe,expect,it,vi} from 'vitest';
import {apiFetch,bearerAllowed,configureTransport} from './http';

afterEach(()=>{configureTransport({token:()=>undefined,unauthorized:()=>{},forbidden:()=>{}});vi.unstubAllGlobals()});

describe('bearer endpoint allow-list',()=>{
 it.each(['/api/v1/me','/api/v1/admin/users?page=0&size=100','/api/v1/admin/users/11111111-1111-1111-1111-111111111111/roles/22222222-2222-2222-2222-222222222222','/api/v2/me/orders/11111111-1111-1111-1111-111111111111/confirmation'])('allows an approved same-origin path: %s',path=>expect(bearerAllowed(new URL(path,location.origin))).toBe(true));
 it.each(['/api/v1/unexpected','/api/v2/other','/api/uat/token','/image.png','https://foreign.invalid/api/v1/me'])('rejects an unapproved bearer destination: %s',path=>expect(bearerAllowed(new URL(path,location.origin))).toBe(false));
 it('injects the token only for approved paths',async()=>{configureTransport({token:()=> 'qa-secret-token'});const fetcher=vi.fn<typeof fetch>(async()=>new Response('{}',{headers:{'Content-Type':'application/json'}}));await apiFetch('/api/v1/me',{}, {fetcher});await apiFetch('/api/v1/unexpected',{}, {fetcher});expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer qa-secret-token');expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('Authorization')).toBeNull()});
});
