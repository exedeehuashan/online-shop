import {queryOptions} from '@tanstack/react-query';import {api,ApiProblem} from './client';
export const customerId=import.meta.env.VITE_ORDER_CUSTOMER_ID??'customer-1';
const retry=(count:number,error:Error)=>count<2&&(!(error instanceof ApiProblem)||error.status>=500);
export const orderKeys={list:['orders',customerId,{page:0,size:20}] as const,detail:(id:string)=>['orders',customerId,id] as const};
export const listOptions=()=>queryOptions({queryKey:orderKeys.list,queryFn:({signal})=>api.list(customerId,signal),retry});
export const detailOptions=(id:string)=>queryOptions({queryKey:orderKeys.detail(id),queryFn:({signal})=>api.detail(customerId,id,signal),retry});
