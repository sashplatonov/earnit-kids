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
    it('preserves dashboard data when trends fails', async () => {
        const dashboard = { overview: { totalFamilies: 2 }, unavailableSections: [] };
        const fetch = vi.fn<typeof globalThis.fetch>(async (input) => {
            if (String(input).includes('/trends')) return new Response('unavailable', { status: 503 });
            return new Response(JSON.stringify(dashboard), { status: 200 });
        });

        const result = await load(makeEvent(fetch, '7d'));

        expect(result).toMatchObject({
            dashboardStatus: 'available',
            trendsStatus: 'unavailable',
            period: '7d',
            overview: { overview: dashboard.overview },
            trends: null,
        });
        expect(fetch).toHaveBeenCalledWith('/api/admin/dashboard?period=7d');
        expect(fetch).toHaveBeenCalledWith('/api/admin/analytics/trends?period=7d');
    });

    it('keeps successful sections and reports a failed dashboard section', async () => {
        const dashboard = {
            overview: { totalFamilies: 2 },
            coinEconomy: null,
            tasks: { taskMetrics: { taskCompletions: 4 } },
            unavailableSections: ['coinEconomy'],
        };
        const fetch = vi.fn<typeof globalThis.fetch>(async (input) => {
            if (String(input).includes('/trends')) return new Response(JSON.stringify({ points: [] }));
            return new Response(JSON.stringify(dashboard));
        });

        const result = await load(makeEvent(fetch, 'all'));

        expect(result).toMatchObject({
            dashboardStatus: 'partial',
            unavailableSections: ['coinEconomy'],
            coinEconomy: null,
            taskEconomy: dashboard.tasks,
            period: 'all',
        });
    });
});
