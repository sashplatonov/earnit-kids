import { describe, expect, it, vi } from 'vitest';
import { isBrowserPushSupported, toBrowserPushSubscription, enableBrowserPush } from '$lib/features/workspace/notifications/browserPush';

const { loadBrowserPushPublicKey, registerBrowserPushSubscription } = vi.hoisted(() => ({
    loadBrowserPushPublicKey: vi.fn(),
    registerBrowserPushSubscription: vi.fn(),
}));
vi.mock('$lib/services/api', () => ({
    loadBrowserPushPublicKey,
    registerBrowserPushSubscription,
    unregisterBrowserPushSubscription: vi.fn(),
}));

describe('browser push', () => {
    it('detects unsupported hosts without touching push APIs', () => { vi.stubGlobal('window', {}); expect(isBrowserPushSupported()).toBe(false); });
    it('normalizes the browser subscription to the server contract', () => {
        const subscription = { endpoint: 'https://push.example/1', toJSON: () => ({ keys: { p256dh: 'public', auth: 'secret' } }) } as unknown as PushSubscription;
        expect(toBrowserPushSubscription(subscription)).toEqual({ endpoint: 'https://push.example/1', p256dh: 'public', auth: 'secret' });
    });
    it('rejects incomplete subscriptions before a network call', () => {
        const subscription = { endpoint: 'https://push.example/1', toJSON: () => ({ keys: {} }) } as unknown as PushSubscription;
        expect(() => toBrowserPushSubscription(subscription)).toThrow('Invalid push subscription');
    });

    it('requires the public VAPID key before requesting permission', async () => {
        loadBrowserPushPublicKey.mockResolvedValue({ ok: false, error: 'disabled', errorCode: null, status: 204 });
        const requestPermission = vi.fn();
        vi.stubGlobal('window', { Notification: {}, PushManager: {} });
        vi.stubGlobal('Notification', { permission: 'default', requestPermission });
        vi.stubGlobal('navigator', { serviceWorker: { ready: Promise.resolve({ pushManager: { subscribe: vi.fn() } }) } });

        await expect(enableBrowserPush()).resolves.toBe('error');
        expect(requestPermission).not.toHaveBeenCalled();
    });

    it('passes the decoded VAPID key and registers only a successful subscription', async () => {
        const publicKey = btoa(String.fromCharCode(4, ...new Array(64).fill(0)));
        const subscribe = vi.fn().mockResolvedValue({
            endpoint: 'https://push.example/1',
            toJSON: () => ({ keys: { p256dh: 'public', auth: 'secret' } }),
        });
        loadBrowserPushPublicKey.mockResolvedValue({ ok: true, data: { publicKey } });
        registerBrowserPushSubscription.mockResolvedValue({ ok: true, data: null });
        vi.stubGlobal('window', { Notification: {}, PushManager: {} });
        vi.stubGlobal('Notification', { permission: 'granted' });
        vi.stubGlobal('navigator', { serviceWorker: { ready: Promise.resolve({ pushManager: { subscribe } }) } });

        await expect(enableBrowserPush()).resolves.toBe('subscribed');
        expect(subscribe).toHaveBeenCalledWith(expect.objectContaining({
            userVisibleOnly: true,
            applicationServerKey: expect.any(ArrayBuffer),
        }));
        expect(registerBrowserPushSubscription).toHaveBeenCalledOnce();
    });
});
