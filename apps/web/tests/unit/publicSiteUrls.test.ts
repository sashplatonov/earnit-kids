import { describe, expect, it } from 'vitest';
import { canonicalPublicPath, normalizePublicRequest, publicDocumentPath, publicLanguageHref } from '../../scripts/public-site/urls.js';

describe('public-site URL contract', () => {
    it('maps all six pages to English and Russian canonical artifacts', () => {
        expect(publicDocumentPath(new URL('https://example.test/'))).toBe('/public/index.html');
        expect(publicDocumentPath(new URL('https://example.test/ru/'))).toBe('/public/ru/index.html');
        expect(publicDocumentPath('/ru/faq.html')).toBe('/public/ru/faq.html');
        expect(canonicalPublicPath('/tasks.html', 'ru')).toBe('/ru/tasks.html');
    });

    it('redirects valid legacy locale queries and preserves unrelated pairs', () => {
        const result = normalizePublicRequest('https://example.test/how.html?lang=ru&utm_source=mail');
        expect(result.redirect).toBe('https://example.test/ru/how.html?utm_source=mail');
        expect(normalizePublicRequest('https://example.test/ru/how.html?lang=en').redirect)
            .toBe('https://example.test/how.html');
    });

    it('leaves unsupported, protected, and Telegram handoff requests untouched', () => {
        for (const input of [
            'https://example.test/how.html?lang=de',
            'https://example.test/api/login-google/start?lang=ru',
            'https://example.test/app?lang=ru',
            'https://example.test/workspace?lang=ru',
            'https://example.test/?tgWebAppStartParam=home&lang=ru',
        ]) {
            expect(normalizePublicRequest(input).redirect).toBeNull();
        }
    });

    it('builds same-origin language links without touching external destinations', () => {
        expect(publicLanguageHref('/how.html', 'ru', 'https://example.test')).toBe('https://example.test/ru/how.html');
        expect(publicLanguageHref('/api/login-google/start', 'ru', 'https://example.test')).toBeNull();
    });
});
