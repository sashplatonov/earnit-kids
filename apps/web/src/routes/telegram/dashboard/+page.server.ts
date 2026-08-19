import { fail, redirect } from '@sveltejs/kit';
import { isAdminRole } from '$lib/auth/roles';
import type { Actions, PageServerLoad } from './$types';

// EXPLAIN: The admin dashboard lives inside the Telegram Mini App block
// EXPLAIN: (/telegram/*), a Russian-only bare surface with no locale prefix.
// EXPLAIN: Auth is resolved from the same trusted session cookie as the rest
// EXPLAIN: of the app; the backend enforces admin privilege on every analytics
// EXPLAIN: endpoint (returns 401/403), so this redirect is only a UX shortcut.
export const load: PageServerLoad = async ({ locals, fetch, url }) => {
    // Verify admin access server-side
    if (!locals.session.authenticated || !isAdminRole(locals.session.role)) {
        // Send non-admins back to the Mini App home, where Telegram auth runs.
        throw redirect(302, '/telegram');
    }

    // EXPLAIN: The selected period is reflected in the URL (?period=7d|30d|90d|all)
    // EXPLAIN: so period changes reload the section data instead of being cosmetic.
    const rawPeriod = url.searchParams.get('period') ?? '30d';
    const period = ['7d', '30d', '90d', 'all'].includes(rawPeriod) ? rawPeriod : '30d';

    // Fetch aggregated dashboard data - session cookie is passed automatically.
    // EXPLAIN: Each fetch is isolated so a transient failure of one endpoint
    // EXPLAIN: never wipes out the metrics that did load. A single rejected
    // EXPLAIN: Promise.all would otherwise null every section on the page.
    let overview = null;
    let coinEconomy = null;
    let taskEconomy = null;
    let parentBehavior = null;
    let childBehavior = null;
    let activationFunnel = null;
    let retention = null;
    let rewards = null;
    let trends = null;

    const [dashboardRes, trendsRes] = await Promise.allSettled([
        fetch(`/api/admin/dashboard?period=${period}`),
        fetch(`/api/admin/analytics/trends?period=${period}`),
    ]);

    if (dashboardRes.status === 'fulfilled' && dashboardRes.value.ok) {
        try {
            const dashboard = await dashboardRes.value.json();
            overview = { overview: dashboard.overview };
            coinEconomy = dashboard.coinEconomy;
            taskEconomy = dashboard.tasks;
            parentBehavior = dashboard.parentSignals;
            childBehavior = dashboard.childSignals;
            activationFunnel = dashboard.activation;
            retention = dashboard.activity;
            rewards = dashboard.rewards;
        } catch (e) {
            console.error('Failed to parse admin dashboard response:', e);
        }
    } else {
        console.error('Admin dashboard fetch failed:', dashboardRes.status === 'rejected' ? dashboardRes.reason : `HTTP ${dashboardRes.status === 'fulfilled' ? dashboardRes.value.status : '?'}`);
    }

    if (trendsRes.status === 'fulfilled' && trendsRes.value.ok) {
        try {
            trends = await trendsRes.value.json();
        } catch (e) {
            console.error('Failed to parse admin trends response:', e);
        }
    } else {
        console.error('Admin trends fetch failed:', trendsRes.status === 'rejected' ? trendsRes.reason : `HTTP ${trendsRes.status === 'fulfilled' ? trendsRes.value.status : '?'}`);
    }

    return {
        overview,
        coinEconomy,
        taskEconomy,
        parentBehavior,
        childBehavior,
        activationFunnel,
        retention,
        rewards,
        trends,
        period,
    };

};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
