import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    if (locals.session.authenticated) {
        throw redirect(302, localizePath('/telegram', locals.locale));
    }

    return {
        session: locals.session,
    };
};
