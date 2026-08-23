import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import { resolveContinuePath } from './continue';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
    const continueTo = resolveContinuePath(url.searchParams.get('continue'), locals.locale);

    if (locals.session.authenticated) {
        throw redirect(302, continueTo ?? localizePath('/telegram', locals.locale));
    }

    return {
        session: locals.session,
        continueTo,
    };
};
