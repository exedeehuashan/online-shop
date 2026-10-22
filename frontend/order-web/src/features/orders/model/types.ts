export type Money={amount:string;currency:string};
export type Address={recipient:string;line1:string;line2?:string;city:string;region?:string;postalCode:string;countryCode:string;phone?:string};
export type DraftItem={productId:string;sku:string;name:string;unitPrice:Money;quantity:number};
export type CreateOrder={customerName:string;items:DraftItem[];shippingAddress:Address};
export type OrderItem=DraftItem&{lineTotal:Money};
export type Order={id:string;customerId:string;customerName:string;status:'DRAFT'|'CONFIRMED'|'CANCELLED';items:OrderItem[];total:Money;shippingAddress:Address;cancellationReason:string|null;version:number;createdAt:string;updatedAt:string};
export type OrderPage={content:Order[];page:number;size:number};
export type Violation={field:string;message:string};
