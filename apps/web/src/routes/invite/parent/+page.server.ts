import type { PageServerLoad } from './$types';

export const load: PageServerLoad = ({ locals }) => ({
    authenticated: locals.session.authenticated,
});
