import { error, redirect } from '@sveltejs/kit';
import {
    getAppSectionTitle,
    getDefaultAppSection,
    isAppSection,
    isSectionAllowed,
    toAppPath,
} from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, params }) => {
    if (!isAppSection(params.section)) {
        throw error(404, `Route not found: /app/${params.section}`);
    }

    if (!isSectionAllowed(params.section, locals.session.role)) {
        throw redirect(302, toAppPath(getDefaultAppSection(locals.session.role)));
    }

    return {
        section: params.section,
        metaTitle: getAppSectionTitle(params.section),
        canonicalPath: toAppPath(params.section),
    };
};