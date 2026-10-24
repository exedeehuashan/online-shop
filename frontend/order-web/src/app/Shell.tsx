import {lazy,Suspense,type ReactNode} from 'react';
import {Link,Outlet} from '@tanstack/react-router';
import {useAuth} from './auth';

export function AppShell({children}:{children?:ReactNode}){
  const auth=useAuth();
  const canOperate=auth.permissions.has('order:manage');
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <aside className="uat-banner" aria-label="Environment">Controlled UAT environment · Test data only</aside>
    <header className="site-header"><div className="header-inner">
      <Link to="/" className="brand"><span aria-hidden="true" className="brand-mark">N</span><span>Northstar <small>market</small></span></Link>
      <nav aria-label="Primary"><Link to="/">Home</Link><Link to="/products">Shop</Link><Link to="/cart">Cart</Link><Link to="/account/orders">Orders</Link>{canOperate&&<Link to="/admin/orders">Operations</Link>}</nav>
      <Link to="/cart" className="header-action">View cart</Link>
    </div></header>
    {auth.status==='expired'&&<div className="alert" role="alert">Your session has expired. Sign in again before continuing.</div>}
    {auth.status==='forbidden'&&<div className="alert" role="alert">Your account does not have permission for that action.</div>}
    <main id="main-content" tabIndex={-1}>{children??<Outlet/>}</main>
    <footer><div><strong>Northstar market</strong><p>Simple ordering for controlled UAT.</p></div><nav aria-label="Footer"><Link to="/offline">Connection help</Link><Link to="/forbidden">Access help</Link></nav><small>© 2026 Northstar. No production transactions.</small></footer>
  </div>;
}

const FeaturedProducts=lazy(()=>import('../features/catalog/components/FeaturedProducts').then(m=>({default:m.FeaturedProducts})));
export function HomePage(){return <><section className="hero"><p className="eyebrow">Commerce, clearly delivered</p><h1>A dependable home for every order.</h1><p className="hero__copy">Browse the catalog and use the order tools you already trust, in one accessible workspace.</p><div className="actions"><Link className="button" to="/products">Browse products</Link><Link className="text-link" to="/account/orders">View orders</Link></div></section><Suspense fallback={<p className="busy">Loading featured products…</p>}><FeaturedProducts/></Suspense></>}
export function ForbiddenPage(){return <section className="state state--error"><p className="error-code">403</p><h1>You don’t have access</h1><p>Your account is signed in, but this area needs another permission.</p><Link className="button" to="/">Return home</Link></section>}
export function OfflinePage(){return <section className="state"><p className="error-code">Offline</p><h1>Check your connection</h1><p>Your work has not been submitted. Reconnect, then try the action again.</p><button onClick={()=>globalThis.location?.reload()}>Try again</button></section>}
export function NotFoundPage(){return <section className="state"><p className="error-code">404</p><h1>Page not found</h1><p>The address may have changed or the page may no longer be available.</p><Link className="button" to="/">Return home</Link></section>}
export function OperationsPage(){const auth=useAuth();return auth.permissions.has('order:manage')?<section className="state"><p className="eyebrow">Operations</p><h1>Order operations</h1><p>Administrative order tools will appear in the approved operations delivery.</p></section>:<ForbiddenPage/>}
