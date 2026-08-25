import type { LayoutServerLoad } from './$types';
import { getI18nPayloadForPath } from '$lib/i18n';

export const load: LayoutServerLoad = async ({ locals, url }) => {
    return {
        appConfig: locals.appConfig,
        i18n: await getI18nPayloadForPath(url.pathname, locals.locale),
        locale: locals.locale,
        session: locals.session,
    };
};
