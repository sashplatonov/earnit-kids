import { redirect } from '@sveltejs/kit';
import { getDefaultAppSection, toAppPath } from '$lib/app/routes';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    throw redirect(302, toAppPath(getDefaultAppSection(locals.session.role)));
};