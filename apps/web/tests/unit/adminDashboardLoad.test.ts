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
    it('loads the complete Statistics payload in one request', async () => {
        const dashboard = { overview: { totalFamilies: 2 }, trends: { points: [] }, unavailableSections: [] };
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response(JSON.stringify(dashboard), { status: 200 }));

        const result = await load(makeEvent(fetch, '7d'));

        expect(result).toMatchObject({
            dashboardStatus: 'available',
            period: '7d',
            overview: { overview: dashboard.overview },
            trends: dashboard.trends,
            trendsStatus: 'available',
        });
        expect(fetch).toHaveBeenCalledWith('/api/admin/dashboard?period=7d');
        expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('keeps successful sections and reports a failed dashboard section', async () => {
        const dashboard = {
            overview: { totalFamilies: 2 },
            coinEconomy: null,
            tasks: { taskMetrics: { taskCompletions: 4 } },
            trends: { points: [] },
            unavailableSections: ['coinEconomy'],
        };
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response(JSON.stringify(dashboard)));

        const result = await load(makeEvent(fetch, 'all'));

        expect(result).toMatchObject({
            dashboardStatus: 'partial',
            unavailableSections: ['coinEconomy'],
            coinEconomy: null,
            taskEconomy: dashboard.tasks,
            period: 'all',
        });
    });

    it('keeps dashboard sections when the bundled trends query is unavailable', async () => {
        const dashboard = { overview: { totalFamilies: 2 }, unavailableSections: ['trends'] };
        const fetch = vi.fn<typeof globalThis.fetch>(async () => new Response(JSON.stringify(dashboard)));

        const result = await load(makeEvent(fetch));

        expect(result).toMatchObject({
            dashboardStatus: 'available',
            trendsStatus: 'unavailable',
            overview: { overview: dashboard.overview },
            trends: null,
        });
        expect(fetch).toHaveBeenCalledTimes(1);
    });
});
