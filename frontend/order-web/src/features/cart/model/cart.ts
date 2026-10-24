export type CartItem={productId:string;skuId:string;sku:string;name:string;unitPrice:{amount:string;currency:string};quantity:number;selectedAttributes:Record<string,string>;image?:string};
const KEY='northstar.cart.v2',LEGACY='northstar.pending-order.v1',VERSION=2;
const valid=(v:unknown):v is CartItem=>{if(!v||typeof v!=='object')return false;const i=v as Record<string,unknown>,p=i.unitPrice as Record<string,unknown>|undefined;return typeof i.productId==='string'&&typeof i.skuId==='string'&&typeof i.sku==='string'&&typeof i.name==='string'&&Number.isInteger(i.quantity)&&Number(i.quantity)>0&&!!p&&typeof p.amount==='string'&&typeof p.currency==='string'&&!!i.selectedAttributes&&typeof i.selectedAttributes==='object'};
export class CartStore{
 private items:CartItem[]=[];private snapshot:CartItem[]=[];private listeners=new Set<()=>void>();
 constructor(private storage?:Storage){this.items=this.load();this.snapshot=this.clone()}
 private load(){try{const current=JSON.parse(this.storage?.getItem(KEY)??'null');if(current?.version===VERSION&&Array.isArray(current.items))return current.items.filter(valid);const legacy=JSON.parse(this.storage?.getItem(LEGACY)??'null');if(legacy?.version===1&&Array.isArray(legacy.items))return legacy.items.map((x:Record<string,unknown>)=>({...x,selectedAttributes:{}})).filter(valid)}catch{/* invalid or blocked storage starts empty */}return []}
 private clone(){return this.items.map(i=>({...i,unitPrice:{...i.unitPrice},selectedAttributes:{...i.selectedAttributes}}))}
 private save(){this.snapshot=this.clone();try{this.storage?.setItem(KEY,JSON.stringify({version:VERSION,items:this.items}));this.storage?.removeItem(LEGACY)}catch{/* memory fallback */}this.listeners.forEach(fn=>fn())}
 subscribe=(fn:()=>void)=>{this.listeners.add(fn);return()=>this.listeners.delete(fn)};
 read=()=>this.snapshot;
 add(item:CartItem,max=99){const found=this.items.find(i=>i.skuId===item.skuId);if(found)found.quantity=Math.min(max,found.quantity+item.quantity);else this.items.push({...item,quantity:Math.min(max,item.quantity)});this.save();return this.read()}
 setQuantity(id:string,quantity:number){if(!Number.isInteger(quantity)||quantity<1||quantity>99)return;const found=this.items.find(i=>i.skuId===id);if(found){found.quantity=quantity;this.save()}}
 remove(id:string){this.items=this.items.filter(i=>i.skuId!==id);this.save()}
 removeSubmitted(ids:string[]){const set=new Set(ids);this.items=this.items.filter(i=>!set.has(i.skuId));this.save()}
 clear(){this.items=[];this.save()}
}
let singleton:CartStore|undefined;
export function cartStore(){if(singleton)return singleton;let storage:Storage|undefined;try{storage=globalThis.sessionStorage}catch{/* use memory */}return singleton=new CartStore(storage)}
