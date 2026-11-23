import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import type { ProductDetail, ProductModifier } from '../services/api';

export interface CartItem {
  productId: number;
  name: string;
  imageUrl: string | null;
  modifiers: ProductModifier[];
  modifierIds: number[];
  quantity: number;
}

interface CartContextValue {
  items: CartItem[];
  itemCount: number;
  addItem: (product: ProductDetail, modifierIds: number[], quantity: number) => void;
  setItemQuantity: (productId: number, modifierIds: number[], quantity: number) => void;
  removeItem: (productId: number, modifierIds: number[]) => void;
  clearCart: () => void;
}

export const MIN_QUANTITY = 1;
export const MAX_QUANTITY = 99;
const MAX_LINES = 50;
const STORAGE_KEY = 'wr.cart';
const STORAGE_VERSION = 1;

const CartContext = createContext<CartContextValue | null>(null);

export function getCartItemKey(productId: number, modifierIds: number[]): string {
  return `${productId}:${[...modifierIds].sort((a, b) => a - b).join(',')}`;
}

export function isValidQuantity(quantity: number): boolean {
  return Number.isInteger(quantity) && quantity >= MIN_QUANTITY && quantity <= MAX_QUANTITY;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function parseStoredModifier(value: unknown): ProductModifier | null {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.id) ||
    (value.type !== 'SIZE' && value.type !== 'EXTRA') ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    typeof value.priceDelta !== 'number' ||
    !Number.isFinite(value.priceDelta) ||
    value.priceDelta < 0
  ) {
    return null;
  }

  return { id: value.id, type: value.type, name: value.name, priceDelta: value.priceDelta };
}

function parseStoredItem(value: unknown): CartItem | null {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.productId) ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    (value.imageUrl !== null && typeof value.imageUrl !== 'string') ||
    !Array.isArray(value.modifiers) ||
    typeof value.quantity !== 'number' ||
    !isValidQuantity(value.quantity)
  ) {
    return null;
  }

  const modifiers: ProductModifier[] = [];
  for (const storedModifier of value.modifiers) {
    const modifier = parseStoredModifier(storedModifier);
    if (!modifier) return null;
    modifiers.push(modifier);
  }

  return {
    productId: value.productId,
    name: value.name,
    imageUrl: value.imageUrl,
    modifiers,
    modifierIds: modifiers.map(({ id }) => id),
    quantity: value.quantity,
  };
}

// The cart is saved in localStorage. Only ids, names and quantities are kept:
// prices always come from a new quote. Broken or old data is ignored.
function readStoredCart(): CartItem[] {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (!saved) return [];

    const stored: unknown = JSON.parse(saved);
    if (!isRecord(stored) || stored.version !== STORAGE_VERSION || !Array.isArray(stored.items)) {
      return [];
    }

    const items: CartItem[] = [];
    const keys = new Set<string>();
    for (const storedItem of stored.items) {
      const item = parseStoredItem(storedItem);
      if (!item) continue;

      const key = getCartItemKey(item.productId, item.modifierIds);
      if (keys.has(key) || items.length >= MAX_LINES) continue;
      keys.add(key);
      items.push(item);
    }
    return items;
  } catch {
    return [];
  }
}

function writeStoredCart(items: CartItem[]) {
  try {
    if (items.length === 0) {
      window.localStorage.removeItem(STORAGE_KEY);
    } else {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ version: STORAGE_VERSION, items }));
    }
  } catch {
    // Storage is full or blocked (for example in private mode): the cart still works on this page.
  }
}

export function CartProvider({ children }: PropsWithChildren) {
  const [items, setItems] = useState<CartItem[]>(readStoredCart);

  useEffect(() => {
    writeStoredCart(items);
  }, [items]);

  useEffect(() => {
    function syncWithOtherTab(event: StorageEvent) {
      if (event.key === STORAGE_KEY || event.key === null) setItems(readStoredCart());
    }

    window.addEventListener('storage', syncWithOtherTab);
    return () => window.removeEventListener('storage', syncWithOtherTab);
  }, []);

  function addItem(product: ProductDetail, modifierIds: number[], quantity: number) {
    if (!isValidQuantity(quantity)) {
      throw new Error('Quantity must be a whole number between 1 and 99.');
    }

    const selectedModifiers = product.modifiers.filter(({ id }) => modifierIds.includes(id));
    const nextItem: CartItem = {
      productId: product.id,
      name: product.name,
      imageUrl: product.imageUrl,
      modifiers: selectedModifiers,
      modifierIds: selectedModifiers.map(({ id }) => id),
      quantity,
    };
    const key = getCartItemKey(nextItem.productId, nextItem.modifierIds);

    const existing = items.find((item) => getCartItemKey(item.productId, item.modifierIds) === key);

    if (existing) {
      const nextQuantity = existing.quantity + quantity;
      if (nextQuantity > MAX_QUANTITY) {
        throw new Error('A cart line cannot have more than 99 items.');
      }
      setItems(
        items.map((item) =>
          getCartItemKey(item.productId, item.modifierIds) === key
            ? { ...item, quantity: nextQuantity }
            : item,
        ),
      );
      return;
    }

    if (items.length >= MAX_LINES) {
      throw new Error('A cart cannot have more than 50 lines.');
    }

    setItems([...items, nextItem]);
  }

  function setItemQuantity(productId: number, modifierIds: number[], quantity: number) {
    if (!isValidQuantity(quantity)) return;
    const key = getCartItemKey(productId, modifierIds);
    setItems((currentItems) =>
      currentItems.map((item) =>
        getCartItemKey(item.productId, item.modifierIds) === key ? { ...item, quantity } : item,
      ),
    );
  }

  function removeItem(productId: number, modifierIds: number[]) {
    const key = getCartItemKey(productId, modifierIds);
    setItems((currentItems) =>
      currentItems.filter((item) => getCartItemKey(item.productId, item.modifierIds) !== key),
    );
  }

  function clearCart() {
    setItems([]);
  }

  return (
    <CartContext.Provider
      value={{
        items,
        itemCount: items.reduce((total, item) => total + item.quantity, 0),
        addItem,
        setItemQuantity,
        removeItem,
        clearCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used within a CartProvider');
  return context;
}
