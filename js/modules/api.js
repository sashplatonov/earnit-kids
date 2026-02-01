export const API_URL = '/api/data';
export const LOGIN_URL = '/api/login';

export async function loadDataFromServer() {
    try {
        const response = await fetch(API_URL);
        if (response.ok) {
            return await response.json();
        }
    } catch (err) {
        console.error('Failed to load from server:', err);
    }
    return null;
}

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

export async function login(pin) {
    try {
        const response = await fetch(LOGIN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ pin })
        });
        if (response.ok) {
            return { success: true };
        } else {
            const data = await response.json();
            return { success: false, error: data.error, status: response.status };
        }
    } catch (err) {
        return { success: false, error: 'Network Error' };
    }
}
