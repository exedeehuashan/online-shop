import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({plugins:[react()],server:{proxy:{'/api':{target:process.env.ORDER_API_PROXY_TARGET??'http://127.0.0.1:8080',changeOrigin:true}}},test:{environment:'jsdom',environmentOptions:{jsdom:{url:'http://localhost/'}},setupFiles:'./src/test/setup.ts',css:true,exclude:['e2e/**','e2e-fullstack/**','node_modules/**']}});
