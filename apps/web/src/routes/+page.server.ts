import { redirect } from '@sveltejs/kit';
import { getDefaultAppSection, toAppPath } from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    if (locals.session.role === 'super_admin') {
        throw redirect(302, '/super-admin');
    }

    if (locals.session.authenticated) {
        throw redirect(302, toAppPath(getDefaultAppSection(locals.session.role)));
    }

    return {
        session: locals.session,
        appConfig: locals.appConfig,
    };
};
