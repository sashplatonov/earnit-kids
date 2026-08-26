import { describe, expect, it, vi } from 'vitest';
import { GET } from '../../src/routes/robots.txt/+server';

async function robotsText(deploymentEnv: string, appUrl = 'https://example.test'): Promise<string> {
    vi.stubEnv('APP_URL', appUrl);
    vi.stubEnv('DEPLOYMENT_ENV', deploymentEnv);
    vi.stubEnv('PRODUCTION', 'false');
    return await (await GET({} as Parameters<typeof GET>[0])).text();
}

describe('robots.txt', () => {
    it('allows production crawling and points to one absolute sitemap', async () => {
        const body = await robotsText('production', 'https://example.test///');

        expect(body).toBe([
            'User-agent: *',
            'Allow: /',
            '',
            'Sitemap: https://example.test/sitemap.xml',
            '',
        ].join('\n'));
    });

    it.each(['development', 'preview', 'staging', ''])('blocks indexing outside production (%s)', async (deploymentEnv) => {
        const body = await robotsText(deploymentEnv);

        expect(body).toBe(['User-agent: *', 'Disallow: /', '',].join('\n'));
        expect(body).not.toContain('Sitemap:');
    });

    it('does not let the legacy PRODUCTION flag disagree with deployment policy', async () => {
        const body = await robotsText('staging');

        expect(body).toContain('Disallow: /');
        expect(body).not.toContain('Allow: /');
    });
});
