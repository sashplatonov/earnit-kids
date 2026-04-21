import { error, redirect } from '@sveltejs/kit';
import { localizePath, resolveLegacyAlias, splitLocaleFromPath, stripLocaleFromPath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, params, url }) => {
    const normalizedPath = `/${params.path.replace(/^\/+/, '').replace(/\/+$/, '')}`;
    const redirectTarget = resolveLegacyAlias(stripLocaleFromPath(normalizedPath));

    if (redirectTarget) {
        const locale = splitLocaleFromPath(url.pathname).locale ?? locals.locale;
        throw redirect(302, `${localizePath(redirectTarget, locale)}${url.search}`);
    }

    throw error(404, `Route not found: /${params.path}`);
};
