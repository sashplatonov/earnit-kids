import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import {
    buildTelegramShareUrl,
    sharePublicSite,
    type ShareResult,
} from '../../src/lib/services/publicSiteShare';

describe('buildTelegramShareUrl', () => {
    it('builds a t.me share link for the public site root', () => {
        expect(buildTelegramShareUrl('https://earnit-kids.example', '/')).toBe(
            'https://t.me/share/url?url=https%3A%2F%2Fearnit-kids.example%2F'
        );
    });

    it('builds a t.me share link for a subpage path', () => {
        const url = buildTelegramShareUrl('https://earnit-kids.example', '/how');
        expect(url).toContain('https://t.me/share/url?url=');
        expect(decodeURIComponent(url)).toContain('https://earnit-kids.example/how');
    });

    it('defaults to the public site root when no path is given', () => {
        const url = buildTelegramShareUrl('https://earnit-kids.example');
        expect(url).toBe('https://t.me/share/url?url=https%3A%2F%2Fearnit-kids.example%2F');
    });
});

describe('sharePublicSite', () => {
    let windowSpy: { window?: Window } & { navigator?: Navigator };

    beforeEach(() => {
        windowSpy = {} as { window?: Window } & { navigator?: Navigator };
        vi.stubGlobal('window', windowSpy);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('returns unsupported when not in a browser context', async () => {
        vi.unstubAllGlobals();
        delete (globalThis as { window?: unknown }).window;
        const result: ShareResult = await sharePublicSite('https://earnit-kids.example', '/');
        expect(result).toEqual({ ok: false, reason: 'unsupported' });
    });

    it('uses the Web Share API when available', async () => {
        const share = vi.fn().mockResolvedValue(undefined);
        windowSpy.navigator = { share } as unknown as Navigator;
        vi.stubGlobal('window', { navigator: windowSpy.navigator });

        const result: ShareResult = await sharePublicSite('https://earnit-kids.example', '/');
        expect(share).toHaveBeenCalledOnce();
        expect(result).toEqual({ ok: true, method: 'web-share' });
    });

    it('falls back to clipboard when Web Share API is rejected', async () => {
        const share = vi.fn().mockRejectedValue(new Error('cancel'));
        windowSpy.navigator = {
            share,
            clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
        } as unknown as Navigator;
        vi.stubGlobal('window', { navigator: windowSpy.navigator });

        const result: ShareResult = await sharePublicSite('https://earnit-kids.example', '/how');
        expect(share).toHaveBeenCalledOnce();
        expect(result).toEqual({ ok: true, method: 'clipboard' });
    });

    it('falls back to Telegram openTelegramLink when neither share nor clipboard is available', async () => {
        const openTelegramLink = vi.fn();
        windowSpy.navigator = {} as Navigator;
        vi.stubGlobal('window', {
            navigator: windowSpy.navigator,
            Telegram: { WebApp: { openTelegramLink } },
        });

        const result: ShareResult = await sharePublicSite('https://earnit-kids.example', '/faq');
        expect(openTelegramLink).toHaveBeenCalledOnce();
        expect(openTelegramLink).toHaveBeenCalledWith(expect.stringContaining('t.me/share/url'));
        expect(result).toEqual({ ok: true, method: 'telegram' });
    });

    it('returns unsupported when no sharing mechanism is available', async () => {
        windowSpy.navigator = {} as Navigator;
        vi.stubGlobal('window', { navigator: windowSpy.navigator });

        const result: ShareResult = await sharePublicSite('https://earnit-kids.example', '/');
        expect(result).toEqual({ ok: false, reason: 'unsupported' });
    });
});
