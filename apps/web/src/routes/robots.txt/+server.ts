import { loadAppConfig } from '$lib/server/config';
import type { RequestHandler } from './$types';

// EXPLAIN: Production serves an allow-all robots + sitemap pointer. Non
// EXPLAIN: production environments (staging/preview/dev) block indexing so
// EXPLAIN: they never compete with production in search results.
function isProduction(): boolean {
    const value = process.env.PRODUCTION ?? '';
    return value === 'true' || value === '1';
}

export const GET: RequestHandler = async () => {
    const { publicOrigin } = loadAppConfig();
    const production = isProduction();

    let body: string;
    if (production) {
        body = [
            'User-agent: *',
            'Allow: /',
            '',
            `Sitemap: ${publicOrigin.replace(/\/+$/, '')}/sitemap.xml`,
            '',
        ].join('\n');
    } else {
        body = [
            'User-agent: *',
            'Disallow: /',
            '',
        ].join('\n');
    }

    return new Response(body, {
        headers: {
            'Content-Type': 'text/plain; charset=utf-8',
            'Cache-Control': 'public, max-age=3600',
        },
    });
};
