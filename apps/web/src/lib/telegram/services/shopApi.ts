import { postJsonAfterPendingSave, postJsonResultAfterPendingSave, postJsonResultWithValidation, saveChildGroupOrder as _saveChildGroupOrder, earnCoins as _earnCoins } from '$lib/services/api';
import { flushPendingSave } from '$lib/services/save';

function buildChildQuery(childId: unknown): string {
    return childId != null ? `?childId=${encodeURIComponent(String(childId))}` : '';
}

/** Admin: immediately purchase an item for a child. */
export const buyItem = (itemId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/purchase${buildChildQuery(childId)}`, {});

/** Child: create a purchase request that requires parent approval. */
export const requestItem = (itemId: unknown, childId?: unknown) =>
    postJsonResultAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/request${buildChildQuery(childId)}`, {});

/** Child: create a purchase request with optional note. */
export const requestItemWithNote = (itemId: unknown, note?: string | null, childId?: unknown) =>
    postJsonResultAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/request${buildChildQuery(childId)}`, { note: note ?? null });

export const saveChildGroupOrder = _saveChildGroupOrder;
export const earnCoins = _earnCoins;

export const importShopItems = (body: {
    childId: unknown;
    rows: Array<Record<string, unknown>>;
}) => {
    return flushPendingSave().then(() => postJsonResultWithValidation('/api/shop/import', body));
};
