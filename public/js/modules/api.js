/** @file Api frontend UI module */
function getCsrfToken() {
    const cookieRow = document.cookie
        .split(';')
        .map(row => row.trim())
        .find(row => row.startsWith('csrf_token='));

    if (!cookieRow) return '';
    return decodeURIComponent(cookieRow.slice('csrf_token='.length));
}

async function fetchWithCsrf(url, options = {}) {
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
export const API_URL = '/api/data';
export const LOGIN_URL = '/api/login';
export const LOGOUT_URL = '/api/logout';
export const CHANGE_PIN_URL = '/api/change-pin';
export const PUSH_REGISTER_URL = '/api/push/register';
export const PUSH_UNREGISTER_URL = '/api/push/unregister';

// ...existing code...
export async function loadDataFromServer() {
    try {
        const response = await fetchWithCsrf('/api/data');
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to load from server:', err);
    }
    return null;
}

export async function loadBaseData() {
    try {
        const response = await fetchWithCsrf('/api/base-data');
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to load base data:', err);
    }
    return { tasks: [], products: [] };
}
// ...existing code...

export async function saveDataToServer(data) {
    try {
        const response = await fetchWithCsrf(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            let errorDetails = '';
            try {
                const payload = await response.clone().json();
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
        return await response.json();
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
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function changePin(oldPin, newPin, role) {
    try {
        const response = await fetchWithCsrf(CHANGE_PIN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPin, newPin, role })
        });
        if (response.ok) {
            return { success: true };
        } else {
            const data = await response.json();
            return { success: false, error: data.error };
        }
    } catch (err) {
        return { success: false, error: 'Ошибка сети' };
    }
}

export async function login(pin) {
    try {
        const response = await fetchWithCsrf(LOGIN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ pin })
        });
        if (response.ok) {
            const data = await response.json();
            return { success: true, role: data.role };
        } else {
            const data = await response.json();
            return { success: false, error: data.error, status: response.status };
        }
    } catch (err) {
        return { success: false, error: 'Network Error' };
    }
}

export async function regenerateChildToken(childId) {
    try {
        const response = await fetchWithCsrf(`/api/children/${childId}/regenerate-token`, { method: 'POST' });
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to regenerate token:', err);
    }
    return null;
}

export async function addChild(name) {
    try {
        const response = await fetchWithCsrf('/api/children', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function deleteChild(childId) {
    try {
        const response = await fetchWithCsrf(`/api/children/${childId}`, { method: 'DELETE' });
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function getChildLink(childId) {
    try {
        const response = await fetchWithCsrf(`/api/children/${childId}/link`);
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function updateChildSettings(familyId, childId, settings) {
    try {
        const response = await fetchWithCsrf(`/api/children/${childId}/settings`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(settings)
        });
        return await response.json();
    } catch (err) {
        console.error('Failed to update child settings:', err);
        return { success: false, error: 'Network error' };
    }
}

export async function updateNickname(nickname) {
    try {
        const response = await fetchWithCsrf('/api/update-nickname', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nickname })
        });
        return await response.json();
    } catch (err) {
        console.error('Failed to update nickname:', err);
        return { success: false, error: 'Ошибка сети' };
    }
}

export async function searchUsers(nickname) {
    try {
        const response = await fetchWithCsrf(`/api/search-user?nickname=${encodeURIComponent(nickname)}`);
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to search users:', err);
    }
    return [];
}

export async function addFriend(friendId) {
    try {
        const response = await fetchWithCsrf('/api/add-friend', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ friendId })
        });
        return await response.json();
    } catch (err) {
        console.error('Failed to add friend:', err);
        return { success: false, error: 'Ошибка сети' };
    }
}

export async function loadFriendsList() {
    try {
        const response = await fetchWithCsrf('/api/friends-list');
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to load friends list:', err);
    }
    return [];
}

export async function savePreference(key, value) {
    try {
        const response = await fetchWithCsrf('/api/preferences', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ [key]: value })
        });
        return response.ok;
    } catch (err) {
        console.error('Failed to save preference:', err);
        return false;
    }
}

export async function saveChildTheme(childId, theme) {
    try {
        const response = await fetchWithCsrf(`/api/children/${childId}/theme`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ theme })
        });
        return response.ok;
    } catch (err) {
        console.error('Failed to save child theme:', err);
        return false;
    }
}

export async function fetchAnalyticsData(timeframe = 'month', childId = null) {
    try {
        let url = `/api/analytics?timeframe=${timeframe}`;
        if (childId) url += `&childId=${childId}`;
        const response = await fetchWithCsrf(url);
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to fetch analytics:', err);
    }
    return null;
}
