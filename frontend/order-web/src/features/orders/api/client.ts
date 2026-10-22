import {mergeItemsBySku} from '../model/orders';
import type {CreateOrder,Order,OrderPage,Violation} from '../model/types';
import type {components} from './generated';
export class ApiProblem extends Error{constructor(public status:number,public code:string,detail:string,public violations:Violation[]=[]){super(detail);this.name='ApiProblem';}}
export class TransportError extends Error{constructor(message:string,public cause?:unknown){super(message);this.name='TransportError';}}
const path=(customerId:string)=>`/api/v1/customers/${encodeURIComponent(customerId)}/orders`;
export class OrderApi{
  constructor(private baseUrl=import.meta.env.VITE_ORDER_API_URL??''){}
  private async request<T>(url:string,init?:RequestInit):Promise<T>{let response:Response;try{const target=this.baseUrl?`${this.baseUrl}${url}`:new URL(url,globalThis.location?.origin??'http://localhost').toString();response=await fetch(target,init);}catch(error){if(error instanceof DOMException&&error.name==='AbortError')throw error;throw new TransportError('The order service could not be reached',error);}const contentType=response.headers.get('content-type')??'';let body:unknown;try{body=contentType.includes('json')?await response.json():await response.text();}catch{throw new ApiProblem(response.status,'MALFORMED_RESPONSE','The order service returned an unreadable response');}if(!response.ok){if(body&&typeof body==='object'){const p=body as Record<string,unknown>;throw new ApiProblem(response.status,String(p.code??'HTTP_ERROR'),String(p.detail??'Request failed'),Array.isArray(p.violations)?p.violations as Violation[]:[]);}throw new ApiProblem(response.status,'HTTP_ERROR',typeof body==='string'&&body?body:`Request failed (${response.status})`);}return body as T;}
  list(customerId:string,signal?:AbortSignal){return this.request<OrderPage>(`${path(customerId)}?page=0&size=20`,{signal});}
  detail(customerId:string,orderId:string,signal?:AbortSignal){return this.request<Order>(`${path(customerId)}/${encodeURIComponent(orderId)}`,{signal});}
  create(customerId:string,draft:CreateOrder,key:string){const body:components['schemas']['CreateRequest']={...draft,items:mergeItemsBySku(draft.items).map(item=>({...item,unitPrice:{...item.unitPrice,amount:Number(item.unitPrice.amount)}}))};return this.request<Order>(path(customerId),{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify(body)});}
  confirm(customerId:string,orderId:string,version:number){return this.request<Order>(`${path(customerId)}/${encodeURIComponent(orderId)}/confirmation`,{method:'POST',headers:{'If-Match':`"${version}"`}});}
  cancel(customerId:string,orderId:string,version:number,reason:string){return this.request<Order>(`${path(customerId)}/${encodeURIComponent(orderId)}/cancellation`,{method:'POST',headers:{'Content-Type':'application/json','If-Match':`"${version}"`},body:JSON.stringify({reason:reason.trim()})});}
}
export const api=new OrderApi();
