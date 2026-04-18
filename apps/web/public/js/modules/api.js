/** @file Api frontend UI module */
function getCsrfToken() {
    const cookieRow = document.cookie
        .split(';')
        .map(row => row.trim())
        .find(row => row.startsWith('csrf_token='));

    if (!cookieRow) return '';
    return decodeURIComponent(cookieRow.slice('csrf_token='.length));
}

export async function fetchWithCsrf(url, options = {}) {
    if (['POST', 'PUT', 'DELETE'].includes((options.method || 'GET').toUpperCase())) {
        options.headers = options.headers || {};
        const csrfToken = getCsrfToken();
        if (csrfToken) options.headers['X-CSRF-Token'] = csrfToken;
    }
    return fetch(url, {
        credentials: 'same-origin',
        ...options
    });
}

async function parseJsonSafe(response) {
    if (typeof response.text !== 'function') {
        return typeof response.json === 'function' ? response.json() : null;
    }
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

function buildJsonRequestOptions(method, payload, options = {}) {
    return {
        method,
        headers: { 'Content-Type': 'application/json' },
        ...options,
        body: JSON.stringify(payload)
    };
}

async function fetchOkJson(url, fallback, errorMessage) {
    try {
        const response = await fetchWithCsrf(url);
        return response.ok ? await parseJsonSafe(response) : fallback;
    } catch (err) {
        if (errorMessage) console.error(errorMessage, err);
        return fallback;
    }
}

async function postJsonResult(url, payload, options = {}) {
    try {
        const response = await fetchWithCsrf(url, buildJsonRequestOptions('POST', payload, options.requestOptions || {}));
        return await parseJsonSafe(response);
    } catch (err) {
        return options.fallback;
    }
}

async function postBoolean(url, payload, errorMessage) {
    try {
        const response = await fetchWithCsrf(url, buildJsonRequestOptions('POST', payload));
        return response.ok;
    } catch (err) {
        console.error(errorMessage, err);
        return false;
    }
}

export const API_URL = '/api/data';
export const LOGIN_URL = '/api/login';
export const LOGOUT_URL = '/api/logout';
export const CHANGE_PASSWORD_URL = '/api/change-password';
export const PUSH_REGISTER_URL = '/api/push/register';
export const PUSH_UNREGISTER_URL = '/api/push/unregister';

export async function loadDataFromServer(childId = null) {
    return fetchOkJson(`/api/data${buildChildQuery(childId)}`, null, 'Failed to load from server:');
}

export async function loadBaseData() {
    return fetchOkJson('/api/base-data', { tasks: [], products: [] }, 'Failed to load base data:');
}

export async function saveDataToServer(data, options = {}) {
    try {
        const response = await fetchWithCsrf(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            keepalive: options.keepalive === true,
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            let errorDetails = '';
            try {
                const payload = await parseJsonSafe(response.clone());
                errorDetails = payload?.error ? `: ${payload.error}` : '';
            } catch (_) {
                // Ignore parse errors for non-JSON responses.
            }
            console.error(`Failed to save data (${response.status})${errorDetails}`);
        }
        return response.ok;
    } catch (err) {
        console.error('Failed to save to server:', err);
        return false;
    }
}

function buildChildQuery(childId) {
    return childId === null || childId === undefined
        ? ''
        : `?childId=${encodeURIComponent(childId)}`;
}

async function callAction(url, options = {}) {
    try {
        const response = await fetchWithCsrf(url, options);
        const payload = await parseJsonSafe(response);
        if (response.ok) {
            return { success: true, data: payload };
        }
        return { success: false, error: payload?.error || 'Не удалось выполнить действие' };
    } catch (err) {
        console.error('Action request failed:', err);
        return { success: false, error: 'Ошибка сети' };
    }
}

export function completeTaskOnServer(taskId, childId) {
    return callAction(`/api/tasks/${encodeURIComponent(taskId)}/complete${buildChildQuery(childId)}`, {
        method: 'POST'
    });
}

export const requestTaskCompletionOnServer = (taskId) => callAction(`/api/tasks/${encodeURIComponent(taskId)}/request`, { method: 'POST' });

export const purchaseItemOnServer = (itemId, childId) => callAction(`/api/shop/${encodeURIComponent(itemId)}/purchase${buildChildQuery(childId)}`, { method: 'POST' });

export const requestItemPurchaseOnServer = (itemId) => callAction(`/api/shop/${encodeURIComponent(itemId)}/request`, { method: 'POST' });

export const approveRequestOnServer = (requestId, childId) => callAction(`/api/requests/${encodeURIComponent(requestId)}/approve${buildChildQuery(childId)}`, { method: 'POST' });

export const rejectRequestOnServer = (requestId, childId) => callAction(`/api/requests/${encodeURIComponent(requestId)}/reject${buildChildQuery(childId)}`, { method: 'POST' });

export const deleteRequestOnServer = (requestId, childId) => callAction(`/api/requests/${encodeURIComponent(requestId)}${buildChildQuery(childId)}`, { method: 'DELETE' });

export const deleteHistoryEntryOnServer = (historyEntryId, childId) => callAction(`/api/history/${encodeURIComponent(historyEntryId)}${buildChildQuery(childId)}`, { method: 'DELETE' });

export const adjustBalanceOnServer = (childId, amount, description) => callAction('/api/balance/adjust', buildJsonRequestOptions('POST', { childId, amount, description }));

export async function logout() {
    try {
        const response = await fetchWithCsrf(LOGOUT_URL, { method: 'POST' });
        return response.ok;
    } catch (err) {
        console.error('Logout failed:', err);
        return false;
    }
}

export async function registerPushTokenOnServer(payload) {
    try {
        const response = await fetchWithCsrf(PUSH_REGISTER_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (response.ok) return { success: true };
        return await parseJsonSafe(response);
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function unregisterPushTokenOnServer(token) {
    try {
        const response = await fetchWithCsrf(PUSH_UNREGISTER_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token })
        });
        if (response.ok) return { success: true };
        return await parseJsonSafe(response);
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function changePassword(oldPassword, newPassword) {
    try {
        const response = await fetchWithCsrf(CHANGE_PASSWORD_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            cache: 'no-store',
            body: JSON.stringify({ oldPassword, newPassword })
        });
        if (response.ok) {
            return { success: true };
        } else {
            const data = await parseJsonSafe(response);
            return { success: false, error: data.error };
        }
    } catch (err) {
        return { success: false, error: 'Ошибка сети' };
    }
}

export async function login(email, password) {
    try {
        const response = await fetchWithCsrf(LOGIN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            cache: 'no-store',
            body: JSON.stringify({ email, password })
        });
        if (response.ok) {
            const data = await parseJsonSafe(response);
            return { success: true, role: data.role };
        } else {
            const data = await parseJsonSafe(response);
            return { success: false, error: data.error, status: response.status };
        }
    } catch (err) {
        return { success: false, error: 'Network Error' };
    }
}

// Keep compatibility alias removed — use changePassword instead of changePin

export async function regenerateChildToken(childId) {
    return fetchOkJson(`/api/children/${childId}/regenerate-token`, null, 'Failed to regenerate token:');
}

export const addChild = (name) => postJsonResult('/api/children', { name }, { fallback: { success: false, error: 'Network error' } });

export async function deleteChild(childId) {
    try {
        return await parseJsonSafe(await fetchWithCsrf(`/api/children/${childId}`, { method: 'DELETE' }));
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export const getChildLink = (childId) => fetchOkJson(`/api/children/${childId}/link`, { success: false, error: 'Network error' });

export const updateChildSettings = (familyId, childId, settings) => postJsonResult(`/api/children/${childId}/settings`, settings, { fallback: { success: false, error: 'Network error' } });

export const updateNickname = (nickname) => postJsonResult('/api/update-nickname', { nickname }, { fallback: { success: false, error: 'Ошибка сети' } });

export const searchUsers = (nickname) => fetchOkJson(`/api/search-user?nickname=${encodeURIComponent(nickname)}`, [], 'Failed to search users:');

export const addFriend = (friendId) => postJsonResult('/api/add-friend', { friendId }, { fallback: { success: false, error: 'Ошибка сети' } });

export const loadFriendsList = () => fetchOkJson('/api/friends-list', [], 'Failed to load friends list:');

export const savePreference = (key, value) => postBoolean('/api/preferences', { key, value }, 'Failed to save preference:');

export const saveChildTheme = (childId, theme) => postBoolean(`/api/children/${childId}/theme`, { theme }, 'Failed to save child theme:');

export async function fetchAnalyticsData(timeframe = 'month', childId = null) {
    const childQuery = childId ? `&childId=${childId}` : '';
    return fetchOkJson(`/api/analytics?timeframe=${timeframe}${childQuery}`, null, 'Failed to fetch analytics:');
}
