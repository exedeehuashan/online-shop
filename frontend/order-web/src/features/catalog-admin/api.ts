import {apiFetch} from '../../app/http';
export type CreateProduct={name:string;description:string;price:number;currency:string;stockQuantity:number};
export const createProduct=(draft:CreateProduct)=>apiFetch<{id:string;name:string}>('/api/v1/products',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(draft)});
