import type { components } from '../api/generated';

type Api = components['schemas'];
type RequiredMoney<T extends Api['MoneyRequest']|Api['MoneyResponse']> = Required<T>;
export type Money = RequiredMoney<Api['MoneyResponse']>;
export type Address = Required<Pick<Api['AddressResponse'],'recipient'|'line1'|'city'|'postalCode'|'countryCode'>> & Pick<Api['AddressResponse'],'line2'|'region'|'phone'>;
export type DraftItem = Omit<Required<Api['ItemRequest']>,'unitPrice'> & {unitPrice: Omit<RequiredMoney<Api['MoneyRequest']>,'amount'> & {amount:string}};
export type CreateOrder = Omit<Required<Api['CreateRequest']>,'items'|'shippingAddress'> & {items:DraftItem[];shippingAddress:Address};
export type OrderItem = Omit<Required<Api['ItemResponse']>,'unitPrice'|'lineTotal'> & {unitPrice:Money;lineTotal:Money};
export type Order = Omit<Required<Api['OrderResponse']>,'status'|'items'|'total'|'shippingAddress'|'cancellationReason'> & {status:'DRAFT'|'CONFIRMED'|'CANCELLED';items:OrderItem[];total:Money;shippingAddress:Address;cancellationReason:string|null};
export type OrderPage = Omit<Required<Api['PageResponse']>,'content'> & {content:Order[]};
export type Violation = {field:string;message:string};
