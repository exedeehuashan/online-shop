import {lazy,Suspense,type ReactNode} from 'react';import {RequirePermission} from '../app/auth';
const CheckoutPage=lazy(()=>import('./Checkout').then(x=>({default:x.Checkout}))),OrdersPage=lazy(()=>import('./OrdersList').then(x=>({default:x.OrdersList}))),DetailPage=lazy(()=>import('./OrderDetail').then(x=>({default:x.OrderDetail})));
const Protected=({children}:{children:ReactNode})=><RequirePermission permissions={['order:read:self']}><Suspense fallback={<p role="status">Loading…</p>}>{children}</Suspense></RequirePermission>;
export const ProtectedCheckout=()=> <Protected><CheckoutPage/></Protected>;export const ProtectedOrders=()=> <Protected><OrdersPage/></Protected>;export const ProtectedOrderDetail=()=> <Protected><DetailPage/></Protected>;
