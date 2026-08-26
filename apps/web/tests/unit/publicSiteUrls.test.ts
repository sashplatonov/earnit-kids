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

    it('negotiates Russian browser preferences with ordered quality values', () => {
        expect(normalizePublicRequest('https://example.test/how.html', {
            acceptLanguage: 'fr-FR, ru-RU;q=0.9, en;q=0.8',
        })).toMatchObject({
            redirect: 'https://example.test/ru/how.html',
            vary: true,
        });
        expect(normalizePublicRequest('https://example.test/how.html', {
            acceptLanguage: 'en-US, ru;q=0.8',
        }).redirect).toBeNull();
    });

    it('prefers a saved locale over browser language negotiation', () => {
        expect(normalizePublicRequest('https://example.test/', {
            cookie: 'session=1; locale=ru',
            acceptLanguage: 'en-US, ru;q=0.8',
        })).toMatchObject({
            redirect: 'https://example.test/ru/',
            vary: true,
        });
        expect(normalizePublicRequest('https://example.test/', {
            acceptLanguage: 'de-DE',
        }).redirect).toBeNull();
    });

    it('keeps explicit valid locale queries ahead of browser preferences', () => {
        expect(normalizePublicRequest('https://example.test/how.html?lang=en&utm_source=mail', {
            acceptLanguage: 'ru-RU',
        })).toMatchObject({
            redirect: 'https://example.test/how.html?utm_source=mail',
            vary: false,
        });
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

        expect(normalizePublicRequest('https://example.test/ru/how.html', { acceptLanguage: 'ru-RU' }).redirect).toBeNull();
    });

    it('builds same-origin language links without touching external destinations', () => {
        expect(publicLanguageHref('/how.html', 'ru', 'https://example.test')).toBe('https://example.test/ru/how.html');
        expect(publicLanguageHref('/api/login-google/start', 'ru', 'https://example.test')).toBeNull();
    });

    it('keeps the live demo outside the static public URL contract', () => {
        expect(publicDocumentPath('/demo')).toBeNull();
        expect(publicDocumentPath('/ru/demo')).toBeNull();
        expect(canonicalPublicPath('/demo', 'ru')).toBeNull();
    });
});
