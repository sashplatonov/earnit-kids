import { describe, expect, it, vi } from 'vitest';
import { load } from '../../src/routes/telegram/dashboard/+page.server';

function makeEvent(fetch: typeof globalThis.fetch, period = '7d') {
    return {
        locals: { session: { authenticated: true, role: 'admin' } },
        fetch,
        url: new URL(`https://example.test/telegram/dashboard?period=${period}`),
    } as never;
}

describe('admin dashboard server load', () => {
    it('loads only the visible Overview section on the first render', async () => {
        const overview = { overview: { totalFamilies: 2 } };
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response(JSON.stringify(overview), { status: 200 }));

        const result = await load(makeEvent(fetch, '7d'));

        expect(result).toMatchObject({
            dashboardStatus: 'available',
            period: '7d',
            overview,
            trends: null,
            trendsStatus: 'unavailable',
        });
        expect(fetch).toHaveBeenCalledWith('/api/admin/analytics/overview?period=7d');
        expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('reports the dashboard unavailable when Overview cannot be loaded', async () => {
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response('{}', { status: 503 }));

        const result = await load(makeEvent(fetch, 'all'));

        expect(result).toMatchObject({
            dashboardStatus: 'unavailable',
            unavailableSections: [],
            coinEconomy: null,
            period: 'all',
        });
    });

    it('keeps secondary sections unloaded until their tabs are opened', async () => {
        const overview = { overview: { totalFamilies: 2 } };
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response(JSON.stringify(overview)));

        const result = await load(makeEvent(fetch));

        expect(result).toMatchObject({
            dashboardStatus: 'available',
            trendsStatus: 'unavailable',
            overview,
            trends: null,
        });
        expect(fetch).toHaveBeenCalledTimes(1);
    });
});
