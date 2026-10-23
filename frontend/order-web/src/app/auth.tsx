import {createContext,useContext,useEffect,useState,type ReactNode} from 'react';
import {api} from '../features/orders/api/client';
export type AuthState={status:'anonymous'|'authenticated'|'expired'|'forbidden';displayName?:string;permissions:ReadonlySet<string>};
const anonymous:AuthState={status:'anonymous',permissions:new Set()};const AuthContext=createContext<AuthState>(anonymous);
export function AuthProvider({value=anonymous,children}:{value?:AuthState;children:ReactNode}){const [state,setState]=useState(value);useEffect(()=>{api.setAuthHandlers({onUnauthorized:()=>setState(current=>({...current,status:'expired',permissions:new Set()})),onForbidden:()=>setState(current=>({...current,status:'forbidden',permissions:new Set()}))});return()=>api.setAuthHandlers({});},[]);return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>}
export const useAuth=()=>useContext(AuthContext);
