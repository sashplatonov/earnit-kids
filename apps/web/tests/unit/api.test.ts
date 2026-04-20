import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    addFriend,
    adminAddChild,
    adminAwardCoins,
    adminDeleteChild,
    adminGetChildLink,
    adminRegenerateChildLink,
    adminSaveChildSettings,
    adminSaveLimits,
    approveRequest,
    buyItem,
    deleteHistoryItem,
    deleteRequest,
    earnCoins,
    fetchWithCsrf,
    loadAnalyticsData,
    loadBaseData,
    loadDataFromServer,
    logout,
    registerPushTokenOnServer,
    rejectRequest,
    requestCoins,
    requestItem,
    saveDataToServer,
    searchFriend,
    unregisterPushTokenOnServer,
    updateOwnNickname,
} from '../../src/lib/services/api';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

function setBrowserGlobals() {
    vi.stubGlobal('document', {
        cookie: 'app_role=admin; csrf_token=test-token; theme=light',
    });
}

describe('fetchWithCsrf', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('adds the CSRF header for mutating requests when the cookie is present', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

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
        setBrowserGlobals();

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
        setBrowserGlobals();

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
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ token: 'child-token-1' }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();
        vi.stubGlobal('location', { origin: 'http://localhost:3001' });

        const result = await adminGetChildLink(15);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(result).toEqual({ link: 'http://localhost:3001/login-child/child-token-1' });
    });

    it('maps regenerated child tokens to absolute login urls', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ token: 'child-token-2' }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();
        vi.stubGlobal('location', { origin: 'http://localhost:3001' });

        const result = await adminRegenerateChildLink(15);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(result).toEqual({ link: 'http://localhost:3001/login-child/child-token-2' });
    });

    it('returns a direct child link unchanged when the backend provides one', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ link: 'https://example.com/login-child/direct-link' }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        const result = await adminGetChildLink(15);

        expect(result).toEqual({ link: 'https://example.com/login-child/direct-link' });
    });

    it('loads family data with an explicit child query', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ balance: 25, childNickname: 'Маша' }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        const result = await loadDataFromServer(15);

        expect(fetchMock).toHaveBeenCalledWith('/api/data?childId=15', expect.any(Object));
        expect(result).toEqual({ balance: 25, childNickname: 'Маша' });
    });

    it('returns null when family data loading throws', async () => {
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const fetchMock = vi.fn().mockRejectedValue(new Error('offline'));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(loadDataFromServer()).resolves.toBeNull();
        expect(errorSpy).toHaveBeenCalled();
    });

    it('returns base data from the server when available', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ tasks: [{ id: 1 }], products: [{ id: 2 }] }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(loadBaseData()).resolves.toEqual({ tasks: [{ id: 1 }], products: [{ id: 2 }] });
    });

    it('falls back to empty base data on fetch failure', async () => {
        const fetchMock = vi.fn().mockRejectedValue(new Error('offline'));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(loadBaseData()).resolves.toEqual({ tasks: [], products: [] });
    });

    it('posts save payloads with keepalive support', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true, balance: 42 }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        const payload = { balance: 42, rules: 'Homework first' };
        const result = await saveDataToServer(payload, { keepalive: true });

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit & { keepalive?: boolean }];
        expect(url).toBe('/api/data');
        expect(init.method).toBe('POST');
        expect(init.keepalive).toBe(true);
        expect(init.body).toBe(JSON.stringify(payload));
        expect(result).toEqual({ ok: true, balance: 42 });
    });

    it('returns null when saveDataToServer receives a non-ok response', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ error: 'bad' }, 400));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(saveDataToServer({ balance: 1 })).resolves.toBeNull();
    });

    it.each([
        ['earnCoins', () => earnCoins(101, 15), '/api/tasks/101/complete?childId=15', {}],
        ['buyItem', () => buyItem(201, 15), '/api/shop/201/purchase?childId=15', {}],
        ['approveRequest', () => approveRequest(301, 15), '/api/requests/301/approve?childId=15', {}],
        ['rejectRequest', () => rejectRequest(301, 15), '/api/requests/301/reject?childId=15', {}],
        ['adminAwardCoins', () => adminAwardCoins(15, 7, 'Bonus'), '/api/balance/adjust', { childId: 15, amount: 7, description: 'Bonus' }],
        ['adminSaveChildSettings', () => adminSaveChildSettings(15, { name: 'Маша', dailyCoinLimit: 5, monthlyLimit: 30 }), '/api/children/15/settings', { name: 'Маша', dailyCoinLimit: 5, monthlyLimit: 30 }],
        ['updateOwnNickname', () => updateOwnNickname('Соня'), '/api/update-nickname', { nickname: 'Соня' }],
        ['adminSaveLimits', () => adminSaveLimits(15, { dailyCoinLimit: 5, monthlyLimit: 20 }), '/api/children/15/settings', { dailyCoinLimit: 5, monthlyLimit: 20 }],
        ['registerPushTokenOnServer', () => registerPushTokenOnServer({ endpoint: 'push-endpoint' }), '/api/push/register', { endpoint: 'push-endpoint' }],
        ['unregisterPushTokenOnServer', () => unregisterPushTokenOnServer({ endpoint: 'push-endpoint' }), '/api/push/unregister', { endpoint: 'push-endpoint' }],
    ])('sends %s through the shared JSON POST contract', async (_name, invoke, url, body) => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(invoke()).resolves.toEqual({ ok: true });

        const [actualUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(actualUrl).toBe(url);
        expect(init.method).toBe('POST');
        expect(init.body).toBe(JSON.stringify(body));
    });

    it('wraps successful child task requests into ok/data result objects', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ balance: 25, requests: [{ id: 1 }] }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(requestCoins(101)).resolves.toEqual({
            ok: true,
            data: { balance: 25, requests: [{ id: 1 }] },
        });

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(url).toBe('/api/tasks/101/request');
        expect(init.method).toBe('POST');
        expect(init.body).toBe(JSON.stringify({}));
    });

    it('maps backend problem details for rejected shop requests', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
            detail: 'Лимит заявок по этому товару на сегодня исчерпан. Следующее обновление в 00:00.',
            errorCode: 'ITEM_REQUEST_LIMIT_REACHED',
        }, 400));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(requestItem(201)).resolves.toEqual({
            ok: false,
            error: 'Лимит заявок по этому товару на сегодня исчерпан. Следующее обновление в 00:00.',
            errorCode: 'ITEM_REQUEST_LIMIT_REACHED',
            status: 400,
        });
    });

    it('uses DELETE for request and history removal helpers', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(deleteRequest(401, 15)).resolves.toBe(true);
        await expect(deleteHistoryItem(501, 15)).resolves.toBe(true);
        await expect(adminDeleteChild(15)).resolves.toBe(true);

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/requests/401?childId=15');
        expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('DELETE');
        expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/history/501?childId=15');
        expect((fetchMock.mock.calls[1]?.[1] as RequestInit).method).toBe('DELETE');
        expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/children/15');
    });

    it('builds analytics requests with timeframe and child filters', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ summary: { totalEarned: 50 } }));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        const result = await loadAnalyticsData(15, 'week');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/analytics?timeframe=week&childId=15');
        expect(result).toEqual({ summary: { totalEarned: 50 } });
    });

    it('returns friend search results and falls back to an empty list on network failure', async () => {
        const successFetch = vi.fn().mockResolvedValue(jsonResponse([{ id: 15, nickname: 'Маша' }]));

        vi.stubGlobal('fetch', successFetch);
        setBrowserGlobals();

        await expect(searchFriend('Ма')).resolves.toEqual([{ id: 15, nickname: 'Маша' }]);

        vi.unstubAllGlobals();
        const failedFetch = vi.fn().mockRejectedValue(new Error('offline'));
        vi.stubGlobal('fetch', failedFetch);
        setBrowserGlobals();

        await expect(searchFriend('Ма')).resolves.toEqual([]);
    });

    it('returns false when addFriend fails at the transport layer', async () => {
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const fetchMock = vi.fn().mockRejectedValue(new Error('offline'));

        vi.stubGlobal('fetch', fetchMock);
        setBrowserGlobals();

        await expect(addFriend(22)).resolves.toBe(false);
        expect(errorSpy).toHaveBeenCalled();
    });
});