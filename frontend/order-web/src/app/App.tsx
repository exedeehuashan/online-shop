import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {RouterProvider} from '@tanstack/react-router';import {router} from './router';import {AuthProvider} from './auth';
export const queryClient=new QueryClient({defaultOptions:{queries:{retry:2},mutations:{retry:false}}});
export function App(){return <AuthProvider><QueryClientProvider client={queryClient}><RouterProvider router={router}/></QueryClientProvider></AuthProvider>}
