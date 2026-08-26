import { loadAppConfig } from '$lib/server/config';
import { DEFAULT_PUBLIC_LOCALE, PUBLIC_LOCALES, PUBLIC_PAGES, publicLanguageHref } from '../../../scripts/public-site/urls.js';
import type { RequestHandler } from './$types';

const PAGE_METADATA: Record<string, { priority: string; changefreq: string }> = {
    index: { priority: '1.0', changefreq: 'weekly' },
    how: { priority: '0.8', changefreq: 'monthly' },
    tasks: { priority: '0.8', changefreq: 'monthly' },
    rewards: { priority: '0.8', changefreq: 'monthly' },
    parents: { priority: '0.8', changefreq: 'monthly' },
    faq: { priority: '0.6', changefreq: 'monthly' },
} as const;

function escapeXml(value: string): string {
    return value.replace(/[<>&'"]/g, (character) => ({
        '<': '&lt;',
        '>': '&gt;',
        '&': '&amp;',
        "'": '&apos;',
        '"': '&quot;',
    })[character] ?? character);
}

export const GET: RequestHandler = async () => {
    const { publicOrigin } = loadAppConfig();
    const base = publicOrigin.replace(/\/+$/, '');
    const today = new Date().toISOString().slice(0, 10);

    const urls = PUBLIC_PAGES.flatMap((page) => PUBLIC_LOCALES.map((locale) => {
        const metadata = PAGE_METADATA[page.key];
        const loc = publicLanguageHref(page.englishPath, locale, base);
        const alternates = [
            ...PUBLIC_LOCALES.map((alternateLocale) => [alternateLocale, publicLanguageHref(page.englishPath, alternateLocale, base)] as const),
            ['x-default', publicLanguageHref(page.englishPath, DEFAULT_PUBLIC_LOCALE, base)] as const,
        ].map(([alternateLocale, href]) => `    <xhtml:link rel="alternate" hreflang="${alternateLocale}" href="${escapeXml(href ?? '')}" />`);

        return [
            '  <url>', `    <loc>${escapeXml(loc ?? '')}</loc>`,
            ...alternates,
            `    <lastmod>${today}</lastmod>`, `    <changefreq>${metadata.changefreq}</changefreq>`,
            `    <priority>${metadata.priority}</priority>`, '  </url>',
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
