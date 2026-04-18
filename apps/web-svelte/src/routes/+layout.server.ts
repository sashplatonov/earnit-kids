import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
    return {
        appConfig: locals.appConfig,
        session: locals.session,
    };
};
