import {createContext,useContext,type ReactNode} from 'react';
export type AuthState={status:'anonymous'|'authenticated'|'expired';displayName?:string;permissions:ReadonlySet<string>};
const anonymous:AuthState={status:'anonymous',permissions:new Set()};const AuthContext=createContext<AuthState>(anonymous);
export function AuthProvider({value=anonymous,children}:{value?:AuthState;children:ReactNode}){return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>}
export const useAuth=()=>useContext(AuthContext);
