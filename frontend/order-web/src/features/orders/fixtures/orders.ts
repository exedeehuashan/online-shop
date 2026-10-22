import type {Address,CreateOrder,DraftItem,Money,Order,OrderItem,OrderPage} from '../model/types';
export const buildMoney=(overrides:Partial<Money>={}):Money=>({amount:'99.90',currency:'NZD',...overrides});
export const buildAddress=(overrides:Partial<Address>={}):Address=>({recipient:'Ada Lovelace',line1:'1 Queen Street',line2:'',city:'Auckland',region:'Auckland',postalCode:'1010',countryCode:'NZ',phone:'0210000000',...overrides});
export const buildDraftItem=(overrides:Partial<DraftItem>={}):DraftItem=>({productId:'product-1',sku:'SKU-1',name:'Phone',unitPrice:buildMoney(),quantity:1,...overrides});
export const buildOrderItem=(overrides:Partial<OrderItem>={}):OrderItem=>({...buildDraftItem(),lineTotal:buildMoney(),...overrides});
export const buildDraft=(overrides:Partial<CreateOrder>={}):CreateOrder=>({customerName:'Ada Lovelace',items:[buildDraftItem()],shippingAddress:buildAddress(),...overrides});
export const buildOrder=(overrides:Partial<Order>={}):Order=>({id:'11111111-1111-1111-1111-111111111111',customerId:'customer-1',customerName:'Ada Lovelace',status:'DRAFT',items:[buildOrderItem()],total:buildMoney(),shippingAddress:buildAddress(),cancellationReason:null,version:0,createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-01T00:00:00Z',...overrides});
export const buildOrderPage=(overrides:Partial<OrderPage>&{orders?:Order[]}={}):OrderPage=>{const {orders,...rest}=overrides;return {content:orders??[buildOrder()],page:0,size:20,...rest};};
