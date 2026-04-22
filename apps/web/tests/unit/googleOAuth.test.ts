import { describe, expect, it, vi } from 'vitest';
import {
    GOOGLE_LOGIN_NETWORK_ERROR,
    GOOGLE_LOGIN_URL_UNAVAILABLE,
    requestGoogleLoginUrl,
} from '../../src/lib/auth/googleOAuth';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

describe('requestGoogleLoginUrl', () => {
    it('requests a server-side Google OAuth url for the target redirect', async () => {
        const fetchMock = vi
            .fn<typeof fetch>()
            .mockResolvedValue(jsonResponse({ url: 'https://accounts.google.com/o/oauth2/v2/auth?state=token' }));

        const url = await requestGoogleLoginUrl(fetchMock, '/en/app');

        expect(url).toBe('https://accounts.google.com/o/oauth2/v2/auth?state=token');
        expect(fetchMock).toHaveBeenCalledWith('/api/login-google/url?redirect_to=%2Fen%2Fapp', {
            credentials: 'same-origin',
            cache: 'no-store',
        });
    });

    it('surfaces backend error messages for Google OAuth startup failures', async () => {
        const fetchMock = vi
            .fn<typeof fetch>()
            .mockResolvedValue(jsonResponse({ error: 'Google sign-in is not configured' }, 400));

        await expect(requestGoogleLoginUrl(fetchMock, '/ru/app')).rejects.toThrow('Google sign-in is not configured');
    });

    it('maps missing payload urls to a stable unavailable error', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ ok: true }));

        await expect(requestGoogleLoginUrl(fetchMock, '/en/app')).rejects.toThrow(GOOGLE_LOGIN_URL_UNAVAILABLE);
    });

    it('maps fetch failures to a stable network error', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(new Error('offline'));

        await expect(requestGoogleLoginUrl(fetchMock, '/en/app')).rejects.toThrow(GOOGLE_LOGIN_NETWORK_ERROR);
    });
});