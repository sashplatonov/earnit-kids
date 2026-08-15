import type { PageServerLoad } from './$types';

// EXPLAIN: Expose the public marketing site origin to the Mini App so the
// EXPLAIN: unobtrusive footer link can point at the static public site.
export const load: PageServerLoad = async ({ locals }) => {
    return {
        publicOrigin: locals.appConfig.publicOrigin,
    };
};
