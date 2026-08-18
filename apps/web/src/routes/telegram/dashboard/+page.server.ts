import { fail, redirect } from '@sveltejs/kit';
import { isAdminRole } from '$lib/app/routes';
import type { Actions, PageServerLoad } from './$types';
import { localizePath } from '$lib/i18n';

export const load: PageServerLoad = async ({ locals, fetch }) => {
    // Verify admin access server-side
    if (!locals.session.authenticated) {
        throw redirect(302, localizePath('/login', locals.locale));
    }

    if (!isAdminRole(locals.session.role)) {
        throw redirect(302, localizePath('/app/settings', locals.locale));
    }

    // Fetch aggregated dashboard data - session cookie is passed automatically
    let overview = null;
    let coinEconomy = null;
    let taskEconomy = null;
    let parentBehavior = null;
    let childBehavior = null;
    let activationFunnel = null;
    let retention = null;
    let trends = null;
    try {
        const [dashboardRes, trendsRes] = await Promise.all([
            fetch('/api/admin/dashboard?period=30d'),
            fetch('/api/admin/analytics/trends?period=30d'),
        ]);
        if (dashboardRes.ok) {
            const dashboard = await dashboardRes.json();
            overview = { overview: dashboard.overview };
            coinEconomy = dashboard.coinEconomy;
            taskEconomy = dashboard.tasks;
            parentBehavior = dashboard.parentSignals;
            childBehavior = dashboard.childSignals;
            activationFunnel = dashboard.activation;
            retention = dashboard.activity;
        }
        if (trendsRes.ok) {
            trends = await trendsRes.json();
        }
    } catch (e) {
        console.error('Failed to fetch admin analytics:', e);
    }

    return {
        overview,
        coinEconomy,
        taskEconomy,
        parentBehavior,
        childBehavior,
        activationFunnel,
        retention,
        trends,
    };
};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
