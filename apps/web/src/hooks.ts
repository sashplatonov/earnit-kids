import type { Reroute } from '@sveltejs/kit';
import { splitLocaleFromPath } from '$lib/i18n';

export const reroute: Reroute = ({ url }) => {
    const { locale, pathname } = splitLocaleFromPath(url.pathname);
    return locale ? pathname : undefined;
};
