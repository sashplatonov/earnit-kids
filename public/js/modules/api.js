export const API_URL = 'api/data';
export const LOGIN_URL = 'api/login';
export const LOGOUT_URL = 'api/logout';
export const CHANGE_PIN_URL = 'api/change-pin';

// ...existing code...
export async function loadDataFromServer() {
    try {
        const response = await fetch('api/data');
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
        const response = await fetch('api/base-data');
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
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        return response.ok;
    } catch (err) {
        console.error('Failed to save to server:', err);
        return false;
    }
}

export async function logout() {
    try {
        const response = await fetch(LOGOUT_URL, { method: 'POST' });
        return response.ok;
    } catch (err) {
        console.error('Logout failed:', err);
        return false;
    }
}

export async function changePin(oldPin, newPin, role) {
    try {
        const response = await fetch(CHANGE_PIN_URL, {
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
        const response = await fetch(LOGIN_URL, {
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
        const response = await fetch(`/api/children/${childId}/regenerate-token`, { method: 'POST' });
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
        const response = await fetch('/api/children', {
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
        const response = await fetch(`/api/children/${childId}`, { method: 'DELETE' });
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function getChildLink(childId) {
    try {
        const response = await fetch(`/api/children/${childId}/link`);
        return await response.json();
    } catch (err) {
        return { success: false, error: 'Network error' };
    }
}

export async function updateFamilySettingsOnServer(settings) {
    try {
        const response = await fetch('/api/update-family-settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(settings)
        });
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to update family settings:', err);
    }
    return null;
}
export async function updateChildSettings(familyId, childId, settings) {
    try {
        const response = await fetch(`/api/children/${childId}/settings`, {
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
        const response = await fetch('/api/update-nickname', {
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
        const response = await fetch(`/api/search-user?nickname=${encodeURIComponent(nickname)}`);
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
        const response = await fetch('/api/add-friend', {
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
        const response = await fetch('/api/friends-list');
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to load friends list:', err);
    }
    return [];
}
