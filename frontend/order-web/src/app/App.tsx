import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {RouterProvider} from '@tanstack/react-router';import {router} from './router';
export const queryClient=new QueryClient({defaultOptions:{mutations:{retry:false}}});
export function App(){return <QueryClientProvider client={queryClient}><RouterProvider router={router}/></QueryClientProvider>}
