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

    // Fetch analytics overview data - session cookie is passed automatically
    let overview = null;
    let coinEconomy = null;
    let rewardShop = null;
    let taskEconomy = null;
    try {
        const [overviewRes, coinRes, rewardRes, taskRes] = await Promise.all([
            fetch('/api/admin/analytics/overview?period=30d'),
            fetch('/api/admin/analytics/coin-economy?period=30d'),
            fetch('/api/admin/analytics/reward-shop?period=30d'),
            fetch('/api/admin/analytics/task-economy?period=30d'),
        ]);
        if (overviewRes.ok) {
            overview = await overviewRes.json();
        }
        if (coinRes.ok) {
            coinEconomy = await coinRes.json();
        }
        if (rewardRes.ok) {
            rewardShop = await rewardRes.json();
        }
        if (taskRes.ok) {
            taskEconomy = await taskRes.json();
        }
    } catch (e) {
        console.error('Failed to fetch admin analytics:', e);
    }

    return {
        overview,
        coinEconomy,
        rewardShop,
        taskEconomy,
    };
};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
