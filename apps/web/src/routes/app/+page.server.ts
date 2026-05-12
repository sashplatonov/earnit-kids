import { redirect } from '@sveltejs/kit';
import { LAST_APP_SECTION_COOKIE, resolvePreferredAppSection, toAppPath } from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
    throw redirect(
        302,
        toAppPath(resolvePreferredAppSection(locals.session.role, cookies.get(LAST_APP_SECTION_COOKIE)), locals.locale),
    );
};
