import { writable, type Writable } from 'svelte/store';
import type { ShopItem } from '$lib/telegram/stores/types';

export const shopItems: Writable<ShopItem[]> = writable([]);