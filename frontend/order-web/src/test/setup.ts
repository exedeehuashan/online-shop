import '@testing-library/jest-dom/vitest';import 'vitest-axe/extend-expect';import {cleanup} from '@testing-library/react';import {afterAll,afterEach,beforeAll} from 'vitest';import {server} from './server';
beforeAll(()=>server.listen({onUnhandledRequest:'error'}));afterEach(()=>{cleanup();server.resetHandlers();});afterAll(()=>server.close());
