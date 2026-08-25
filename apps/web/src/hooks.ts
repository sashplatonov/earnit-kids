import type { Reroute } from '@sveltejs/kit';
import { splitLocaleFromPath } from '$lib/i18n';

export const reroute: Reroute = ({ url }) => {
    const { locale, pathname } = splitLocaleFromPath(url.pathname);

    // Keep the localized home route on the dynamic `[locale]` page. Mapping
    // `/en` or `/ru` to `/` would invoke the root loader, which redirects back
    // to the same localized URL forever.
    if (locale && pathname === '/') {
        return undefined;
    }

    return locale ? pathname : undefined;
};
