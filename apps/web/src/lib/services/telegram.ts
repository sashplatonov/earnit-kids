import { fetchWithCsrf } from './api';

type TelegramWebApp = {
    initData?: string;
    initDataUnsafe?: { start_param?: string };
    ready?: () => void;
    expand?: () => void;
};

type TelegramGlobal = { WebApp?: TelegramWebApp };

let initialized = false;
// EXPLAIN: `telegram-web-app.js` is loaded globally in app.html, so the
// `window.Telegram.WebApp` object always exists on PC/mobile web too. Only the
// real Telegram client injects `initData`, so use that to detect the Mini App.
export function isTelegramMiniApp(): boolean {
    if (typeof window === 'undefined') return false;
    return Boolean((window as Window & { Telegram?: TelegramGlobal }).Telegram?.WebApp?.initData);
}

export function initializeTelegramWebApp(): TelegramWebApp | null {
    if (typeof window === 'undefined') {
        return null;
    }
    const telegram = (window as Window & { Telegram?: TelegramGlobal }).Telegram?.WebApp ?? null;
    if (telegram && !initialized) {
        initialized = true;
        telegram.ready?.();
        telegram.expand?.();
    }
    return telegram;
}

export async function exchangeTelegramInitData(initData: string, token?: string | null): Promise<Response> {
    return fetchWithCsrf('/api/telegram/auth/exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ initData, token: token ?? null }),
    });
}

export async function completeTelegramAccountLink(token: string, initData: string): Promise<Response> {
    return fetchWithCsrf('/api/telegram/account-connection/complete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, initData }),
    });
}
