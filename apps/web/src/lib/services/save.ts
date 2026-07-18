/**
 * Save queue service — replaces legacy action-helpers.js save logic.
 * Debounces saves to /api/data to avoid flooding the backend.
 */
import { get } from 'svelte/store';
import { appStore } from '$lib/stores/app';
import type { AppState } from '$lib/stores/app';
import { saveDataToServer } from './api';
import { normalizeServerData } from './serverContract';

let pendingSavePayload: Record<string, unknown> | null = null;
let saveInFlight: Promise<boolean> = Promise.resolve(false);

function buildPayload(): Record<string, unknown> {
    const s = get(appStore);
    const selectedChild = s.children.find((child) => String(child.id) === String(s.currentChildId))
        ?? s.children[0]
        ?? null;
    return {
        childId: selectedChild?.id ?? s.currentChildId,
        balance: s.balance,
        rules: s.rules,
        tasks: s.tasks,
        shop: s.shopItems,
        history: s.history,
        requests: s.requests,
        children: s.children,
    };
}

/** Apply a POST /api/data server response back into the store (updates IDs etc.). */
function applyServerResponse(data: unknown): void {
    if (!data || typeof data !== 'object') return;
    const normalized = normalizeServerData(data as Record<string, unknown>);
    const partial: Partial<AppState> = {};
    if (Array.isArray(normalized.tasks)) partial.tasks = (normalized.tasks as unknown as AppState['tasks']);
    if (Array.isArray(normalized.shop)) partial.shopItems = (normalized.shop as unknown as AppState['shopItems']);
    if (Array.isArray(normalized.history)) partial.history = (normalized.history as unknown as AppState['history']);
    if (Array.isArray(normalized.requests)) partial.requests = (normalized.requests as unknown as AppState['requests']);
    if (Array.isArray((data as Record<string, unknown>).children)) {
        partial.children = (normalized.children as unknown as AppState['children']);
    }
    if ('rules' in normalized) partial.rules = (normalized.rules as string | null | undefined) ?? null;
    if (typeof (data as Record<string, unknown>).balance === 'number') {
        partial.balance = (data as Record<string, unknown>).balance as number;
    }
    if (Object.keys(partial).length > 0) appStore.setState(partial);
}

export function scheduleSave() {
    pendingSavePayload = buildPayload();
    void flushPendingSave();
}

export function flushPendingSave(options: { keepalive?: boolean } = {}): Promise<boolean> {
    if (!pendingSavePayload) return saveInFlight;

    const payload = pendingSavePayload;
    pendingSavePayload = null;

    saveInFlight = saveInFlight
        .catch(() => false)
        .then(async () => {
            const data = await saveDataToServer(payload, options);
            if (data) applyServerResponse(data);
            return data !== null;
        });

    return saveInFlight;
}
