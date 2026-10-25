import {queryOptions} from '@tanstack/react-query';import {api,ApiProblem} from './client';
const retry=(count:number,error:Error)=>count<2&&(!(error instanceof ApiProblem)||error.status>=500);
export const orderKeys={list:['me','orders','list'] as const,listPage:(page:number)=>['me','orders','list',{page,size:20}] as const,detail:(id:string)=>['me','orders',id] as const};
export const listOptions=(page=0)=>queryOptions({queryKey:orderKeys.listPage(page),queryFn:({signal})=>api.list(page,signal),retry});
export const detailOptions=(id:string)=>queryOptions({queryKey:orderKeys.detail(id),queryFn:({signal})=>api.detail(id,signal),retry});
