import type { PageServerLoad } from './$types';
import { listPosts } from '$lib/server/blog';

export const load: PageServerLoad = async () => {
    const posts = await listPosts();
    return { posts };
};
