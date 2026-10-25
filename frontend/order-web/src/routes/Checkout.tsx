import {FormEvent,useMemo,useRef,useState,useSyncExternalStore} from 'react';
import {useMutation,useQueryClient} from '@tanstack/react-query';
import {Link,useNavigate} from '@tanstack/react-router';
import {useAuth} from '../app/auth';
import {cartStore} from '../features/cart/model/cart';
import {api,ApiProblem} from '../features/orders/api/client';
import {orderKeys} from '../features/orders/api/queries';
const store=cartStore();
const empty={recipient:'',line1:'',line2:'',city:'',region:'',postalCode:'',countryCode:'NZ',phone:''};
type AddressField=keyof typeof empty;
const requiredFields:AddressField[]=['recipient','line1','city','postalCode'];
const fieldLabel=(field:AddressField)=>field.replace(/([A-Z])/g,' $1');

export function Checkout(){
 const items=useSyncExternalStore(store.subscribe,store.read,store.read),auth=useAuth();
 const [address,setAddress]=useState(empty),[error,setError]=useState(''),[invalid,setInvalid]=useState<AddressField[]>([]),[key,setKey]=useState(()=>crypto.randomUUID());
 const summary=useRef<HTMLDivElement>(null),navigate=useNavigate(),qc=useQueryClient(),ids=useMemo(()=>items.map(x=>x.skuId),[items]);
 const edit=(field:AddressField,value:string)=>{setAddress(current=>({...current,[field]:value}));setInvalid(fields=>fields.filter(value=>value!==field));setKey(crypto.randomUUID());setError('')};
 const editQuantity=(skuId:string,name:string,value:number)=>{store.setQuantity(skuId,value);if(Number.isInteger(value)&&value>=1&&value<=99){setKey(crypto.randomUUID());setError('')}else setError(`${name} quantity must be from 1 to 99.`)};
 const draft={customerName:auth.displayName??'Signed-in customer',items:items.map(({productId,sku,name,unitPrice,quantity})=>({productId,sku,name,unitPrice,quantity})),shippingAddress:address};
 const mutation=useMutation({mutationFn:()=>api.create(draft,key),retry:false,onSuccess:async order=>{qc.setQueryData(orderKeys.detail(order.id),order);await navigate({to:'/account/orders/$orderId',params:{orderId:order.id}});store.removeSubmitted(ids)},onError:e=>{setError(e instanceof ApiProblem?e.message+(e.correlationId?` (reference ${e.correlationId})`:''):'The result is unknown. Your cart is unchanged; retry safely with the same request key.');setTimeout(()=>summary.current?.focus())}});
 const submit=(e:FormEvent)=>{e.preventDefault();const missing=requiredFields.filter(field=>!address[field].trim());if(missing.length){setInvalid(missing);setError('Complete the required shipping address fields.');setTimeout(()=>summary.current?.focus());return}setInvalid([]);mutation.mutate()};
 if(!items.length)return <section><h1>Checkout</h1><div className="panel"><h2>Your cart is empty</h2><Link className="button" to="/cart">Return to cart</Link></div></section>;
 const total=items.reduce((sum,item)=>sum+Number(item.unitPrice.amount)*item.quantity,0);
 return <section><h1>Checkout</h1><p>Ordering as <strong>{draft.customerName}</strong>. Prices are NZD, tax inclusive. Customer identity is taken from your session.</p>
  {error&&<div ref={summary} tabIndex={-1} role="alert" className="error-summary"><h2>Order not submitted</h2><p>{error}</p>{invalid.length>0&&<ul>{invalid.map(field=><li key={field}><a href={`#checkout-${field}`}>{fieldLabel(field)}</a> is required.</li>)}</ul>}{mutation.isError&&<button onClick={()=>mutation.mutate()}>Retry submission</button>}</div>}
  <ul>{items.map(item=><li key={item.skuId}><strong>{item.name}</strong> ({item.sku}) — {item.unitPrice.currency} {item.unitPrice.amount} each<label htmlFor={`checkout-quantity-${item.skuId}`}>Quantity for {item.name}</label><input id={`checkout-quantity-${item.skuId}`} type="number" min="1" max="99" value={item.quantity} onChange={event=>editQuantity(item.skuId,item.name,Number(event.target.value))}/></li>)}</ul>
  <p><strong>Total: NZD {total.toFixed(2)}</strong> (tax inclusive)</p>
  <form onSubmit={submit} noValidate><h2>Shipping address</h2>{(Object.keys(empty) as AddressField[]).map(field=>{const required=requiredFields.includes(field),bad=invalid.includes(field);return <div key={field}><label htmlFor={`checkout-${field}`}>{fieldLabel(field)}{required?' (required)':''}</label><input id={`checkout-${field}`} value={address[field]} required={required} aria-invalid={bad||undefined} aria-describedby={bad?`checkout-${field}-error`:undefined} onChange={event=>edit(field,event.target.value)}/>{bad&&<p id={`checkout-${field}-error`} className="field-error">Enter {fieldLabel(field)}.</p>}</div>})}<button disabled={mutation.isPending}>{mutation.isPending?'Submitting order…':'Submit order'}</button></form>
 </section>;
}
