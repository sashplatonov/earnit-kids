import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

// EXPLAIN: The edge serves the static marketing home at the canonical root,
// except Telegram launch parameters which must reach the Mini App before
// browser JS runs.
// EXPLAIN: An authenticated Telegram session must not change this public URL;
// EXPLAIN: the Mini App links back to the root specifically to leave Telegram.
export const load: PageServerLoad = async ({ url }) => {
    if (url.searchParams.has('tgWebAppStartParam')) {
        throw redirect(302, `${localizePath('/telegram', 'ru')}${url.search}`);
    }

    return {};
};
