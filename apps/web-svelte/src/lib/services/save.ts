/**
 * Save queue service — replaces legacy action-helpers.js save logic.
 * Debounces saves to /api/data to avoid flooding the backend.
 */
import { get } from 'svelte/store';
import { appStore } from '$lib/stores/app';
import { saveDataToServer } from './api';

let pendingSavePayload: Record<string, unknown> | null = null;
let saveInFlight: Promise<boolean> = Promise.resolve(false);

function buildPayload(): Record<string, unknown> {
    const s = get(appStore);
    return {
        childId: s.currentChildId,
        balance: s.balance,
        tasks: s.tasks,
        shop: s.shopItems,
        history: s.history,
        requests: s.requests,
        children: s.children,
    };
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
        .then(() => saveDataToServer(payload, options));

    return saveInFlight;
}
