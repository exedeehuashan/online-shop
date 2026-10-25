import {cartStore,type CartItem} from '../../cart/model/cart';
export type PendingSelection=Omit<CartItem,'selectedAttributes'> & {selectedAttributes?:Record<string,string>};
export function browserPendingSelectionStore(){const cart=cartStore();return {read:()=>cart.read(),add:(item:PendingSelection,max=99)=>cart.add({...item,selectedAttributes:item.selectedAttributes??{}},max)}}
