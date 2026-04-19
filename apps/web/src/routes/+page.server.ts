import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    if (locals.session.role === 'super_admin') {
        throw redirect(302, '/super-admin');
    }

    return {
        session: locals.session,
        appConfig: locals.appConfig,
    };
};
