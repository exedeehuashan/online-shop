import '@testing-library/jest-dom/vitest';import {cleanup} from '@testing-library/react';import {afterAll,afterEach,beforeAll,vi} from 'vitest';import {server} from './server';
if (!HTMLDialogElement.prototype.showModal) HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
if (!HTMLDialogElement.prototype.close) HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
beforeAll(()=>server.listen({onUnhandledRequest:'error'}));afterEach(()=>{cleanup();server.resetHandlers();vi.unstubAllGlobals();});afterAll(()=>server.close());
