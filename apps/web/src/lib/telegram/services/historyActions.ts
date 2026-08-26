import { getContext, setContext } from 'svelte';
import { loadTelegramHistory } from '$lib/services/telegramActivity';
import type { HistoryEntry } from '$lib/stores/app';
import type { TelegramPage } from '$lib/services/telegramActivity';
export type HistoryActions = { load: (childId: string | number, page?: number, limit?: number) => Promise<TelegramPage<HistoryEntry>> };
const KEY = Symbol('earnit-kids-history-actions');
export function createProductionHistoryActions(): HistoryActions { return { load: loadTelegramHistory }; }
export function provideHistoryActions(actions: HistoryActions): HistoryActions { setContext(KEY, actions); return actions; }
export function useHistoryActions(): HistoryActions { return getContext<HistoryActions | undefined>(KEY) ?? createProductionHistoryActions(); }
