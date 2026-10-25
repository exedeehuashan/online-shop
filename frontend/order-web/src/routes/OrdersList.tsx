import {useState} from 'react';
import {keepPreviousData,useQuery} from '@tanstack/react-query';
import {Link} from '@tanstack/react-router';
import {listOptions} from '../features/orders/api/queries';

export function OrdersList(){
 const [page,setPage]=useState(0);
 const q=useQuery({...listOptions(page),placeholderData:keepPreviousData});
 const hasNext=(q.data?.content.length??0)===20;
 return <section aria-labelledby="orders-heading"><p className="eyebrow">Account</p><h1 id="orders-heading">Your orders</h1>
  {q.isPending&&<div className="busy" role="status" aria-label="Loading orders">Loading orders…</div>}
  {q.isError&&<div role="alert" className="error-summary"><h2>Orders unavailable</h2><p>{q.error.message}</p><button onClick={()=>q.refetch()}>Retry</button></div>}
  {q.data&&q.data.content.length===0&&page===0&&<div className="panel"><h2>No orders yet</h2><p>Create your first order to see it here.</p><Link to="/checkout" className="button">Create an order</Link></div>}
  {q.data&&q.data.content.length>0&&<>{q.isFetching&&!q.isPending&&<p role="status">Loading page {page+1}…</p>}<div className="order-grid">{q.data.content.map(order=><article className="order-card" key={order.id}><h2><Link to="/account/orders/$orderId" params={{orderId:order.id}}>Order {order.id.slice(0,8)}</Link></h2><span className="status">{order.status}</span><span>{order.total.currency} {order.total.amount}</span><time dateTime={order.createdAt}>{new Date(order.createdAt).toLocaleString()}</time></article>)}</div></>}
  {q.data&&(page>0||hasNext)&&<nav className="pagination" aria-label="Order pages"><button className="secondary" disabled={page===0||q.isFetching} onClick={()=>setPage(value=>value-1)}>Previous page</button><span aria-live="polite">Page {page+1}</span><button className="secondary" disabled={!hasNext||q.isFetching} onClick={()=>setPage(value=>value+1)}>Next page</button></nav>}
 </section>
}
