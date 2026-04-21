import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, parent }) => {
    if (!locals.session.authenticated) {
        throw redirect(302, '/login.html');
    }

    if (locals.session.role === 'super_admin') {
        throw redirect(302, '/super-admin');
    }

    const parentData = await parent();

    return {
        session: parentData.session,
        appConfig: parentData.appConfig,
    };
};