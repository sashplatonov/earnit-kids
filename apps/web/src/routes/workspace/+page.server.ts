import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
    if (!locals.session.authenticated) {
        const continuation = encodeURIComponent(`${url.pathname}${url.search}`);
        throw redirect(302, `${localizePath('/login', locals.locale)}?continue=${continuation}`);
    }

    return {
        role: locals.session.role ?? '',
        publicOrigin: locals.appConfig.publicOrigin,
    };
};
