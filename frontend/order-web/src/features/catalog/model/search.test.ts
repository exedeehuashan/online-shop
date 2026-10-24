import {describe,expect,it} from 'vitest';
import {parseCatalogSearch,serializeCatalogSearch,updateCatalogSearch} from './search';

describe('catalog search',()=>{
  it('normalizes, deduplicates and serializes supported values deterministically',()=>{
    const parsed=parseCatalogSearch(new URLSearchParams('page=2&sort=price-desc&category=tea&category=tea&attribute=size:00000000-0000-0000-0000-000000000001&availability=in-stock&minPrice=1.20&maxPrice=9&currency=nzd&ignored=yes'));
    expect(parsed).toEqual({page:2,sort:'price-desc',category:['tea'],attribute:['size:00000000-0000-0000-0000-000000000001'],availability:'in-stock',minPrice:'1.20',maxPrice:'9',currency:'NZD'});
    expect(serializeCatalogSearch(parsed).toString()).toBe('page=2&sort=price-desc&category=tea&attribute=size%3A00000000-0000-0000-0000-000000000001&availability=in-stock&minPrice=1.20&maxPrice=9&currency=NZD');
  });
  it('drops invalid and default values',()=>expect(serializeCatalogSearch(parseCatalogSearch(new URLSearchParams('page=-1&sort=nope&availability=any&minPrice=-4&currency=NZ'))).toString()).toBe(''));
  it('resets page whenever browse controls change',()=>expect(updateCatalogSearch({page:4,sort:'updated-desc',category:[],attribute:[],availability:'any'},{sort:'name-asc'}).page).toBe(0));
});
