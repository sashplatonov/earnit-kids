import { describe, expect, it, vi } from 'vitest';
import { GOOGLE_WORKSPACE_FALLBACK, enhancePublicSite, requestBrowserWorkspaceUrl } from '../../static/public/site.js';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('public site browser access', () => {
    it('localizes dynamic feedback from html lang, never browser or query preferences', () => {
        const status = { className: '', setAttribute: vi.fn(), textContent: '' };
        const documentRef = {
            documentElement: { lang: 'en' },
            querySelectorAll: vi.fn(() => []),
            querySelector: vi.fn(() => null),
            createElement: vi.fn(() => status),
            body: { append: vi.fn() },
        } as unknown as Parameters<typeof enhancePublicSite>[0];
        const windowRef = {
            location: { pathname: '/', origin: 'https://example.test', href: 'https://example.test/?lang=ru&error=oauth' },
            navigator: { languages: ['ru-RU'], language: 'ru-RU' },
            EARNIT_CONFIG: {},
            matchMedia: () => ({ matches: false }),
        } as unknown as Parameters<typeof enhancePublicSite>[1];

        enhancePublicSite(documentRef, windowRef, vi.fn());

        expect(status.textContent).toBe('Google sign-in is temporarily unavailable. Use the browser sign-in link to try again.');
    });

    it('keeps a local login fallback and requests the app OAuth target', async () => {
        const fetchMock = vi.fn<typeof fetch>()
            .mockResolvedValueOnce(jsonResponse({ googleEnabled: true }))
            .mockResolvedValueOnce(jsonResponse({ url: 'https://accounts.google.com/o/oauth2/auth?state=signed' }));

        await expect(requestBrowserWorkspaceUrl(fetchMock)).resolves.toContain('accounts.google.com');
        expect(GOOGLE_WORKSPACE_FALLBACK).toBe('/');
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/login-google/url?redirect_to=%2Fapp', {
            credentials: 'same-origin',
            cache: 'no-store',
        });
    });

    it('keeps an explicit public CTA redirect target scoped to the app route', async () => {
        const fetchMock = vi.fn<typeof fetch>()
            .mockResolvedValueOnce(jsonResponse({ googleEnabled: true }))
            .mockResolvedValueOnce(jsonResponse({ url: 'https://accounts.google.com/o/oauth2/auth?state=signed' }));

        await expect(requestBrowserWorkspaceUrl(fetchMock, { redirectTo: '/app' })).resolves.toContain('accounts.google.com');
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/login-google/url?redirect_to=%2Fapp', {
            credentials: 'same-origin',
            cache: 'no-store',
        });
    });

    it('returns one stable failure for disabled or unavailable Google startup', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ googleEnabled: false }));

        await expect(requestBrowserWorkspaceUrl(fetchMock)).rejects.toThrow('unavailable');
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ['a non-OK response', jsonResponse({ url: 'https://accounts.google.com/o/oauth2/auth?state=ignored' }, 503)],
        ['an empty URL', jsonResponse({ url: '' })],
        ['a malformed URL', jsonResponse({ url: 'not a URL' })],
        ['a foreign URL', jsonResponse({ url: 'https://attacker.example/oauth' })],
    ])('rejects %s without a usable authorization target', async (_caseName, response) => {
        const fetchMock = vi.fn<typeof fetch>()
            .mockResolvedValueOnce(jsonResponse({ googleEnabled: true }))
            .mockResolvedValueOnce(response);

        await expect(requestBrowserWorkspaceUrl(fetchMock)).rejects.toThrow('unavailable');
    });

});
