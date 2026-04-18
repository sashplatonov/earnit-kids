/**
 * Bootstrap service — replaces legacy main-init.js
 * Loads server data into the app store after mount.
 */
import { appStore } from '$lib/stores/app';
import type { AppState } from '$lib/stores/app';
import { tabStore } from '$lib/stores/tabs';
import { showToast } from '$lib/stores/toasts';
import { loadDataFromServer, loadBaseData } from './api';
import { buildInitialState } from './serverContract';

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
                    tasks: (childRecord.tasks as AppState['tasks']) ?? [],
                    shopItems: (childRecord.shop as AppState['shopItems']) ?? [],
                    history: (childRecord.history as AppState['history']) ?? [],
                    requests: (childRecord.requests as AppState['requests']) ?? [],
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
