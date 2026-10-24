import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {RouterProvider} from '@tanstack/react-router';import {router} from './router';import {AuthProvider} from './auth';
export const queryClient=new QueryClient({defaultOptions:{queries:{retry:2,refetchOnReconnect:true},mutations:{retry:false}}});
export function App(){return <QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router}/></AuthProvider></QueryClientProvider>}
