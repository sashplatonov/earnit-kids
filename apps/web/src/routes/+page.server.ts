import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import { LAST_APP_SECTION_COOKIE, resolvePreferredAppSection, toAppPath } from '$lib/app/routes';
import type { PageServerLoad } from './$types';

// EXPLAIN: The public marketing site is a static HTML site served from
// EXPLAIN: static/public/. The root URL redirects there. Authenticated
// EXPLAIN: users still go to the app shell (or super-admin).
export const load: PageServerLoad = async ({ locals, cookies }) => {
    if (locals.session.authenticated) {
        const savedSection = cookies.get(LAST_APP_SECTION_COOKIE);

        // EXPLAIN: Super admins without a saved app section land on /super-admin.
        if (locals.session.role === 'super_admin' && savedSection == null) {
            throw redirect(302, localizePath('/super-admin', locals.locale));
        }

        const preferredSection = resolvePreferredAppSection(locals.session.role, savedSection);
        throw redirect(302, toAppPath(preferredSection, locals.locale));
    }

    throw redirect(302, '/public/index.html');
};