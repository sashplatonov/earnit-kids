import { loadAppConfig } from '$lib/server/config';
import type { RequestHandler } from './$types';

const PUBLIC_PAGES = [
    { path: '/', priority: '1.0', changefreq: 'weekly' },
    { path: '/about', priority: '0.8', changefreq: 'monthly' },
    { path: '/features/tasks', priority: '0.8', changefreq: 'monthly' },
    { path: '/features/shop', priority: '0.8', changefreq: 'monthly' },
    { path: '/faq', priority: '0.6', changefreq: 'monthly' },
] as const;

export const GET: RequestHandler = async () => {
    const { publicOrigin } = loadAppConfig();
    const base = publicOrigin.replace(/\/+$/, '');
    const today = new Date().toISOString().slice(0, 10);

    const urls = PUBLIC_PAGES.flatMap((page) => ['en', 'ru'].map((locale) => {
        const loc = `${base}/${locale}${page.path === '/' ? '/' : page.path}`;
        const alternates = ['en', 'ru'].map((alternate) =>
            `    <xhtml:link rel="alternate" hreflang="${alternate}" href="${base}/${alternate}${page.path === '/' ? '/' : page.path}" />`);
        alternates.push(`    <xhtml:link rel="alternate" hreflang="x-default" href="${base}/en${page.path === '/' ? '/' : page.path}" />`);
        return [
            '  <url>', `    <loc>${loc}</loc>`, ...alternates,
            `    <lastmod>${today}</lastmod>`, `    <changefreq>${page.changefreq}</changefreq>`,
            `    <priority>${page.priority}</priority>`, '  </url>',
        ].join('\n');
    })).join('\n');

    const xml = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
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
