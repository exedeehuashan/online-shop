import {keepPreviousData,queryOptions} from '@tanstack/react-query';import {ApiProblem,catalogApi} from './client';import {catalogSearchKey,type CatalogSearch} from '../model/search';
const retry=(failures:number,error:unknown)=>!(error instanceof DOMException&&error.name==='AbortError')&&!(error instanceof ApiProblem&&error.status<500)&&failures<2;
export const browseOptions=(search:CatalogSearch)=>queryOptions({queryKey:catalogSearchKey(search),queryFn:({signal})=>catalogApi.browse(search,signal),staleTime:30_000,retry,placeholderData:keepPreviousData});
export const featuredOptions=()=>queryOptions({queryKey:['catalog','products','featured'],queryFn:({signal})=>catalogApi.featured(signal),staleTime:30_000,retry});
export const detailOptions=(slug:string)=>queryOptions({queryKey:['catalog','product','slug',slug],queryFn:({signal})=>catalogApi.detail(slug,signal),staleTime:30_000,retry});
