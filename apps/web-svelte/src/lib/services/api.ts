/**
 * Typed API service — replaces legacy api.js + action-*.js
 * Preserves: CSRF cookie, credentials: same-origin, same endpoint paths.
 */

// ── CSRF ─────────────────────────────────────────────────────────────────────

function getCsrfToken(): string {
    if (typeof document === 'undefined') return '';
    const row = document.cookie
        .split(';')
        .map(r => r.trim())
        .find(r => r.startsWith('csrf_token='));
    return row ? decodeURIComponent(row.slice('csrf_token='.length)) : '';
}

type FetchOptions = RequestInit & { body?: BodyInit };

export async function fetchWithCsrf(url: string, options: FetchOptions = {}): Promise<Response> {
    const method = (options.method ?? 'GET').toUpperCase();
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
        const token = getCsrfToken();
        if (token) {
            options.headers = { ...(options.headers as Record<string, string>), 'X-CSRF-Token': token };
        }
    }
    return fetch(url, { credentials: 'same-origin', ...options });
}

async function parseJsonSafe<T = unknown>(res: Response): Promise<T | null> {
    const text = await res.text();
    return text ? (JSON.parse(text) as T) : null;
}

async function postJson<T = unknown>(url: string, body: unknown): Promise<T | null> {
    const res = await fetchWithCsrf(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    return parseJsonSafe<T>(res);
}

async function postBoolean(url: string, body: unknown, errorMsg?: string): Promise<boolean> {
    try {
        const res = await fetchWithCsrf(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        return res.ok;
    } catch (err) {
        if (errorMsg) console.error(errorMsg, err);
        return false;
    }
}

// ── Endpoints ─────────────────────────────────────────────────────────────────

export const API_URL = '/api/data';

export async function loadDataFromServer(childId?: string | number | null) {
    const q = childId != null ? `?childId=${encodeURIComponent(childId)}` : '';
    try {
        const res = await fetchWithCsrf(`/api/data${q}`);
        return res.ok ? await parseJsonSafe(res) : null;
    } catch (err) {
        console.error('Failed to load from server:', err);
        return null;
    }
}

export async function loadBaseData() {
    try {
        const res = await fetchWithCsrf('/api/base-data');
        return res.ok ? await parseJsonSafe(res) : { tasks: [], products: [] };
    } catch {
        return { tasks: [], products: [] };
    }
}

export async function saveDataToServer(data: unknown, options: { keepalive?: boolean } = {}): Promise<boolean> {
    try {
        const res = await fetchWithCsrf(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            ...(options.keepalive ? { keepalive: true } : {}),
            body: JSON.stringify(data),
        });
        return res.ok;
    } catch (err) {
        console.error('Failed to save to server:', err);
        return false;
    }
}

export const logout = () => postBoolean('/api/logout', {}, 'Logout failed');

// ── Task actions ──────────────────────────────────────────────────────────────

export const earnCoins = (taskId: unknown, childId?: unknown) =>
    postJson('/api/earn', { taskId, childId });

export const requestCoins = (taskId: unknown, childId?: unknown) =>
    postJson('/api/request', { taskId, childId });

// ── Shop actions ──────────────────────────────────────────────────────────────

export const buyItem = (itemId: unknown, childId?: unknown) =>
    postJson('/api/buy', { itemId, childId });

// ── Request actions ───────────────────────────────────────────────────────────

export const approveRequest = (requestId: unknown, childId?: unknown) =>
    postJson('/api/request/approve', { requestId, childId });

export const rejectRequest = (requestId: unknown, childId?: unknown) =>
    postJson('/api/request/reject', { requestId, childId });

export const deleteRequest = (requestId: unknown) =>
    postBoolean('/api/request/delete', { requestId }, 'Delete request failed');

// ── History actions ───────────────────────────────────────────────────────────

export const deleteHistoryItem = (historyId: unknown) =>
    postBoolean('/api/history/delete', { historyId }, 'Delete history failed');

// ── Admin actions ─────────────────────────────────────────────────────────────

export const adminAwardCoins = (childId: unknown, amount: number, reason?: string) =>
    postJson('/api/admin/award', { childId, amount, reason });

export const adminSaveTask = (task: unknown) =>
    postJson('/api/admin/task/save', task);

export const adminDeleteTask = (taskId: unknown) =>
    postBoolean('/api/admin/task/delete', { taskId }, 'Delete task failed');

export const adminSaveShopItem = (item: unknown) =>
    postJson('/api/admin/shop/save', item);

export const adminDeleteShopItem = (itemId: unknown) =>
    postBoolean('/api/admin/shop/delete', { itemId }, 'Delete shop item failed');

export const adminSaveChild = (childData: unknown) =>
    postJson('/api/admin/child/save', childData);

export const adminAddChild = (childData: unknown) =>
    postJson('/api/admin/child/add', childData);

export const adminChangePassword = (childId: unknown, password: string) =>
    postBoolean('/api/admin/child/password', { childId, password }, 'Change password failed');

export const adminGetChildLink = (childId: unknown) =>
    postJson<{ link: string }>('/api/admin/child/link', { childId });

export const adminRegenerateChildLink = (childId: unknown) =>
    postJson<{ link: string }>('/api/admin/child/regenerate-link', { childId });

export const adminSaveRules = (rules: unknown) =>
    postBoolean('/api/admin/rules/save', { rules }, 'Save rules failed');

export const adminSaveLimits = (childId: unknown, limits: unknown) =>
    postBoolean('/api/admin/limits/save', { childId, limits }, 'Save limits failed');

// ── Push registration ─────────────────────────────────────────────────────────

export const registerPushTokenOnServer = (payload: unknown) =>
    postJson('/api/push/register', payload);

export const unregisterPushTokenOnServer = (payload: unknown) =>
    postJson('/api/push/unregister', payload);

// ── Analytics ─────────────────────────────────────────────────────────────────

export async function loadAnalyticsData(childId?: unknown, timeframe = 'month') {
    const q = new URLSearchParams({ timeframe: String(timeframe) });
    if (childId != null) q.set('childId', String(childId));
    try {
        const res = await fetchWithCsrf(`/api/analytics?${q}`);
        return res.ok ? await parseJsonSafe(res) : null;
    } catch { return null; }
}

// ── Friends ───────────────────────────────────────────────────────────────────

export const searchFriend = (query: string) =>
    postJson('/api/friends/search', { query });

export const addFriend = (friendId: unknown) =>
    postBoolean('/api/friends/add', { friendId }, 'Add friend failed');
