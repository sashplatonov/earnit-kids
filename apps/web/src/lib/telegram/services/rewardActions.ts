import { getContext, setContext } from 'svelte';
import { buyItem, saveChildGroupOrder } from '$lib/telegram/services/shopApi';
import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
import { scheduleSave } from '$lib/services/save';
import type { ApiActionResult } from '$lib/services/api';
import { shopItems } from '$lib/telegram/stores/shopItems';
import type { ShopItem } from '$lib/telegram/stores/types';
export type RewardActionInput = { itemId?: string | number; id?: string | number; childId?: string | number; [key: string]: unknown };
export type RewardActions = { buy: (input: RewardActionInput) => Promise<unknown | null>; saveGroups: (childId: unknown, groups: string[], hiddenGroups: string[]) => Promise<ApiActionResult>; persist: () => Promise<boolean>; applySnapshot: (snapshot: Record<string, unknown>) => void; refresh: (showSuccess?: boolean) => Promise<boolean>; saveReward: (item: ShopItem | null, payload: Partial<ShopItem>) => Promise<ApiActionResult> };
const KEY = Symbol('earnit-kids-reward-actions');
export function createProductionRewardActions(): RewardActions { return { buy: ({ itemId, id, childId }) => buyItem(itemId ?? id, childId), saveGroups: (childId, groups, hiddenGroups) => saveChildGroupOrder(childId, 'shop', groups, hiddenGroups), persist: async () => { await scheduleSave(); return true; }, applySnapshot: applyDataSnapshot, refresh: refreshData, saveReward: async (item, payload) => { const next = item ? { ...item, ...payload } : { ...payload, id: `reward-${Date.now()}` } as ShopItem; shopItems.update((items) => item ? items.map((entry) => entry.id === item.id ? next : entry) : [...items, next]); await scheduleSave(); return { ok: true, data: null }; } }; }
export function provideRewardActions(actions: RewardActions): RewardActions { setContext(KEY, actions); return actions; }
export function useRewardActions(): RewardActions { return getContext<RewardActions | undefined>(KEY) ?? createProductionRewardActions(); }
