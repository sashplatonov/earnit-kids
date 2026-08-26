import { loadAppConfig } from '$lib/server/config';
import type { RequestHandler } from './$types';

const PUBLIC_PAGES = [
    { path: '/', priority: '1.0', changefreq: 'weekly' },
    { path: '/how.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/tasks.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/rewards.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/parents.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/faq.html', priority: '0.6', changefreq: 'monthly' },
] as const;

export const GET: RequestHandler = async () => {
    const { publicOrigin } = loadAppConfig();
    const base = publicOrigin.replace(/\/+$/, '');
    const today = new Date().toISOString().slice(0, 10);

    const urls = PUBLIC_PAGES.map((page) => {
        const loc = `${base}${page.path}`;
        return [
            '  <url>', `    <loc>${loc}</loc>`,
            `    <lastmod>${today}</lastmod>`, `    <changefreq>${page.changefreq}</changefreq>`,
            `    <priority>${page.priority}</priority>`, '  </url>',
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
