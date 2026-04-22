import type { PageServerLoad } from './$types';
import { listPosts } from '$lib/server/blog';

export const load: PageServerLoad = async ({ locals }) => {
    const posts = await listPosts(locals.locale);
    return { posts };
};
