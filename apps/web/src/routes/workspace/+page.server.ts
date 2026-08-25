import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { localizePath } from '$lib/i18n';

export const load: PageServerLoad = async ({ locals, url }) => {
    if (!locals.session.authenticated) {
        const continuation = encodeURIComponent(`${localizePath(url.pathname, locals.locale)}${url.search}`);
        throw redirect(302, `/public/index.html?continue=${continuation}`);
    }

    return {
        role: locals.session.role ?? '',
        publicOrigin: locals.appConfig.publicOrigin,
    };
};
