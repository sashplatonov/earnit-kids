import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    if (locals.session.role !== 'super_admin') {
        throw redirect(302, localizePath('/login', locals.locale));
    }

    return {
        session: locals.session,
        appConfig: locals.appConfig,
    };
};
