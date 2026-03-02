/** @file Push frontend UI module */
import { state } from './state.js';
import { registerPushTokenOnServer, unregisterPushTokenOnServer } from './api.js';
import { showMobileEventNotification } from './utils.js';

let listenersBound = false;
let currentToken = '';
let currentWebSubscription = null;
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
        return 'web';
    }
    return window.Capacitor.getPlatform() || 'web';
}

function getPushChildId(role) {
    if (role !== 'child') return null;
    if (state.children && state.children.length > 0) return state.children[0].id;
    return state.currentChildId || null;
}

async function syncTokenToServer(tokenValue) {
    if (!tokenValue) return;

    const role = state.isAdmin ? 'admin' : 'child';
    const childId = getPushChildId(role);

    const payload = { token: tokenValue, platform: getPlatform(), role, childId };
    const result = await registerPushTokenOnServer(payload);

    if (!result || !result.success) {
        const err = result ? result.error : 'unknown';
        console.warn('❌ [push] token register failed:', err || 'unknown');
    } else {
        console.log(`🔔 [push] ${role === 'admin' ? 'Родитель' : 'Ребенок'} подключен к пуш-уведомлениям (native)`);
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
        const title = notification?.title || 'EarnIt Kids';
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

// --- Web Push (browser PWA) ---

function getVapidPublicKey() {
    return document.querySelector('meta[name="vapid-public-key"]')?.content?.trim() || '';
}

function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = atob(base64);
    return Uint8Array.from([...rawData].map((char) => char.charCodeAt(0)));
}

async function syncWebSubscriptionToServer(subscription) {
    const role = state.isAdmin ? 'admin' : 'child';
    const childId = getPushChildId(role);
    const json = subscription.toJSON();

    const payload = {
        pushType: 'web',
        endpoint: json.endpoint,
        keyP256dh: json.keys?.p256dh || '',
        keyAuth: json.keys?.auth || '',
        platform: 'web',
        role,
        childId
    };

    const result = await registerPushTokenOnServer(payload);
    if (!result || !result.success) {
        console.warn('❌ [push] web subscription register failed:', result?.error || 'unknown');
    } else {
        console.log(`🔔 [push] ${role === 'admin' ? 'Родитель' : 'Ребенок'} подключен к пуш-уведомлениям (web)`);
    }
}

async function initializeWebPush() {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;

    const vapidKey = getVapidPublicKey();
    if (!vapidKey) {
        console.warn('[push] VAPID public key not found, web push disabled');
        return;
    }

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    try {
        const registration = await navigator.serviceWorker.ready;
        let subscription = await registration.pushManager.getSubscription();

        if (!subscription) {
            subscription = await registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: urlBase64ToUint8Array(vapidKey)
            });
        }

        currentWebSubscription = subscription;
        await syncWebSubscriptionToServer(subscription);
    } catch (err) {
        console.error('[push] web push subscribe failed:', err);
    }
}

// --- Init / Unregister ---

export async function initializePushNotifications() {
    console.log('🔍 [push] инициализация уведомлений...');
    const push = getPushPlugin();
    if (push) {
        // Native Capacitor path
        bindPushListeners(push);
        try {
            const permissionStatus = await push.requestPermissions();
            if (permissionStatus.receive !== 'granted') return;
            await push.register();
        } catch (err) {
            console.error('[push] initialize failed:', err);
        }
    } else {
        // Browser Web Push path
        await initializeWebPush();
    }
}

export async function unregisterPushNotifications() {
    const push = getPushPlugin();
    if (push) {
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
    } else if (currentWebSubscription) {
        try {
            const endpoint = currentWebSubscription.endpoint;
            await currentWebSubscription.unsubscribe();
            currentWebSubscription = null;
            await unregisterPushTokenOnServer(endpoint);
        } catch (err) {
            console.error('[push] web push unregister failed:', err);
        }
    }
}
