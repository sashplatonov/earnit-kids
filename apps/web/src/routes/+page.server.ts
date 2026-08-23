import { redirect } from '@sveltejs/kit';
import { localizePath } from '$lib/i18n';
import type { PageServerLoad } from './$types';

// EXPLAIN: The public marketing site is a static HTML site served from
// EXPLAIN: static/public/. The root URL redirects there, except Telegram
// EXPLAIN: launch parameters which must reach the Mini App before browser JS runs.
// EXPLAIN: An authenticated Telegram session must not change this public URL;
// EXPLAIN: the Mini App links back to the root specifically to leave Telegram.
export const load: PageServerLoad = async ({ url }) => {
    if (url.searchParams.has('tgWebAppStartParam')) {
        throw redirect(302, `${localizePath('/telegram', 'ru')}${url.search}`);
    }

    throw redirect(302, '/public/index.html');
};
