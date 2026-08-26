import type { PageServerLoad } from './$types';

// EXPLAIN: This public route deliberately does not inspect authentication.
export const load: PageServerLoad = async ({ locals }) => {
    return {
        publicOrigin: locals.appConfig.publicOrigin,
    };
};
