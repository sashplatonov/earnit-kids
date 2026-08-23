import { loadBrowserPushPublicKey, registerBrowserPushSubscription, unregisterBrowserPushSubscription, type BrowserPushSubscription } from '$lib/services/api';

export type BrowserPushState = 'unsupported' | 'default' | 'granted' | 'denied' | 'pending' | 'error' | 'subscribed' | 'unsubscribed';

export function isBrowserPushSupported(): boolean {
    return typeof window !== 'undefined' && 'Notification' in window && 'serviceWorker' in navigator && 'PushManager' in window;
}

export function toBrowserPushSubscription(subscription: PushSubscription): BrowserPushSubscription {
    const json = subscription.toJSON();
    const p256dh = json.keys?.p256dh;
    const auth = json.keys?.auth;
    if (!subscription.endpoint || !p256dh || !auth) throw new Error('Invalid push subscription');
    return { endpoint: subscription.endpoint, p256dh, auth };
}

export async function readBrowserPushState(): Promise<BrowserPushState> {
    if (!isBrowserPushSupported()) return 'unsupported';
    if (Notification.permission === 'denied') return 'denied';
    const subscription = await (await navigator.serviceWorker.ready).pushManager.getSubscription();
    return subscription ? 'subscribed' : Notification.permission === 'granted' ? 'unsubscribed' : 'default';
}

export async function enableBrowserPush(): Promise<BrowserPushState> {
    if (!isBrowserPushSupported()) return 'unsupported';
    if (Notification.permission === 'denied') return 'denied';
    const keyResult = await loadBrowserPushPublicKey();
    if (!keyResult.ok || !keyResult.data?.publicKey) return 'error';
    let applicationServerKey: Uint8Array;
    try {
        applicationServerKey = decodeVapidPublicKey(keyResult.data.publicKey);
    } catch { return 'error'; }
    const permission = Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission;
    if (permission !== 'granted') return 'denied';
    try {
        const subscription = await (await navigator.serviceWorker.ready).pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: applicationServerKey.buffer as ArrayBuffer,
        });
        const result = await registerBrowserPushSubscription(toBrowserPushSubscription(subscription));
        return result.ok ? 'subscribed' : 'error';
    } catch { return 'error'; }
}

function decodeVapidPublicKey(value: string): Uint8Array {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
    if (bytes.length !== 65 || bytes[0] !== 4) throw new Error('Invalid VAPID public key');
    return bytes;
}

export async function disableBrowserPush(): Promise<BrowserPushState> {
    if (!isBrowserPushSupported()) return 'unsupported';
    try {
        const subscription = await (await navigator.serviceWorker.ready).pushManager.getSubscription();
        if (!subscription) return 'unsubscribed';
        const result = await unregisterBrowserPushSubscription(toBrowserPushSubscription(subscription));
        if (!result.ok) return 'error';
        await subscription.unsubscribe();
        return 'unsubscribed';
    } catch { return 'error'; }
}
