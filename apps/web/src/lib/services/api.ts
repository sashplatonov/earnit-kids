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

type ProblemDetails = {
    detail?: unknown;
    title?: unknown;
    errorCode?: unknown;
};

export type ApiActionResult<T = unknown> =
    | { ok: true; data: T | null }
    | { ok: false; error: string; errorCode: string | null; status: number };

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

function extractProblemMessage(payload: unknown): string {
    if (!payload || typeof payload !== 'object') {
        return 'Не удалось выполнить запрос';
    }

    const problem = payload as ProblemDetails;
    if (typeof problem.detail === 'string' && problem.detail.trim()) {
        return problem.detail;
    }
    if (typeof problem.title === 'string' && problem.title.trim()) {
        return problem.title;
    }
    return 'Не удалось выполнить запрос';
}

function extractProblemCode(payload: unknown): string | null {
    if (!payload || typeof payload !== 'object') {
        return null;
    }

    const code = (payload as ProblemDetails).errorCode;
    return typeof code === 'string' && code.trim() ? code : null;
}

async function postJsonResult<T = unknown>(url: string, body: unknown): Promise<ApiActionResult<T>> {
    try {
        const res = await fetchWithCsrf(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await parseJsonSafe<T | ProblemDetails>(res);

        if (res.ok) {
            return { ok: true, data: data as T | null };
        }

        return {
            ok: false,
            error: extractProblemMessage(data),
            errorCode: extractProblemCode(data),
            status: res.status,
        };
    } catch (err) {
        console.error('POST request failed:', url, err);
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
    }
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

function buildChildQuery(childId: unknown): string {
    return childId != null ? `?childId=${encodeURIComponent(String(childId))}` : '';
}

async function fetchGet<T = unknown>(url: string): Promise<T | null> {
    try {
        const res = await fetchWithCsrf(url);
        return res.ok ? await parseJsonSafe<T>(res) : null;
    } catch (err) {
        console.error('GET request failed:', url, err);
        return null;
    }
}

async function deleteResource(url: string, errorMsg?: string): Promise<boolean> {
    try {
        const res = await fetchWithCsrf(url, { method: 'DELETE' });
        return res.ok;
    } catch (err) {
        if (errorMsg) console.error(errorMsg, err);
        return false;
    }
}

type ChildLinkPayload = {
    link?: string;
    token?: string;
};

function normalizeChildLink(payload: ChildLinkPayload | null): { link: string } | null {
    if (!payload) return null;
    if (payload.link) return { link: payload.link };
    if (!payload.token) return null;

    const origin = typeof location !== 'undefined' && location.origin
        ? location.origin.replace(/\/+$/, '')
        : '';

    return {
        link: `${origin}/login-child/${payload.token}`,
    };
}

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

export async function saveDataToServer(data: unknown, options: { keepalive?: boolean } = {}): Promise<unknown> {
    try {
        const res = await fetchWithCsrf(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            ...(options.keepalive ? { keepalive: true } : {}),
            body: JSON.stringify(data),
        });
        if (!res.ok) return null;
        return await parseJsonSafe(res);
    } catch (err) {
        console.error('Failed to save to server:', err);
        return null;
    }
}

export const logout = () => postBoolean('/api/logout', {}, 'Logout failed');

// ── Task actions ──────────────────────────────────────────────────────────────

export const earnCoins = (taskId: unknown, childId?: unknown) =>
    postJson(`/api/tasks/${encodeURIComponent(String(taskId))}/complete${buildChildQuery(childId)}`, {});

export const requestCoins = (taskId: unknown) =>
    postJsonResult(`/api/tasks/${encodeURIComponent(String(taskId))}/request`, {});

// ── Shop actions ──────────────────────────────────────────────────────────────

/** Admin: immediately purchase an item for a child. */
export const buyItem = (itemId: unknown, childId?: unknown) =>
    postJson(`/api/shop/${encodeURIComponent(String(itemId))}/purchase${buildChildQuery(childId)}`, {});

/** Child: create a purchase request that requires parent approval. */
export const requestItem = (itemId: unknown) =>
    postJsonResult(`/api/shop/${encodeURIComponent(String(itemId))}/request`, {});

// ── Request actions ───────────────────────────────────────────────────────────

export const approveRequest = (requestId: unknown, childId?: unknown) =>
    postJson(`/api/requests/${encodeURIComponent(String(requestId))}/approve${buildChildQuery(childId)}`, {});

export const rejectRequest = (requestId: unknown, childId?: unknown) =>
    postJson(`/api/requests/${encodeURIComponent(String(requestId))}/reject${buildChildQuery(childId)}`, {});

export const deleteRequest = (requestId: unknown, childId?: unknown) =>
    deleteResource(`/api/requests/${encodeURIComponent(String(requestId))}${buildChildQuery(childId)}`, 'Delete request failed');

// ── History actions ───────────────────────────────────────────────────────────

export const deleteHistoryItem = (historyId: unknown, childId?: unknown) =>
    deleteResource(`/api/history/${encodeURIComponent(String(historyId))}${buildChildQuery(childId)}`, 'Delete history failed');

// ── Admin actions ─────────────────────────────────────────────────────────────

/** Award or deduct coins for a child. Maps to POST /api/balance/adjust. */
export const adminAwardCoins = (childId: unknown, amount: number, description?: string) =>
    postJson('/api/balance/adjust', { childId, amount, description });

/** Update child settings (name + limits). Admin only. */
export const adminSaveChildSettings = (childId: unknown, settings: { name?: string; dailyCoinLimit?: number; monthlyLimit?: number }) =>
    postJson(`/api/children/${encodeURIComponent(String(childId))}/settings`, settings);

/** Rename the currently-authenticated child (child session). */
export const updateOwnNickname = (nickname: string) =>
    postJson('/api/update-nickname', { nickname });

/** Create a new child profile. */
export const adminAddChild = (name: string) =>
    postJson('/api/children', { name });

/** Delete a child profile. */
export const adminDeleteChild = (childId: unknown) =>
    deleteResource(`/api/children/${encodeURIComponent(String(childId))}`, 'Delete child failed');

/** Get the current login link/token for a child. */
export async function adminGetChildLink(childId: unknown) {
    const payload = await fetchGet<ChildLinkPayload>(`/api/children/${encodeURIComponent(String(childId))}/link`);
    return normalizeChildLink(payload);
}

/** Regenerate the login token for a child. */
export async function adminRegenerateChildLink(childId: unknown) {
    const payload = await postJson<ChildLinkPayload>(`/api/children/${encodeURIComponent(String(childId))}/regenerate-token`, {});
    return normalizeChildLink(payload);
}

/** Save child spending/coin limits. Maps to POST /api/children/{id}/settings. */
export const adminSaveLimits = (childId: unknown, limits: { dailyCoinLimit?: number; monthlyLimit?: number }) =>
    postJson(`/api/children/${encodeURIComponent(String(childId))}/settings`, limits);

export const saveChildGroupOrder = (childId: unknown, section: 'tasks' | 'shop', groups: string[]) =>
    postJsonResult(`/api/children/${encodeURIComponent(String(childId))}/group-order`, { section, groups });

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

export const searchFriend = async (query: string) => {
    try {
        const res = await fetchWithCsrf(`/api/search-user?nickname=${encodeURIComponent(query)}`);
        return res.ok ? await parseJsonSafe(res) : [];
    } catch { return []; }
};

export const addFriend = (friendId: unknown) =>
    postBoolean('/api/add-friend', { friendId }, 'Add friend failed');
