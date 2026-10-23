import type {ReactNode} from 'react';
import {Link,Outlet} from '@tanstack/react-router';
import {useAuth} from './auth';

export function AppShell({children}:{children?:ReactNode}){
  const auth=useAuth();
  const canOperate=auth.permissions.has('order:manage');
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <div className="uat-banner">Controlled UAT environment · Test data only</div>
    <header className="site-header"><div className="header-inner">
      <Link to="/" className="brand" aria-label="Northstar market home"><span aria-hidden="true" className="brand-mark">N</span><span>Northstar <small>market</small></span></Link>
      <nav aria-label="Primary"><Link to="/">Home</Link><Link to="/products">Shop</Link><Link to="/account/orders">Orders</Link>{canOperate&&<Link to="/admin/orders">Operations</Link>}</nav>
      <Link to="/checkout" className="header-action">Create order</Link>
    </div></header>
    <main id="main-content" tabIndex={-1}>{children??<Outlet/>}</main>
    <footer><div><strong>Northstar market</strong><p>Simple ordering for controlled UAT.</p></div><nav aria-label="Footer"><Link to="/offline">Connection help</Link><Link to="/forbidden">Access help</Link></nav><small>© 2026 Northstar. No production transactions.</small></footer>
  </div>;
}

export function HomePage(){return <section className="hero"><p className="eyebrow">Commerce, clearly delivered</p><h1>A dependable home for every order.</h1><p className="hero__copy">Browse-ready foundations and the order tools you already trust, now in one accessible workspace.</p><div className="actions"><Link className="button" to="/products">Browse products</Link><Link className="text-link" to="/account/orders">View orders</Link></div></section>}
export function ProductsPlaceholder(){return <section className="state"><p className="eyebrow">Catalog</p><h1>Products are on their way</h1><p>The commercial catalog launches in the next approved delivery slice. Existing ordering remains available.</p><Link className="button" to="/account/orders">View your orders</Link></section>}
export function ForbiddenPage(){return <section className="state state--error"><p className="error-code">403</p><h1>You don’t have access</h1><p>Your account is signed in, but this area needs another permission.</p><Link className="button" to="/">Return home</Link></section>}
export function OfflinePage(){return <section className="state"><p className="error-code">Offline</p><h1>Check your connection</h1><p>Your work has not been submitted. Reconnect, then try the action again.</p><button onClick={()=>globalThis.location?.reload()}>Try again</button></section>}
export function NotFoundPage(){return <section className="state"><p className="error-code">404</p><h1>Page not found</h1><p>The address may have changed or the page may no longer be available.</p><Link className="button" to="/">Return home</Link></section>}
