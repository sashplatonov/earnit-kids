import { describe, expect, it, vi } from 'vitest';
import { GOOGLE_WORKSPACE_FALLBACK, requestBrowserWorkspaceUrl } from '../../static/public/site.js';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('public site browser access', () => {
    it('keeps a local login fallback and requests the workspace OAuth target', async () => {
        const fetchMock = vi.fn<typeof fetch>()
            .mockResolvedValueOnce(jsonResponse({ googleEnabled: true }))
            .mockResolvedValueOnce(jsonResponse({ url: 'https://accounts.google.com/o/oauth2/auth?state=signed' }));

        await expect(requestBrowserWorkspaceUrl(fetchMock, { redirectTo: '/workspace' })).resolves.toContain('accounts.google.com');
        expect(GOOGLE_WORKSPACE_FALLBACK).toBe('/public/index.html');
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/login-google/url?redirect_to=%2Fworkspace', {
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
