/**
 * Bootstrap service — replaces legacy main-init.js
 * Loads server data into the app store after mount.
 */
import { get } from 'svelte/store';
import { appStore } from '$lib/stores/app';
import { shopItems } from '$lib/telegram/stores/shopItems';
import { catalogRewards } from '$lib/telegram/stores/rewards';
import type { AppState } from '$lib/stores/app';
import { showToast } from '$lib/stores/toasts';
import { loadDataDetailsFromServer, loadDataFromServer, loadBaseData } from './api';
import { buildInitialState, normalizeServerData, normalizeTask, normalizeHistoryEntry, normalizeRequest } from './serverContract';
import { logClientInfo } from '$lib/logging/clientLogger';

const LAST_CHILD_KEY = 'earnit-last-child-id';

function mark(label: string): number {
    if (typeof performance === 'undefined') return 0;
    performance.mark(`bootstrap:${label}`);
    return performance.now();
}

function measure(label: string, startMs: number) {
    if (typeof performance === 'undefined' || startMs === 0) return;
    const elapsed = Math.round(performance.now() - startMs);
    logClientInfo('perf.bootstrap', `bootstrap:${label}`, { durationMs: elapsed });
}

export async function initializeFromServer(): Promise<boolean> {
    const t0 = mark('initializeFromServer');
    appStore.setState({ isLoading: true });

    const data = await loadDataFromServer();
    if (!data || typeof data !== 'object') {
        showToast('Не удалось загрузить данные', 'error');
        appStore.setState({ isLoading: false });
        return false;
    }
    measure('loadFamilyData', t0);

    const t1 = mark('loadBaseData');
    const record = data as Record<string, unknown>;
    const isAdmin = record.isAdmin === true || record.isAdmin === 'true' || record.isAdmin === 1;
    console.info('[bootstrap] /api/data isAdmin field:', record.isAdmin, 'derived isAdmin:', isAdmin, 'role:', record.role);
    const baseData = isAdmin
        ? ((await loadBaseData()) as { tasks: unknown[]; products: unknown[] }) ?? { tasks: [], products: [] }
        : { tasks: [], products: [] };
    measure('loadBaseData', t1);

    const t2 = mark('buildInitialState');
    const currentPermission = get(appStore).permission;
    const currentFamilyId = get(appStore).familyId;

    const state = buildInitialState(record, baseData as Record<string, unknown>);
    if (state.permission == null && currentPermission != null) {
        state.permission = currentPermission;
    }
    if (state.familyId == null && currentFamilyId != null) {
        state.familyId = currentFamilyId;
    }

    // Sync server shop items to the dedicated shopItems store
    if (state.shopItems && Array.isArray(state.shopItems)) {
        shopItems.set(state.shopItems);
    }

    // Sync server reward catalog to the dedicated catalogRewards store
    if (state.catalog?.rewards && Array.isArray(state.catalog.rewards)) {
        catalogRewards.set(state.catalog.rewards);
    }

    // Keep isLoading true — will be cleared after all data (including child data) is loaded
    appStore.setState({ ...(state as Partial<AppState>), isLoading: true });
    measure('buildInitialState', t2);

    const t3 = mark('loadChildData');
    const stateRecord = state as Record<string, unknown>;
    const selectedChildId = stateRecord.currentChildId ?? null;
    if (selectedChildId != null) {
        try { localStorage.setItem(LAST_CHILD_KEY, String(selectedChildId)); } catch { /* */ }
        const detailData = await loadDataDetailsFromServer(selectedChildId as string | number);
        if (detailData && typeof detailData === 'object') {
            const detailRecord = detailData as Record<string, unknown>;
            appStore.setState({
                history: Array.isArray(detailRecord.history) ? (detailRecord.history.map(normalizeHistoryEntry) as unknown as AppState['history']) : [],
                requests: Array.isArray(detailRecord.requests) ? (detailRecord.requests.map(normalizeRequest) as unknown as AppState['requests']) : [],
                friends: Array.isArray(detailRecord.friends) ? detailRecord.friends as unknown as AppState['friends'] : [],
            });
        }
    }
    measure('loadChildData', t3);

    // All data is now loaded — clear loading flag
    appStore.setState({ isLoading: false });
    measure('total', t0);
    return true;
}

export async function refreshData(showSuccess = false): Promise<boolean> {
    const t0 = typeof performance !== 'undefined' ? performance.now() : 0;
    const ok = await initializeFromServer();
    if (ok && showSuccess) showToast('Данные обновлены', 'success');
    if (t0 > 0) {
        logClientInfo('perf.refresh', 'refreshData', { durationMs: Math.round(performance.now() - t0) });
    }
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
    if ('rules' in data) partial.rules = (data.rules as string | null | undefined) ?? null;
    if (Array.isArray(normalized.tasks)) partial.tasks = (normalized.tasks as unknown as AppState['tasks']);
    if (Array.isArray(normalized.history)) partial.history = (normalized.history as unknown as AppState['history']);
    if (Array.isArray(normalized.requests)) partial.requests = (normalized.requests as unknown as AppState['requests']);
    if (Array.isArray(data.children)) partial.children = (normalized.children as unknown as AppState['children']);
    // Preserve current child if server doesn't override it
    if (!partial.tasks?.length) partial.tasks = current.tasks;
    if (Object.keys(partial).length > 0) appStore.setState(partial);
}

let latestChildSwitchRequest = 0;

/**
 * Switch the active child (admin only) — loads child-specific data and
 * persists the selection to localStorage so it survives page reloads.
 */
export async function switchChild(childId: string | number): Promise<void> {
    const requestId = ++latestChildSwitchRequest;
    persistLastChildId(childId);
    appStore.setState({ currentChildId: childId, isLoading: true });

    const t0 = typeof performance !== 'undefined' ? performance.now() : 0;
    const childShell = await loadDataFromServer(childId);
    if (requestId !== latestChildSwitchRequest) return;
    if (childShell && typeof childShell === 'object') {
        const rec = childShell as Record<string, unknown>;
        appStore.setState({
            balance: (rec.balance as number) ?? 0,
            rules: (rec.rules as string | null | undefined) ?? null,
            tasks: Array.isArray(rec.tasks) ? (rec.tasks.map(normalizeTask) as unknown as AppState['tasks']) : [],
            childNickname: (rec.childNickname as string) ?? null,
        });
    }

    const childDetails = await loadDataDetailsFromServer(childId);
    if (requestId !== latestChildSwitchRequest) return;
    if (childDetails && typeof childDetails === 'object') {
        const rec = childDetails as Record<string, unknown>;
        appStore.setState({
            history: Array.isArray(rec.history) ? (rec.history.map(normalizeHistoryEntry) as unknown as AppState['history']) : [],
            requests: Array.isArray(rec.requests) ? (rec.requests.map(normalizeRequest) as unknown as AppState['requests']) : [],
            friends: Array.isArray(rec.friends) ? rec.friends as unknown as AppState['friends'] : [],
            isLoading: false,
        });
    } else {
        appStore.setState({ isLoading: false });
    }
    if (t0 > 0) {
        logClientInfo('perf.switchChild', 'switchChild', { childId: String(childId), durationMs: Math.round(performance.now() - t0) });
    }
}
