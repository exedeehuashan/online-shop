import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e-fullstack',workers:1,fullyParallel:false,use:{baseURL:process.env.FULLSTACK_BASE_URL??'http://127.0.0.1:28080',trace:'retain-on-failure'}});
