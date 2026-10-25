import { FormEvent, useMemo, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { api, ApiProblem } from '../features/orders/api/client';
import { orderKeys } from '../features/orders/api/queries';
import type { CreateOrder, DraftItem } from '../features/orders/model/types';
import { mergeItemsBySku, validateDraft } from '../features/orders/model/orders';

const blankItem = (): DraftItem => ({productId:'',sku:'',name:'',unitPrice:{amount:'',currency:'NZD'},quantity:1});
const initial: CreateOrder = {customerName:'',items:[blankItem()],shippingAddress:{recipient:'',line1:'',line2:'',city:'',region:'',postalCode:'',countryCode:'NZ',phone:''}};

export function NewOrder() {
  const [draft,setDraft]=useState(initial);
  const [errors,setErrors]=useState<Record<string,string>>({});
  const [serverError,setServerError]=useState('');
  const [key,setKey]=useState(()=>crypto.randomUUID());
  const summary=useRef<HTMLDivElement>(null);
  const navigate=useNavigate();
  const qc=useQueryClient();
  const rotateKey=()=>{setKey(crypto.randomUUID());setServerError('');};
  const mutation=useMutation({
    mutationFn:()=>api.create(draft,key),retry:false,
    onSuccess:async order=>{qc.setQueryData(orderKeys.detail(order.id),order);await qc.invalidateQueries({queryKey:orderKeys.list});await navigate({to:'/account/orders/$orderId',params:{orderId:order.id}});},
    onError:error=>{setServerError(error instanceof ApiProblem&&error.status===422?`Order validation failed: ${error.message}`:`Could not create order: ${error.message}`);setTimeout(()=>summary.current?.focus());}
  });
  const merged=useMemo(()=>{try{return mergeItemsBySku(draft.items)}catch{return null}},[draft.items]);
  const item=(index:number,patch:Partial<DraftItem>)=>{setDraft(d=>({...d,items:d.items.map((x,i)=>i===index?{...x,...patch,unitPrice:patch.unitPrice??x.unitPrice}:x)}));rotateKey();};
  const addItem=()=>{setDraft(d=>({...d,items:[...d.items,blankItem()]}));rotateKey();};
  const removeItem=(index:number)=>{setDraft(d=>({...d,items:d.items.filter((_,i)=>i!==index)}));rotateKey();};
  const submit=(e:FormEvent)=>{e.preventDefault();const found=validateDraft(draft);setErrors(found);setServerError('');if(Object.keys(found).length){setTimeout(()=>summary.current?.focus());return;}mutation.mutate();};
  const fieldError=(id:string)=>errors[id]&&<span id={`${id}-error`} className="field-error">{errors[id]}</span>;
  const inputA11y=(id:string)=>({'aria-invalid':errors[id]?true:undefined,'aria-describedby':errors[id]?`${id}-error`:undefined});

  return <section><h1>Create order</h1><p>Review the customer, item, price, and delivery snapshots before submitting.</p>
    <div aria-live="assertive" ref={summary} tabIndex={-1}>{(Object.keys(errors).length>0||serverError)&&<div className="error-summary" role="alert"><h2>Check the order</h2><p>{serverError||'Correct the highlighted fields.'}</p>{!serverError&&<ul>{Object.entries(errors).map(([id,message])=><li key={id}>{id==='items'?message:<a href={`#${id}`}>{message}</a>}</li>)}</ul>}{serverError&&<button onClick={()=>mutation.mutate()} disabled={mutation.isPending}>Retry submission</button>}</div>}</div>
    <form onSubmit={submit} noValidate>
      <label htmlFor="customerName">Customer name</label><input id="customerName" value={draft.customerName} onChange={e=>{setDraft(d=>({...d,customerName:e.target.value}));rotateKey();}} {...inputA11y('customerName')}/>{fieldError('customerName')}
      <h2>Items</h2>{draft.items.map((x,i)=><fieldset key={i}><legend>Item {i+1}</legend>
        <label htmlFor={`productId-${i}`}>Product reference</label><input id={`productId-${i}`} value={x.productId} onChange={e=>item(i,{productId:e.target.value})} {...inputA11y(`productId-${i}`)}/>{fieldError(`productId-${i}`)}
        <label htmlFor={`sku-${i}`}>SKU</label><input id={`sku-${i}`} value={x.sku} onChange={e=>item(i,{sku:e.target.value})} {...inputA11y(`sku-${i}`)}/>{fieldError(`sku-${i}`)}
        <label htmlFor={`name-${i}`}>Product name</label><input id={`name-${i}`} value={x.name} onChange={e=>item(i,{name:e.target.value})} {...inputA11y(`name-${i}`)}/>{fieldError(`name-${i}`)}
        <label htmlFor={`amount-${i}`}>Unit price (NZD)</label><input id={`amount-${i}`} inputMode="decimal" value={x.unitPrice.amount} onChange={e=>item(i,{unitPrice:{amount:e.target.value,currency:'NZD'}})} {...inputA11y(`amount-${i}`)}/>{fieldError(`amount-${i}`)}
        <label htmlFor={`quantity-${i}`}>Quantity</label><input id={`quantity-${i}`} type="number" min="1" step="1" value={x.quantity} onChange={e=>item(i,{quantity:Number(e.target.value)})} {...inputA11y(`quantity-${i}`)}/>{fieldError(`quantity-${i}`)}
        {draft.items.length>1&&<button type="button" className="secondary" onClick={()=>removeItem(i)}>Remove item {i+1}</button>}
      </fieldset>)}
      <button type="button" className="secondary" onClick={addItem}>Add another item</button>{errors.items&&<p className="field-error">{errors.items}</p>}{merged&&merged.length<draft.items.length&&<p role="status">Review combines {draft.items.length} rows into {merged.length} unique SKU.</p>}
      <h2>Shipping address</h2>{(['recipient','line1','line2','city','region','postalCode','countryCode','phone'] as const).map(field=><div key={field}><label htmlFor={field}>{({recipient:'Recipient',line1:'Address line 1',line2:'Address line 2',city:'City',region:'Region',postalCode:'Postal code',countryCode:'Country code',phone:'Phone'})[field]}</label><input id={field} value={draft.shippingAddress[field]??''} onChange={e=>{setDraft(d=>({...d,shippingAddress:{...d.shippingAddress,[field]:e.target.value}}));rotateKey();}} {...inputA11y(field)}/>{fieldError(field)}</div>)}
      <div className="actions"><button type="submit" disabled={mutation.isPending}>{mutation.isPending?'Submitting order…':'Submit order'}</button></div>
    </form>
  </section>;
}
