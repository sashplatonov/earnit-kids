import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import { LAST_APP_SECTION_COOKIE, resolvePreferredAppSection, toAppPath } from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
    if (locals.session.authenticated) {
        const preferredSection = resolvePreferredAppSection(
            locals.session.role,
            cookies.get(LAST_APP_SECTION_COOKIE),
        );

        if (locals.session.role === 'super_admin' && cookies.get(LAST_APP_SECTION_COOKIE) == null) {
            throw redirect(302, localizePath('/super-admin', locals.locale));
        }

        throw redirect(302, toAppPath(preferredSection, locals.locale));
    }

    return {
        session: locals.session,
        appConfig: locals.appConfig,
    };
};
