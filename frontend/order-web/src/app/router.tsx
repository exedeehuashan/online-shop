import {createRootRoute,createRoute,createRouter,Link,Outlet} from '@tanstack/react-router';
import {OrdersList} from '../routes/OrdersList';import {NewOrder} from '../routes/NewOrder';import {OrderDetail} from '../routes/OrderDetail';
function Shell(){return <><header><Link to="/orders" className="brand">Order desk</Link><nav aria-label="Primary"><Link to="/orders">Orders</Link><Link to="/orders/new">Create order</Link></nav><p className="uat">Local / controlled UAT only · customer-1</p></header><main id="main"><Outlet/></main></>}
const root=createRootRoute({component:Shell,notFoundComponent:()=> <section><h1>Page not found</h1><Link to="/orders">Back to orders</Link></section>});
const list=createRoute({getParentRoute:()=>root,path:'/orders',component:OrdersList});
const create=createRoute({getParentRoute:()=>root,path:'/orders/new',component:NewOrder});
const detail=createRoute({getParentRoute:()=>root,path:'/orders/$orderId',component:OrderDetail});
export const router=createRouter({routeTree:root.addChildren([list,create,detail]),defaultPreload:'intent'});
declare module '@tanstack/react-router'{interface Register{router:typeof router}}
