import {queryOptions} from '@tanstack/react-query';import {api,ApiProblem} from './client';
const retry=(count:number,error:Error)=>count<2&&(!(error instanceof ApiProblem)||error.status>=500);
export const orderKeys={list:['me','orders',{page:0,size:20}] as const,detail:(id:string)=>['me','orders',id] as const};
export const listOptions=()=>queryOptions({queryKey:orderKeys.list,queryFn:({signal})=>api.list(signal),retry});
export const detailOptions=(id:string)=>queryOptions({queryKey:orderKeys.detail(id),queryFn:({signal})=>api.detail(id,signal),retry});
