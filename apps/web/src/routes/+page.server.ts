import { redirect } from '@sveltejs/kit';
import { DEFAULT_LOCALE, localizePath, normalizeLocale } from '$lib/i18n';
import type { PageServerLoad } from './$types';

// EXPLAIN: The edge serves the static marketing home at the canonical root,
// except Telegram launch parameters which must reach the Mini App before
// browser JS runs.
// EXPLAIN: An authenticated Telegram session must not change this public URL;
// EXPLAIN: the Mini App links back to the root specifically to leave Telegram.
export const load: PageServerLoad = async ({ url, locals }) => {
    if (url.searchParams.has('tgWebAppStartParam')) {
        // EXPLAIN: Honor the visitor's negotiated locale (cookie or
        // Accept-Language) instead of forcing Russian on every Telegram launch.
        const locale = normalizeLocale(locals.locale) ?? DEFAULT_LOCALE;
        throw redirect(302, `${localizePath('/telegram', locale)}${url.search}`);
    }

    return {};
};
