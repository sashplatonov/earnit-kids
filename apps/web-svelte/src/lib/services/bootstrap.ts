/**
 * Bootstrap service — replaces legacy main-init.js
 * Loads server data into the app store after mount.
 */
import { get } from 'svelte/store';
import { appStore } from '$lib/stores/app';
import type { AppState } from '$lib/stores/app';
import { tabStore } from '$lib/stores/tabs';
import { showToast } from '$lib/stores/toasts';
import { loadDataFromServer, loadBaseData } from './api';
import { buildInitialState, normalizeServerData, normalizeShopItem, normalizeTask, normalizeHistoryEntry, normalizeRequest } from './serverContract';

const LAST_CHILD_KEY = 'earnit-last-child-id';

export async function initializeFromServer(): Promise<boolean> {
    appStore.setState({ isLoading: true });

    const data = await loadDataFromServer();
    if (!data || typeof data !== 'object') {
        showToast('Не удалось загрузить данные', 'error');
        appStore.setState({ isLoading: false });
        return false;
    }

    const record = data as Record<string, unknown>;
    const isAdmin = record.isAdmin === true || record.isAdmin === 'true' || record.isAdmin === 1;
    const baseData = isAdmin
        ? ((await loadBaseData()) as { tasks: unknown[]; products: unknown[] }) ?? { tasks: [], products: [] }
        : { tasks: [], products: [] };

    const state = buildInitialState(record, baseData as Record<string, unknown>);
    appStore.setState(state as Partial<AppState>);

    // Initialize tab to role-appropriate default
    tabStore.initForRole(!!(state as Record<string, unknown>).isAdmin);

    const stateRecord = state as Record<string, unknown>;
    // Auto-select child for admin
    if (stateRecord.isAdmin && Array.isArray(stateRecord.children) && stateRecord.children.length > 0) {
        const serverChildId = record.lastSelectedChildId;
        let localChildId: string | null = null;
        try { localChildId = typeof localStorage !== 'undefined' ? localStorage.getItem(LAST_CHILD_KEY) : null; } catch { /* */ }

        const preferred = serverChildId ?? localChildId;
        const match = preferred
            ? (stateRecord.children as Array<{ id: unknown }>).find(c => String(c.id) === String(preferred))
            : null;
        const childToSelect = (match ?? stateRecord.children[0]) as { id: unknown };

        if (childToSelect?.id != null) {
            try { localStorage.setItem(LAST_CHILD_KEY, String(childToSelect.id)); } catch { /* */ }
            appStore.setState({ currentChildId: childToSelect.id as string });
            // Load child-specific data
            const childData = await loadDataFromServer(childToSelect.id as string);
            if (childData && typeof childData === 'object') {
                const childRecord = childData as Record<string, unknown>;
                appStore.setState({
                    balance: (childRecord.balance as number) ?? 0,
                    tasks: Array.isArray(childRecord.tasks) ? childRecord.tasks.map(normalizeTask) as AppState['tasks'] : [],
                    shopItems: Array.isArray(childRecord.shop) ? childRecord.shop.map(normalizeShopItem) as AppState['shopItems'] : [],
                    history: Array.isArray(childRecord.history) ? childRecord.history.map(normalizeHistoryEntry) as AppState['history'] : [],
                    requests: Array.isArray(childRecord.requests) ? childRecord.requests.map(normalizeRequest) as AppState['requests'] : [],
                    childNickname: (childRecord.childNickname as string) ?? null,
                });
            }
        }
    }

    return true;
}

export async function refreshData(showSuccess = false): Promise<boolean> {
    const ok = await initializeFromServer();
    if (ok && showSuccess) showToast('Данные обновлены', 'success');
    return ok;
}

/** Persist the currently selected child id to localStorage */
export function persistLastChildId(childId: string | number) {
    try { localStorage.setItem(LAST_CHILD_KEY, String(childId)); } catch { /* */ }
}

/**
 * Apply a FamilyDataResponse snapshot (returned by action endpoints like
 * /api/tasks/{id}/complete, /api/shop/{id}/purchase, etc.) into the app store.
 * Only overwrites fields that are present in the response.
 */
export function applyDataSnapshot(data: Record<string, unknown>): void {
    if (!data || typeof data !== 'object') return;
    const normalized = normalizeServerData(data);
    const current = get(appStore);
    const partial: Partial<AppState> = {};
    if (typeof data.balance === 'number') partial.balance = data.balance;
    if (Array.isArray(normalized.tasks)) partial.tasks = normalized.tasks as AppState['tasks'];
    if (Array.isArray(normalized.shop)) partial.shopItems = normalized.shop as AppState['shopItems'];
    if (Array.isArray(normalized.history)) partial.history = normalized.history as AppState['history'];
    if (Array.isArray(normalized.requests)) partial.requests = normalized.requests as AppState['requests'];
    // Preserve current child if server doesn't override it
    if (!partial.tasks?.length) partial.tasks = current.tasks;
    if (Object.keys(partial).length > 0) appStore.setState(partial);
}
