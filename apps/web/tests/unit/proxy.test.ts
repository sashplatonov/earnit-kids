import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RequestEvent } from '@sveltejs/kit';
import { proxyToBackend } from '../../src/lib/server/proxy';

describe('proxyToBackend', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('normalizes forwarded request context to the configured public app origin', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ ok: true }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            })
        );

        vi.stubGlobal('fetch', fetchMock);

        const request = new Request('http://127.0.0.1:3001/api/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Cookie: 'app_role=admin',
                Origin: 'http://127.0.0.1:3001',
                'X-Forwarded-Host': '127.0.0.1:3001',
                'X-Forwarded-Port': '3001',
                'X-Forwarded-Proto': 'http',
            },
            body: JSON.stringify({ email: 'parent@example.com', password: 'secret123' }),
        });

        const event = {
            url: new URL(request.url),
            request,
            locals: {
                appConfig: {
                    backendOrigin: 'http://backend:8080',
                    publicOrigin: 'http://localhost:3001',
                    sessionPath: '/api/page-data/session',
                    wsPath: '/ws',
                    devPort: 4173,
                    previewPort: 4174,
                },
            },
        } as RequestEvent;

        await proxyToBackend(event);

        expect(fetchMock).toHaveBeenCalledTimes(1);

        const [targetUrl, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
        const headers = new Headers(init.headers);

        expect(targetUrl.toString()).toBe('http://backend:8080/api/register');
        expect(headers.get('origin')).toBe('http://localhost:3001');
        expect(headers.get('x-forwarded-host')).toBe('http://localhost:3001'.replace('http://', ''));
        expect(headers.get('x-forwarded-port')).toBe('3001');
        expect(headers.get('x-forwarded-proto')).toBe('http');
        expect(headers.get('cookie')).toBe('app_role=admin');
    });
});