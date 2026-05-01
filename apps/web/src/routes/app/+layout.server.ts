import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, parent }) => {
    if (!locals.session.authenticated) {
        throw redirect(302, localizePath('/login', locals.locale));
    }

    const parentData = await parent();

    return {
        session: parentData.session,
        appConfig: parentData.appConfig,
    };
};
