import { state } from './state.js';
import { registerPushTokenOnServer, unregisterPushTokenOnServer } from './api.js';
import { showMobileEventNotification } from './utils.js';

let listenersBound = false;
let currentToken = '';
let refreshHandler = null;

function isNativeCapacitor() {
    if (typeof window === 'undefined') return false;
    if (!window.Capacitor) return false;
    if (typeof window.Capacitor.isNativePlatform !== 'function') return false;
    return window.Capacitor.isNativePlatform();
}

function getPushPlugin() {
    if (!isNativeCapacitor()) return null;
    return window.Capacitor.Plugins?.PushNotifications || null;
}

function getPlatform() {
    if (!window.Capacitor || typeof window.Capacitor.getPlatform !== 'function') {
        return 'unknown';
    }
    return window.Capacitor.getPlatform() || 'unknown';
}

async function syncTokenToServer(tokenValue) {
    if (!tokenValue) return;

    const role = state.isAdmin ? 'admin' : 'child';
    const childId = role === 'child'
        ? (state.children?.[0]?.id || state.currentChildId || null)
        : null;

    const payload = {
        token: tokenValue,
        platform: getPlatform(),
        role,
        childId
    };

    const result = await registerPushTokenOnServer(payload);
    if (!result?.success) {
        console.warn('[push] token register failed:', result?.error || 'unknown');
    }
}

function bindPushListeners(push) {
    if (listenersBound) return;
    listenersBound = true;

    push.addListener('registration', async (token) => {
        currentToken = token?.value || '';
        await syncTokenToServer(currentToken);
    });

    push.addListener('registrationError', (error) => {
        console.error('[push] registration error:', error);
    });

    push.addListener('pushNotificationReceived', (notification) => {
        const title = notification?.title || 'Coins Kids Shop';
        const message = notification?.body || 'Новое уведомление';
        showMobileEventNotification(message, 'info', title);
        if (typeof refreshHandler === 'function') {
            void refreshHandler(notification?.data || {});
        }
    });

    push.addListener('pushNotificationActionPerformed', (event) => {
        if (typeof refreshHandler === 'function') {
            void refreshHandler(event?.notification?.data || {});
        }
    });
}

export function setPushRefreshHandler(handler) {
    refreshHandler = typeof handler === 'function' ? handler : null;
}

export async function initializePushNotifications() {
    const push = getPushPlugin();
    if (!push) return;

    bindPushListeners(push);

    try {
        const permissionStatus = await push.requestPermissions();
        if (permissionStatus.receive !== 'granted') {
            console.warn('[push] permission not granted');
            return;
        }

        await push.register();
    } catch (err) {
        console.error('[push] initialize failed:', err);
    }
}

export async function unregisterPushNotifications() {
    const push = getPushPlugin();
    if (!push) return;

    try {
        if (currentToken) {
            await unregisterPushTokenOnServer(currentToken);
            currentToken = '';
        }
        await push.removeAllListeners();
        listenersBound = false;
    } catch (err) {
        console.error('[push] unregister failed:', err);
    }
}
