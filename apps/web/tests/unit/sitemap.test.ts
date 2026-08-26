import { describe, expect, it, vi } from 'vitest';
import { GET } from '../../src/routes/sitemap.xml/+server';

const pages = ['/', '/how.html', '/tasks.html', '/rewards.html', '/parents.html', '/faq.html', '/demo.html'];

async function sitemapXml(env: NodeJS.ProcessEnv): Promise<string> {
    vi.stubEnv('APP_URL', env.APP_URL ?? 'https://example.test');
    vi.stubEnv('DEPLOYMENT_ENV', env.DEPLOYMENT_ENV ?? 'production');
    return await (await GET({} as Parameters<typeof GET>[0])).text();
}

describe('sitemap.xml', () => {
    it('publishes fourteen canonical localized URLs with reciprocal alternates', async () => {
        const xml = await sitemapXml({ APP_URL: 'https://example.test///' });

        expect(xml).toContain('xmlns:xhtml="http://www.w3.org/1999/xhtml"');
        expect(xml.match(/<url>/g)).toHaveLength(14);
        expect(xml.match(/<loc>/g)).toHaveLength(14);
        expect(xml).not.toMatch(/\/public\/|\?lang=|\/app|\/workspace/);

        for (const page of pages) {
            const russianPage = page === '/' ? '/ru/' : `/ru${page}`;
            const englishUrl = page === '/' ? 'https://example.test/' : `https://example.test${page}`;
            const russianUrl = `https://example.test${russianPage}`;
            const block = xml.match(new RegExp(`<url>[\\s\\S]*?<loc>${page === '/' ? 'https://example\\.test/' : `https://example\\.test${page}`}<\\/loc>[\\s\\S]*?<\\/url>`))?.[0];
            const russianBlock = xml.match(new RegExp(`<url>[\\s\\S]*?<loc>https://example\\.test${russianPage.replace('/', '\\/')}<\\/loc>[\\s\\S]*?<\\/url>`))?.[0];

            expect(block).toBeDefined();
            expect(russianBlock).toBeDefined();
            for (const alternate of [englishUrl, russianUrl]) {
                expect(block).toContain(`href="${alternate}"`);
                expect(russianBlock).toContain(`href="${alternate}"`);
            }
            expect(block).toContain('hreflang="x-default"');
            expect(russianBlock).toContain('hreflang="x-default"');
        }
    });

    it('uses the configured origin without carrying an APP_URL path or query', async () => {
        const xml = await sitemapXml({ APP_URL: 'https://example.test/en/app/tasks?tab=1///' });

        expect(xml).toContain('<loc>https://example.test/</loc>');
        expect(xml).toContain('<loc>https://example.test/ru/</loc>');
        expect(xml).not.toContain('/en/app/tasks');
        expect(xml).not.toContain('?tab=1');
    });
});
