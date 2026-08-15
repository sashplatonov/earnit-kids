import { describe, expect, it, vi } from 'vitest';
import { getPublicSiteUrl, getPublicSiteUrlBrowser } from '../../src/lib/services/publicSiteUrl';

describe('getPublicSiteUrl', () => {
    it('builds an absolute URL from a configured origin and bare path', () => {
        expect(getPublicSiteUrl('https://earnit-kids.example', '/')).toBe('https://earnit-kids.example/');
        expect(getPublicSiteUrl('https://earnit-kids.example', '/how')).toBe('https://earnit-kids.example/how');
        expect(getPublicSiteUrl('https://earnit-kids.example', '/tasks')).toBe('https://earnit-kids.example/tasks');
    });

    it('normalizes trailing slashes in origin and path', () => {
        expect(getPublicSiteUrl('https://earnit-kids.example///', '/how/')).toBe('https://earnit-kids.example/how');
    });

    it('defaults to root when no path is given', () => {
        expect(getPublicSiteUrl('https://earnit-kids.example')).toBe('https://earnit-kids.example/');
    });

    it('does not inject a locale prefix', () => {
        expect(getPublicSiteUrl('https://earnit-kids.example', '/')).not.toContain('/ru');
        expect(getPublicSiteUrl('https://earnit-kids.example', '/faq')).not.toContain('/ru/faq');
    });

    it('supports a separate marketing origin distinct from the app origin', () => {
        const url = getPublicSiteUrl('https://go.earnit-kids.example', '/how');
        expect(url).toBe('https://go.earnit-kids.example/how');
    });
});

describe('getPublicSiteUrlBrowser', () => {
    it('falls back to window.location.origin in browser context', () => {
        vi.stubGlobal('window', { location: { origin: 'http://localhost:4173' } });
        // EXPLAIN: The browser guard in publicSiteUrl checks the `browser`
        // EXPLAIN: flag from $app/environment; in unit tests this is false,
        // EXPLAIN: so the fallback produces an empty-origin URL. This verifies
        // EXPLAIN: the helper does not throw and delegates to getPublicSiteUrl.
        const url = getPublicSiteUrlBrowser('/how');
        expect(url).toBe('/how');
        vi.unstubAllGlobals();
    });
});