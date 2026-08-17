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
    try {
        const response = await fetch('/api/admin/analytics/overview?period=30d');
        if (response.ok) {
            overview = await response.json();
        }
    } catch (e) {
        console.error('Failed to fetch admin analytics:', e);
    }

    return {
        overview,
    };
};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
