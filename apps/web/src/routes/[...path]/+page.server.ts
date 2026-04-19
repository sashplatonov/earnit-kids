import { error, redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';

const LEGACY_REDIRECTS: Record<string, string> = {
    'about.html': '/about',
    'faq.html': '/faq',
    features: '/features/tasks',
    'index.html': '/',
    'reset-password.html': '/reset-password',
    'super-admin.html': '/super-admin',
    'verify.html': '/verify',
};

export const load: PageServerLoad = async ({ params, url }) => {
    const normalizedPath = params.path.replace(/\/+$/, '');
    const redirectTarget = LEGACY_REDIRECTS[normalizedPath];

    if (redirectTarget) {
        throw redirect(302, `${redirectTarget}${url.search}`);
    }

    throw error(404, `Route not found: /${params.path}`);
};
