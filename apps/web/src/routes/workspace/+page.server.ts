import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { localizePath, splitLocaleFromPath } from '$lib/i18n';

export const load: PageServerLoad = ({ url }) => {
    const { locale } = splitLocaleFromPath(url.pathname);
    const destination = locale ? localizePath('/app', locale) : '/app';

    throw redirect(308, `${destination}${url.search}`);
};
