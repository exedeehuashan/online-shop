import {useState,useSyncExternalStore} from 'react';
import {Link} from '@tanstack/react-router';
import {cartStore} from '../features/cart/model/cart';
const store=cartStore();
export function Cart(){
 const items=useSyncExternalStore(store.subscribe,store.read,store.read),[announcement,setAnnouncement]=useState('');
 const total=items.reduce((n,x)=>n+Number(x.unitPrice.amount)*x.quantity,0);
 return <section><p className="eyebrow">Your selection</p><h1>Cart</h1><p className="field-hint">Prices and availability are snapshots. Items are not reserved and stock is not guaranteed until the order is accepted.</p><p className="sr-only" role="status" aria-live="polite">{announcement}</p>{items.length===0?<div className="panel"><h2>Your cart is empty</h2><Link className="button" to="/products">Browse products</Link></div>:<><ul className="order-grid">{items.map(x=><li className="order-card" key={x.skuId}><h2>{x.name}</h2><p>SKU {x.sku}</p><label htmlFor={`quantity-${x.skuId}`}>Quantity</label><input id={`quantity-${x.skuId}`} type="number" min="1" max="99" value={x.quantity} onChange={e=>{const quantity=Number(e.target.value);store.setQuantity(x.skuId,quantity);if(Number.isInteger(quantity)&&quantity>=1&&quantity<=99)setAnnouncement(`${x.name} quantity changed to ${quantity}.`)}}/><p>{x.unitPrice.currency} {x.unitPrice.amount} each</p><button className="secondary" onClick={()=>{store.remove(x.skuId);setAnnouncement(`${x.name} removed from cart.`)}}>Remove {x.name}</button></li>)}</ul><p><strong>Total: NZD {total.toFixed(2)}</strong> (tax inclusive)</p><div className="actions"><Link className="button" to="/checkout">Checkout</Link><button className="secondary" onClick={()=>{store.clear();setAnnouncement('Cart cleared.')}}>Clear cart</button></div></>}</section>
}
