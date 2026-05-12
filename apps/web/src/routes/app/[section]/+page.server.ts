import { error, redirect } from '@sveltejs/kit';
import { buildI18nPayload, translateKey } from '$lib/i18n';
import {
    getAppSectionTitleKey,
    isAppSection,
    isSectionAllowed,
    LAST_APP_SECTION_COOKIE,
    resolvePreferredAppSection,
    toAppPath,
} from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, params, cookies }) => {
    if (!isAppSection(params.section)) {
        throw error(404, `Route not found: /app/${params.section}`);
    }

    if (!isSectionAllowed(params.section, locals.session.role)) {
        throw redirect(
            302,
            toAppPath(resolvePreferredAppSection(locals.session.role, cookies.get(LAST_APP_SECTION_COOKIE)), locals.locale),
        );
    }

    const i18n = buildI18nPayload(locals.locale, ['app']);

    return {
        section: params.section,
        metaTitle: translateKey(i18n, getAppSectionTitleKey(params.section)),
        canonicalPath: toAppPath(params.section, locals.locale),
    };
};
