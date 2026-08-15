import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import { LAST_APP_SECTION_COOKIE, resolvePreferredAppSection, toAppPath } from '$lib/app/routes';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
    if (locals.session.authenticated) {
        const preferredSection = resolvePreferredAppSection(
            locals.session.role,
            cookies.get(LAST_APP_SECTION_COOKIE),
        );

        // EXPLAIN: Public routes are bare-URL (no locale prefix), but the
        // EXPLAIN: authenticated app and super-admin surfaces still use the
        // EXPLAIN: locale-prefixed routing. `locals.locale` is `ru` for the
        // EXPLAIN: public route group, so redirects land on the RU app shell.
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

export const actions: Actions = {
    default: async ({ locals, cookies }) => {
        if (locals.session.authenticated) {
            const preferredSection = resolvePreferredAppSection(
                locals.session.role,
                cookies.get(LAST_APP_SECTION_COOKIE),
            );

            if (locals.session.role === 'super_admin' && cookies.get(LAST_APP_SECTION_COOKIE) == null) {
                throw redirect(303, localizePath('/super-admin', locals.locale));
            }

            throw redirect(303, toAppPath(preferredSection, locals.locale));
        }

        throw redirect(303, '/');
    },
};