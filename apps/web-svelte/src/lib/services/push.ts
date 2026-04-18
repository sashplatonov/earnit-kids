/**
 * Push notification service — replaces legacy push.js
 * Supports both Web Push and Capacitor (mobile).
 */
import { registerPushTokenOnServer, unregisterPushTokenOnServer } from './api';

/** Detect Capacitor runtime */
function isCapacitor() {
    return typeof window !== 'undefined' && 'Capacitor' in window;
}

/** Register a push token with the backend */
export async function registerPushToken(token: string, platform: string): Promise<boolean> {
    try {
        await registerPushTokenOnServer({ token, platform });
        return true;
    } catch {
        return false;
    }
}

/** Unregister a push token from the backend */
export async function unregisterPushToken(token: string, platform: string): Promise<boolean> {
    try {
        await unregisterPushTokenOnServer({ token, platform });
        return true;
    } catch {
        return false;
    }
}

/** Web Push: subscribe and register with backend */
export async function initWebPush(vapidPublicKey: string): Promise<PushSubscription | null> {
    if (!('PushManager' in window) || !('serviceWorker' in navigator)) return null;
    try {
        const reg = await navigator.serviceWorker.ready;
        const existing = await reg.pushManager.getSubscription();
        if (existing) return existing;

        const sub = await reg.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(vapidPublicKey) as BufferSource,
        });
        await registerPushTokenOnServer({ token: JSON.stringify(sub), platform: 'web' });
        return sub;
    } catch (err) {
        console.error('Web Push init failed:', err);
        return null;
    }
}

/** Capacitor Push: delegate to plugin */
export async function initCapacitorPush() {
    if (!isCapacitor()) return;
    try {
        // Dynamic import so bundler doesn't break on web
        const { PushNotifications } = await import('@capacitor/push-notifications' as string as 'module');
        const perms = await (PushNotifications as unknown as { requestPermissions(): Promise<{ receive: string }> }).requestPermissions();
        if (perms.receive !== 'granted') return;
        await (PushNotifications as unknown as { register(): Promise<void> }).register();
        (PushNotifications as unknown as { addListener(evt: string, fn: (t: { value: string }) => void): void }).addListener('registration', (token: { value: string }) => {
            void registerPushToken(token.value, 'capacitor');
        });
    } catch (err) {
        console.error('Capacitor Push init failed:', err);
    }
}

function urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = atob(base64);
    return Uint8Array.from([...rawData].map(c => c.charCodeAt(0)));
}
