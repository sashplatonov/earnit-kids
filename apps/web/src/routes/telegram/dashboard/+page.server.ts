import { fail, redirect } from '@sveltejs/kit';
import { isAdminRole } from '$lib/auth/roles';
import type { Actions, PageServerLoad } from './$types';

// EXPLAIN: The admin dashboard lives inside the Telegram Mini App block
// EXPLAIN: (/telegram/*), a bare surface whose family locale is resolved by
// the authenticated Mini App bootstrap.
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

    // EXPLAIN: Render the first visible tab without waiting for every expensive
    // analytics aggregate. The client requests the remaining tab data only when
    // the administrator opens it.
    let overview = null;
    const coinEconomy = null;
    const taskEconomy = null;
    const parentBehavior = null;
    const childBehavior = null;
    const activationFunnel = null;
    const retention = null;
    const rewards = null;
    const trends = null;
    let dashboardStatus: 'available' | 'partial' | 'unavailable' = 'unavailable';
    const trendsStatus: 'available' | 'unavailable' = 'unavailable';
    let unavailableSections: string[] = [];

    try {
        const overviewRes = await fetch(`/api/admin/analytics/overview?period=${period}`);
        if (!overviewRes.ok) {
            console.error('Admin overview fetch failed:', `HTTP ${overviewRes.status}`);
        } else {
            try {
                overview = await overviewRes.json();
                if (overview?.overview == null) {
                    unavailableSections = ['overview'];
                } else {
                    dashboardStatus = 'available';
                }
            } catch (e) {
                console.error('Failed to parse admin overview response:', e);
                dashboardStatus = 'unavailable';
            }
        }
    } catch (e) {
        console.error('Admin overview fetch failed:', e);
    }

    return {
        isAdmin: true,
        overview,
        coinEconomy,
        taskEconomy,
        parentBehavior,
        childBehavior,
        activationFunnel,
        retention,
        rewards,
        trends,
        dashboardStatus,
        trendsStatus,
        unavailableSections,
        period,
    };

};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
