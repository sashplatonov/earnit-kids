/**
 * Typed API service — replaces legacy api.js + action-*.js
 * Preserves: CSRF cookie, credentials: same-origin, same endpoint paths.
 */

import { normalizeAuthResponse, normalizeChild } from './serverContract';
import type { AuthResponseSnapshot, MembershipPermission, ParentMembership } from '$lib/types/auth';
import type { Child } from '$lib/stores/app';
import { logClientError } from '$lib/logging/clientLogger';


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
    try {
        const res = await fetchWithCsrf(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        return res.ok ? parseJsonSafe<T>(res) : null;
    } catch (err) {
        logClientError('api.post_failed', 'POST request failed', { url, error: err });
        return null;
    }
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

async function putJsonResult<T = unknown>(url: string, body: unknown): Promise<ApiActionResult<T>> {
    try {
        const res = await fetchWithCsrf(url, {
            method: 'PUT',
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
        logClientError('api.put_failed', 'PUT request failed', { url, error: err });
        return {
            ok: false,
            error: 'Сеть недоступна. Попробуйте еще раз.',
            errorCode: null,
            status: 0,
        };
    }
}

export async function postJsonResultWithValidation<T = unknown>(url: string, body: unknown): Promise<ImportActionResult<T>> {
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

export async function postJsonAfterPendingSave<T = unknown>(url: string, body: unknown): Promise<T | null> {
    try {
        await flushPendingCrudSave();
        return postJson<T>(url, body);
    } catch (err) {
        logClientError('api.pending_save_failed', 'Pending save failed before POST request', { url, error: err });
        return null;
    }
}

export async function postJsonResultAfterPendingSave<T = unknown>(url: string, body: unknown): Promise<ApiActionResult<T>> {
    try {
        await flushPendingCrudSave();
        return postJsonResult<T>(url, body);
    } catch (err) {
        logClientError('api.pending_save_failed', 'Pending save failed before POST request', { url, error: err });
        return {
            ok: false,
            error: 'Не удалось сохранить последние изменения. Попробуйте еще раз.',
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

export async function loadDataDetailsFromServer(childId?: string | number | null) {
    const q = childId != null ? `?childId=${encodeURIComponent(childId)}` : '';
    try {
        const res = await fetchWithCsrf(`/api/data/details${q}`);
        return res.ok ? await parseJsonSafe(res) : null;
    } catch (err) {
        logClientError('api.load_data_details_failed', 'Failed to load detail data from server', { error: err, childId });
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

export type TelegramAccountConnection = {
    email: string;
    emailConnected: boolean;
    telegramConnected: boolean;
    miniAppUrl: string | null;
};

type TelegramLinkLaunch = { launchUrl: string };

export const getTelegramAccountConnection = () =>
    fetchGet<TelegramAccountConnection>('/api/telegram/account-connection');

export const startTelegramAccountLink = () =>
    postJsonResult<TelegramLinkLaunch>('/api/telegram/account-connection/start', {});

export const unlinkTelegramAccount = () =>
    deleteJsonResult<void>('/api/telegram/account-connection');

/** Create a single-use Telegram invite that binds a second parent to the family. */
export async function createParentTelegramInvite(): Promise<{ launchUrl: string } | null> {
    const result = await postJsonResult<{ launchUrl: string }>('/api/telegram/parents/invite', {});
    return result.ok ? result.data : null;
}

/** Accept a parent Telegram invite: binds the Telegram user and joins the family. */
export async function acceptParentTelegramInvite(token: string, email: string, initData: string): Promise<ApiActionResult<void>> {
    return postJsonResult<void>('/api/telegram/parents/invite/accept', { token, email, initData });
}

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
    putJsonResult<ParentMembership>(`/api/parents/${encodeURIComponent(String(membershipId))}`, body);

export const deactivateParentMembership = (membershipId: number) =>
    postJsonResult<ParentMembership>(`/api/parents/${encodeURIComponent(String(membershipId))}/deactivate`, {});

export const reactivateParentMembership = (membershipId: number) =>
    postJsonResult<ParentMembership>(`/api/parents/${encodeURIComponent(String(membershipId))}/reactivate`, {});

export const transferParentAdmin = (membershipId: number) =>
    postJsonResult<ParentMembership>(`/api/parents/${encodeURIComponent(String(membershipId))}/transfer-admin`, {});

export async function removeParentMembership(membershipId: number): Promise<ApiActionResult<void>> {
    return deleteJsonResult<void>(`/api/parents/${encodeURIComponent(String(membershipId))}`);
}


export type NotificationPreference = { key: string; enabled: boolean };
export type ChildNotificationSettings = { childId: number; childName: string; preferences: NotificationPreference[] };
export type FamilyNotificationSettings = { parent: NotificationPreference[]; children: ChildNotificationSettings[] };

/** Load role-aware notification settings for the current family. */
export async function getFamilyNotificationSettings(): Promise<FamilyNotificationSettings | null> {
    return fetchGet<FamilyNotificationSettings>('/api/family/notifications');
}

/** Update a single role-aware notification preference. */
export async function setFamilyNotificationPreference(
    scope: string,
    childId: number | null,
    key: string,
    enabled: boolean,
): Promise<boolean> {
    const result = await putJsonResult<void>('/api/family/notifications', { scope, childId, key, enabled });
    return result.ok;
}


export type AccountConnection = {
    email: string;
    emailLinked: boolean;
    telegramLinked: boolean;
};

/** Load the parent account connection overview (email + telegram). */
export async function getAccountConnection(): Promise<AccountConnection | null> {
    return fetchGet<AccountConnection>('/api/account');
}

/** Change the parent email address. */
export async function changeAccountEmail(newEmail: string): Promise<ApiActionResult<void>> {
    return postJsonResult<void>('/api/account/email', { newEmail });
}

/** Unlink email login (requires a linked Telegram account). */
export async function unlinkAccountEmail(): Promise<ApiActionResult<void>> {
    return postJsonResult<void>('/api/account/email/unlink', {});
}

/** Change the parent password using the current password. */
export async function changePassword(oldPassword: string, newPassword: string): Promise<ApiActionResult<void>> {
    return postJsonResult<void>('/api/change-password', { oldPassword, newPassword });
}


export const earnCoins = (taskId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/complete${buildChildQuery(childId)}`, {});

export const requestCoins = (taskId: unknown, childId?: unknown) =>
    postJsonResultAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/request${buildChildQuery(childId)}`, {});

export const requestCoinsWithNote = (taskId: unknown, note?: string | null, childId?: unknown) =>
    postJsonResultAfterPendingSave(`/api/tasks/${encodeURIComponent(String(taskId))}/request${buildChildQuery(childId)}`, { note: note ?? null });


/** Admin: immediately purchase an item for a child. */
// Moved to $lib/telegram/services/shopApi.ts

/** Child: create a purchase request that requires parent approval. */
// Moved to $lib/telegram/services/shopApi.ts

/** Child: create a purchase request with optional note. */
// Moved to $lib/telegram/services/shopApi.ts

export type BulkAction = 'delete' | 'block' | 'unblock' | 'change_group';

export type BulkTaskActionPayload = {
    childId: unknown;
    action: BulkAction;
    taskIds: Array<number | string>;
    groupName?: string | null;
};

export const bulkTaskAction = (body: BulkTaskActionPayload) =>
    postJsonResultAfterPendingSave('/api/tasks/bulk', body);

export const importTasks = (body: {
    childId: unknown;
    rows: Array<Record<string, unknown>>;
}) => flushPendingCrudSave().then(() => postJsonResultWithValidation('/api/tasks/import', body));

// Moved to $lib/telegram/services/shopApi.ts
export const importShopItems = (body: {
    childId: unknown;
    rows: Array<Record<string, unknown>>;
}) => flushPendingCrudSave().then(() => postJsonResultWithValidation('/api/shop/import', body));


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


export const approveRequest = (requestId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}/approve${buildChildQuery(childId)}`, {});

export const rejectRequest = (requestId: unknown, childId?: unknown) =>
    postJsonAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}/reject${buildChildQuery(childId)}`, {});

export const deleteRequest = (requestId: unknown, childId?: unknown) =>
    deleteResourceAfterPendingSave(`/api/requests/${encodeURIComponent(String(requestId))}${buildChildQuery(childId)}`, 'Delete request failed');


export const deleteHistoryItem = (historyId: unknown, childId?: unknown) =>
    deleteResourceAfterPendingSave(`/api/history/${encodeURIComponent(String(historyId))}${buildChildQuery(childId)}`, 'Delete history failed');


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

/** Deactivate or reactivate a child profile without deleting data. */
export const adminSetChildActive = (childId: unknown, active: boolean) =>
    postJson(`/api/children/${encodeURIComponent(String(childId))}/${active ? 'reactivate' : 'deactivate'}`, {});

/** List inactive child profiles for the current family. */
export async function adminGetInactiveChildren(): Promise<Child[]> {
    const payload = await fetchGet<{ id: number | string; status?: string; [key: string]: unknown }[] | null>(
        '/api/children/inactive'
    );
    return (payload ?? []).map((child) => normalizeChild(child) as unknown as Child);
}

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
export const adminSaveLimits = (childId: unknown, limits: { name: string; dailyCoinLimit?: number; monthlyLimit?: number; dailyRewardLimit?: number }) =>
    postJson(`/api/children/${encodeURIComponent(String(childId))}/settings`, limits);

export const saveChildGroupOrder = (childId: unknown, section: 'tasks' | 'shop', groups: string[], hiddenGroups: string[] = []) =>
    postJsonResult(`/api/children/${encodeURIComponent(String(childId))}/group-order`, { section, groups, hiddenGroups });


export type ChildTelegramConnection = {
    childId: number;
    linked: boolean;
    telegramUserId?: number | null;
};

/** Get the Telegram linkage status of a child profile. */
export async function adminGetChildTelegram(childId: unknown): Promise<ChildTelegramConnection | null> {
    return fetchGet<ChildTelegramConnection>(`/api/children/${encodeURIComponent(String(childId))}/telegram`);
}

/** Create a single-use invite that binds the child's Telegram account. */
export async function adminCreateChildTelegramInvite(childId: unknown): Promise<{ launchUrl: string } | null> {
    const result = await postJsonResult<{ launchUrl: string }>(
        `/api/children/${encodeURIComponent(String(childId))}/telegram/invite`, {});
    return result.ok ? result.data : null;
}

/** Unlink the child's Telegram account. */
export async function adminUnlinkChildTelegram(childId: unknown): Promise<boolean> {
    const result = await postJsonResult<void>(
        `/api/children/${encodeURIComponent(String(childId))}/telegram/unlink`, {});
    return result.ok;
}


export const registerPushTokenOnServer = (payload: unknown) =>
    postJson('/api/push/register', payload);

export const unregisterPushTokenOnServer = (payload: unknown) =>
    postJson('/api/push/unregister', payload);


export async function loadAnalyticsData(childId?: unknown, timeframe = 'month') {
    const q = new URLSearchParams({ timeframe: String(timeframe) });
    if (childId != null) q.set('childId', String(childId));
    try {
        const res = await fetchWithCsrf(`/api/analytics?${q}`);
        return res.ok ? await parseJsonSafe(res) : null;
    } catch { return null; }
}


export const searchFriend = async (query: string) => {
    try {
        const res = await fetchWithCsrf(`/api/search-user?nickname=${encodeURIComponent(query)}`);
        return res.ok ? await parseJsonSafe(res) : [];
    } catch { return []; }
};

export const addFriend = (friendId: unknown) =>
    postBoolean('/api/add-friend', { friendId }, 'Add friend failed');
