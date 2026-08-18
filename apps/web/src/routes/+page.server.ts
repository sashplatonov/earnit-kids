import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

// EXPLAIN: The public marketing site is a static HTML site served from
// EXPLAIN: static/public/. The root URL redirects there. Authenticated
// EXPLAIN: users still go to the Mini App.
export const load: PageServerLoad = async ({ locals }) => {
    if (locals.session.authenticated) {
        throw redirect(302, localizePath('/telegram', locals.locale));
    }

    throw redirect(302, '/public/index.html');
};