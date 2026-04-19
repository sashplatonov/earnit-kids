import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAddChild, adminGetChildLink, adminRegenerateChildLink, fetchWithCsrf, logout } from '../../src/lib/services/api';

describe('fetchWithCsrf', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('adds the CSRF header for mutating requests when the cookie is present', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', {
            cookie: 'app_role=admin; csrf_token=test-token; theme=light',
        });

        await fetchWithCsrf('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'parent@example.com', password: 'secret123' }),
        });

        expect(fetchMock).toHaveBeenCalledTimes(1);

        const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = new Headers(init.headers);

        expect(headers.get('X-CSRF-Token')).toBe('test-token');
        expect(init.credentials).toBe('same-origin');
    });

    it('does not add the CSRF header for safe requests', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', {
            cookie: 'csrf_token=test-token',
        });

        await fetchWithCsrf('/api/auth-config');

        const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = new Headers(init.headers);

        expect(headers.has('X-CSRF-Token')).toBe(false);
    });

    it('posts logout requests with csrf protection', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', {
            cookie: 'app_role=admin; csrf_token=test-token',
        });

        const ok = await logout();

        expect(ok).toBe(true);
        expect(fetchMock).toHaveBeenCalledTimes(1);

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = new Headers(init.headers);

        expect(url).toBe('/api/logout');
        expect(init.method).toBe('POST');
        expect(init.body).toBe('{}');
        expect(headers.get('Content-Type')).toBe('application/json');
        expect(headers.get('X-CSRF-Token')).toBe('test-token');
    });

    it('posts child creation payload to the child endpoint', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ id: 15, name: 'Маша' }), {
                status: 201,
                headers: { 'Content-Type': 'application/json' },
            })
        );

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', {
            cookie: 'app_role=admin; csrf_token=test-token',
        });

        const result = await adminAddChild('Маша');

        expect(fetchMock).toHaveBeenCalledTimes(1);

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = new Headers(init.headers);

        expect(url).toBe('/api/children');
        expect(init.method).toBe('POST');
        expect(init.body).toBe(JSON.stringify({ name: 'Маша' }));
        expect(headers.get('Content-Type')).toBe('application/json');
        expect(headers.get('X-CSRF-Token')).toBe('test-token');
        expect(result).toEqual({ id: 15, name: 'Маша' });
    });

    it('maps token child link payloads to absolute login urls', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ token: 'child-token-1' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            })
        );

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', { cookie: 'app_role=admin; csrf_token=test-token' });
        vi.stubGlobal('location', { origin: 'http://localhost:3001' });

        const result = await adminGetChildLink(15);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(result).toEqual({ link: 'http://localhost:3001/login-child/child-token-1' });
    });

    it('maps regenerated child tokens to absolute login urls', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ token: 'child-token-2' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            })
        );

        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', { cookie: 'app_role=admin; csrf_token=test-token' });
        vi.stubGlobal('location', { origin: 'http://localhost:3001' });

        const result = await adminRegenerateChildLink(15);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(result).toEqual({ link: 'http://localhost:3001/login-child/child-token-2' });
    });
});