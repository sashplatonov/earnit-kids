import { loadAppConfig } from '$lib/server/config';
import type { RequestHandler } from './$types';

// EXPLAIN: Public site canonical URLs (Russian-only first stage). These are
// EXPLAIN: the only real production pages — no `/ru` prefix, no EN hreflang.
const PUBLIC_PAGES = [
    { path: '/', priority: '1.0', changefreq: 'weekly' },
    { path: '/how', priority: '0.8', changefreq: 'monthly' },
    { path: '/tasks', priority: '0.8', changefreq: 'monthly' },
    { path: '/rewards', priority: '0.8', changefreq: 'monthly' },
    { path: '/parents', priority: '0.8', changefreq: 'monthly' },
    { path: '/faq', priority: '0.6', changefreq: 'monthly' },
] as const;

export const GET: RequestHandler = async () => {
    const { publicOrigin } = loadAppConfig();
    const base = publicOrigin.replace(/\/+$/, '');
    const today = new Date().toISOString().slice(0, 10);

    const urls = PUBLIC_PAGES.map((page) => {
        const loc = page.path === '/' ? `${base}/` : `${base}${page.path}`;
        return [
            '  <url>',
            `    <loc>${loc}</loc>`,
            `    <lastmod>${today}</lastmod>`,
            `    <changefreq>${page.changefreq}</changefreq>`,
            `    <priority>${page.priority}</priority>`,
            '  </url>',
        ].join('\n');
    }).join('\n');

    const xml = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
        urls,
        '</urlset>',
        '',
    ].join('\n');

    return new Response(xml, {
        headers: {
            'Content-Type': 'application/xml; charset=utf-8',
            'Cache-Control': 'public, max-age=3600',
        },
    });
};
