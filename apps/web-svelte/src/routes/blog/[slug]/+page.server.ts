import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { loadPost } from '$lib/server/blog';

export const load: PageServerLoad = async ({ params }) => {
    const post = await loadPost(params.slug);
    if (!post) throw error(404, `Article not found: ${params.slug}`);
    return { post };
};
