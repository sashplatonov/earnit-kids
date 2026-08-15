import { getPublicSiteUrl } from './publicSiteUrl';

// EXPLAIN: Single source of truth for sharing the public site from the Mini
// EXPLAIN: App. URLs are built via getPublicSiteUrl('/') (no hardcoded locale
// EXPLAIN: or origin), and sharing falls back from the Web Share API to the
// EXPLAIN: clipboard, with Telegram openTelegramLink for the t.me/share path.

export type ShareResult =
    | { ok: true; method: 'web-share' }
    | { ok: true; method: 'clipboard' }
    | { ok: true; method: 'telegram' }
    | { ok: false; reason: 'unsupported' | 'denied' };

type TelegramWebApp = {
    openTelegramLink?: (url: string) => void;
};

type TelegramGlobal = { WebApp?: TelegramWebApp };

function isBrowser(): boolean {
    return typeof window !== 'undefined';
}

function getTelegramWebApp(): TelegramWebApp | null {
    if (!isBrowser()) {
        return null;
    }
    return (window as Window & { Telegram?: TelegramGlobal }).Telegram?.WebApp ?? null;
}

/**
 * Build the `t.me/share/url` share link for a public site path.
 */
export function buildTelegramShareUrl(publicOrigin: string, path?: string): string {
    const url = getPublicSiteUrl(publicOrigin, path ?? '/');
    return `https://t.me/share/url?url=${encodeURIComponent(url)}`;
}

/**
 * Share a public site path. Prefers the Web Share API when available and
 * appropriate, then falls back to the clipboard, then to a Telegram share
 * link. Returns the method used or a failure reason.
 */
export async function sharePublicSite(publicOrigin: string, path?: string): Promise<ShareResult> {
    const url = getPublicSiteUrl(publicOrigin, path ?? '/');

    if (!isBrowser()) {
        return { ok: false, reason: 'unsupported' };
    }

    // 1. Web Share API (best UX on mobile and in-app browsers).
    const nav = (window as Window & { navigator?: Navigator }).navigator;
    if (nav && typeof nav.share === 'function') {
        try {
            await nav.share({
                title: 'EarnIt Kids',
                text: 'EarnIt Kids — семейные задания и награды',
                url,
            });
            return { ok: true, method: 'web-share' };
        } catch {
            // EXPLAIN: share() rejects when the user cancels or the platform
            // EXPLAIN: refuses; fall through to the clipboard path.
        }
    }

    // 2. Clipboard fallback.
    if (nav?.clipboard && typeof nav.clipboard.writeText === 'function') {
        try {
            await nav.clipboard.writeText(url);
            return { ok: true, method: 'clipboard' };
        } catch {
            // EXPLAIN: Clipboard access can be denied; fall through to Telegram.
        }
    }

    // 3. Telegram openTelegramLink with a t.me/share URL.
    const telegram = getTelegramWebApp();
    if (telegram && typeof telegram.openTelegramLink === 'function') {
        telegram.openTelegramLink(buildTelegramShareUrl(publicOrigin, path ?? '/'));
        return { ok: true, method: 'telegram' };
    }

    return { ok: false, reason: 'unsupported' };
}
