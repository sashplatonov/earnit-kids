import { loadAppConfig } from '$lib/server/config';
import type { RequestHandler } from './$types';

const PUBLIC_PAGES = [
    { path: '/public/index.html', priority: '1.0', changefreq: 'weekly' },
    { path: '/public/how.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/public/tasks.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/public/rewards.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/public/parents.html', priority: '0.8', changefreq: 'monthly' },
    { path: '/public/faq.html', priority: '0.6', changefreq: 'monthly' },
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
