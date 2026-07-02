/**
 * Typed API service — replaces legacy api.js + action-*.js
 * Preserves: CSRF cookie, credentials: same-origin, same endpoint paths.
 */

import { normalizeAuthResponse } from './serverContract';
import type { AuthResponseSnapshot, MembershipPermission, ParentMembership } from '$lib/types/auth';
import { logClientError } from '$lib/logging/clientLogger';

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
    errors?: Array<{ row?: unknown; field?: unknown; message?: unknown }>;
};

export type ApiActionResult<T = unknown> =
    | { ok: true; data: T | null }
    | { ok: false; error: string; errorCode: string | null; status: number };

export type ImportValidationError = {
    row: number;
    field: string;
    message: string;
};

export type ImportActionResult<T = unknown> =
    | { ok: true; data: T | null }
    | { ok: false; error: string; errorCode: string | null; status: number; validationErrors?: ImportValidationError[] };

export type AuthActionResult = ApiActionResult<AuthResponseSnapshot>;

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

function extractValidationErrors(payload: unknown): ImportValidationError[] | undefined {
    if (!payload || typeof payload !== 'object') {
        return undefined;
    }

    const errors = (payload as ProblemDetails).errors;
    if (!Array.isArray(errors) || errors.length === 0) {
        return undefined;
    }

    return errors
        .map((error) => {
            const row = typeof error.row === 'number' && Number.isFinite(error.row) ? error.row : Number(error.row ?? 0);
            const field = typeof error.field === 'string' ? error.field : '';
            const message = typeof error.message === 'string' ? error.message : '';
            return {
                row: Number.isFinite(row) ? row : 0,
                field,
                message,
            };
        })
        .filter((error) => error.field || error.message);
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
        logClientError('api.post_failed', 'POST request failed', { url, error: err });
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
    }
}

async function postJsonResultWithValidation<T = unknown>(url: string, body: unknown): Promise<ImportActionResult<T>> {
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
            validationErrors: extractValidationErrors(data),
        };
    } catch (err) {
        logClientError('api.post_failed', 'POST request failed', { url, error: err });
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
    }
}

async function postAuthJson(url: string, body: unknown): Promise<AuthActionResult> {
    const result = await postJsonResult<Record<string, unknown>>(url, body);

    if (!result.ok) {
        return result;
    }

    return {
        ok: true,
        data: result.data ? normalizeAuthResponse(result.data) : null,
    };
}

async function flushPendingCrudSave(): Promise<void> {
    const { flushPendingSave } = await import('$lib/services/save');
    await flushPendingSave();
}

async function postJsonAfterPendingSave<T = unknown>(url: string, body: unknown): Promise<T | null> {
    await flushPendingCrudSave();
    return postJson<T>(url, body);
}

async function postJsonResultAfterPendingSave<T = unknown>(url: string, body: unknown): Promise<ApiActionResult<T>> {
    await flushPendingCrudSave();
    return postJsonResult<T>(url, body);
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
        if (errorMsg) logClientError('api.post_boolean_failed', errorMsg, { url, error: err });
        return false;
    }
}

async function deleteJsonResult<T = unknown>(url: string): Promise<ApiActionResult<T>> {
    try {
        const res = await fetchWithCsrf(url, { method: 'DELETE' });
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
        logClientError('api.delete_failed', 'DELETE request failed', { url, error: err });
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
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
        logClientError('api.get_failed', 'GET request failed', { url, error: err });
        return null;
    }
}

async function deleteResource(url: string, errorMsg?: string): Promise<boolean> {
    try {
        const res = await fetchWithCsrf(url, { method: 'DELETE' });
        return res.ok;
    } catch (err) {
        if (errorMsg) logClientError('api.delete_resource_failed', errorMsg, { url, error: err });
        return false;
    }
}

async function deleteResourceAfterPendingSave(url: string, errorMsg?: string): Promise<boolean> {
    await flushPendingCrudSave();
    return deleteResource(url, errorMsg);
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
        logClientError('api.load_data_failed', 'Failed to load from server', { error: err, childId });
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
        logClientError('api.save_data_failed', 'Failed to save to server', { error: err });
        return null;
    }
}

export const logout = () => postBoolean('/api/logout', {}, 'Logout failed');

export const loginWithEmail = (email: string, password: string) =>
    postAuthJson('/api/login', { email, password });

export const selectFamily = (email: string, familyId: string) =>
    postAuthJson('/api/select-family', { email, familyId });

export async function loadParentMemberships(): Promise<ApiActionResult<ParentMembership[]>> {
    try {
        const res = await fetchWithCsrf('/api/parents');
        const data = await parseJsonSafe<ParentMembership[] | ProblemDetails>(res);
        if (res.ok) {
            return { ok: true, data: Array.isArray(data) ? data : [] };
        }

        return {
            ok: false,
            error: extractProblemMessage(data),
            errorCode: extractProblemCode(data),
            status: res.status,
        };
    } catch (err) {
        logClientError('api.parent_memberships_failed', 'GET request failed', { url: '/api/parents', error: err });
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
    }
}

export const addParentMembership = (body: { email: string; permission: MembershipPermission }) =>
    postJsonResult<ParentMembership>('/api/parents', body);

export const updateParentMembership = (membershipId: number, body: { permission: MembershipPermission }) =>
    postJsonResult<ParentMembership>(`/api/parents/${encodeURIComponent(String(membershipId))}`, body);

export async function removeParentMembership(membershipId: number): Promise<ApiActionResult<void>> {
    return deleteJsonResult<void>(`/api/parents/${encodeURIComponent(String(membershipId))}`);
}

// ── Task actions ──────────────────────────────────────────────────────────────

export const earnCoins = (taskId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/complete${buildChildQuery(childId)}`, {});

export const requestCoins = (taskId: unknown) =>
    postJsonResultAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/request`, {});

export const requestCoinsWithNote = (taskId: unknown, note?: string | null) =>
    postJsonResultAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/request`, { note: note ?? null });

// ── Shop actions ──────────────────────────────────────────────────────────────

/** Admin: immediately purchase an item for a child. */
export const buyItem = (itemId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/purchase${buildChildQuery(childId)}`, {});

/** Child: create a purchase request that requires parent approval. */
export const requestItem = (itemId: unknown) =>
    postJsonResultAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/request`, {});

/** Child: create a purchase request with optional note. */
export const requestItemWithNote = (itemId: unknown, note?: string | null) =>
    postJsonResultAfterPendingSave(`/api/shop/${encodeURIComponent(String(itemId))}/request`, { note: note ?? null });

export type BulkAction = 'delete' | 'block' | 'unblock' | 'change_group';

export type BulkTaskActionPayload = {
    childId: unknown;
    action: BulkAction;
    taskIds: Array<number | string>;
    groupName?: string | null;
};

export type BulkShopActionPayload = {
    childId: unknown;
    action: BulkAction;
    itemIds: Array<number | string>;
    groupName?: string | null;
};

export const bulkTaskAction = (body: BulkTaskActionPayload) =>
    postJsonResultAfterPendingSave('/api/tasks/bulk', body);

export const bulkShopAction = (body: BulkShopActionPayload) =>
    postJsonResultAfterPendingSave('/api/shop/bulk', body);

export const importTasks = (body: {
    childId: unknown;
    rows: Array<Record<string, unknown>>;
}) => flushPendingCrudSave().then(() => postJsonResultWithValidation('/api/tasks/import', body));

export const importShopItems = (body: {
    childId: unknown;
    rows: Array<Record<string, unknown>>;
}) => flushPendingCrudSave().then(() => postJsonResultWithValidation('/api/shop/import', body));

// ── Lightweight polling ───────────────────────────────────────────────────────

/**
 * Fetch only the requests page (lightweight) instead of the full `/api/data`
 * snapshot. Used by RequestsSection polling to avoid full reloads.
 */
export async function fetchRequestsFromServer(page = 1, limit = 50): Promise<Record<string, unknown> | null> {
    try {
        const res = await fetchWithCsrf(`/api/requests?page=${page}&limit=${limit}`);
        return res.ok ? await parseJsonSafe<Record<string, unknown>>(res) : null;
    } catch (err) {
        logClientError('api.fetch_requests_failed', 'Failed to fetch requests', { error: err });
        return null;
    }
}

// ── Request actions ───────────────────────────────────────────────────────────

export const approveRequest = (requestId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}/approve${buildChildQuery(childId)}`, {});

export const rejectRequest = (requestId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}/reject${buildChildQuery(childId)}`, {});

export const deleteRequest = (requestId: unknown, childId?: unknown) =>
    deleteResourceAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}${buildChildQuery(childId)}`, 'Delete request failed');

// ── History actions ───────────────────────────────────────────────────────────

export const deleteHistoryItem = (historyId: unknown, childId?: unknown) =>
    deleteResourceAfterPendingSave(`/api/history/${encodeURIComponent(String(historyId))}${buildChildQuery(childId)}`, 'Delete history failed');

// ── Admin actions ─────────────────────────────────────────────────────────────

/** Award or deduct coins for a child. Maps to POST /api/balance/adjust. */
export const adminAwardCoins = (childId: unknown, amount: number, description?: string) =>
    postJsonAfterPendingSave('/api/balance/adjust', { childId, amount, description });

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
