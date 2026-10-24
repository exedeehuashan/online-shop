import {FormEvent,useMemo,useRef,useState,useSyncExternalStore} from 'react';
import {useMutation,useQueryClient} from '@tanstack/react-query';
import {Link,useNavigate} from '@tanstack/react-router';
import {useAuth} from '../app/auth';
import {cartStore} from '../features/cart/model/cart';
import {api,ApiProblem} from '../features/orders/api/client';
import {orderKeys} from '../features/orders/api/queries';
const store=cartStore();
const empty={recipient:'',line1:'',line2:'',city:'',region:'',postalCode:'',countryCode:'NZ',phone:''};
export function Checkout(){
 const items=useSyncExternalStore(store.subscribe,store.read,store.read),auth=useAuth();
 const [address,setAddress]=useState(empty),[error,setError]=useState(''),[key,setKey]=useState(()=>crypto.randomUUID());
 const summary=useRef<HTMLDivElement>(null),navigate=useNavigate(),qc=useQueryClient(),ids=useMemo(()=>items.map(x=>x.skuId),[items]);
 const edit=(field:keyof typeof empty,value:string)=>{setAddress(a=>({...a,[field]:value}));setKey(crypto.randomUUID());setError('')};
 const draft={customerName:auth.displayName??'Signed-in customer',items:items.map(({productId,sku,name,unitPrice,quantity})=>({productId,sku,name,unitPrice,quantity})),shippingAddress:address};
 const mutation=useMutation({mutationFn:()=>api.create(draft,key),retry:false,onSuccess:async order=>{qc.setQueryData(orderKeys.detail(order.id),order);await navigate({to:'/account/orders/$orderId',params:{orderId:order.id}});store.removeSubmitted(ids)},onError:e=>{setError(e instanceof ApiProblem?e.message+(e.correlationId?` (reference ${e.correlationId})`:''):'The result is unknown. Your cart is unchanged; retry safely with the same request key.');setTimeout(()=>summary.current?.focus())}});
 const submit=(e:FormEvent)=>{e.preventDefault();if(!address.recipient.trim()||!address.line1.trim()||!address.city.trim()||!address.postalCode.trim()){setError('Complete recipient, address line 1, city, and postal code.');setTimeout(()=>summary.current?.focus());return}mutation.mutate()};
 if(!items.length)return <section><h1>Checkout</h1><div className="panel"><h2>Your cart is empty</h2><Link className="button" to="/cart">Return to cart</Link></div></section>;
 return <section><h1>Checkout</h1><p>Ordering as <strong>{draft.customerName}</strong>. Prices are NZD, tax inclusive. Customer identity is taken from your session.</p>{error&&<div ref={summary} tabIndex={-1} role="alert" className="error-summary"><h2>Order not submitted</h2><p>{error}</p>{mutation.isError&&<button onClick={()=>mutation.mutate()}>Retry submission</button>}</div>}<ul>{items.map(x=><li key={x.skuId}>{x.name} ({x.sku}) × {x.quantity} — {x.unitPrice.currency} {x.unitPrice.amount}</li>)}</ul><form onSubmit={submit} noValidate><h2>Shipping address</h2>{(['recipient','line1','line2','city','region','postalCode','countryCode','phone'] as const).map(field=><div key={field}><label htmlFor={field}>{field.replace(/([A-Z])/g,' $1')}</label><input id={field} value={address[field]} onChange={e=>edit(field,e.target.value)}/></div>)}<button disabled={mutation.isPending}>{mutation.isPending?'Submitting order…':'Submit order'}</button></form></section>;
}
