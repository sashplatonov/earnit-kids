import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
    DEFAULT_LOCALE,
    LOCALES,
    getMessage,
    messages,
    normalizeLocale,
    resolveDocumentLocale,
    withLanguage,
} from '../../scripts/public-site/i18n.js';
import { resolvePublicOrigin } from '../../scripts/public-site/urls.js';

describe('static public-site i18n', () => {
    it('normalizes supported language variants and rejects unsupported values', () => {
        expect(normalizeLocale('RU-ru')).toBe('ru');
        expect(normalizeLocale('en-GB')).toBe('en');
        expect(normalizeLocale('de')).toBeNull();
    });

    it('uses only the served document language and falls back to English', () => {
        expect(resolveDocumentLocale({ documentElement: { lang: 'ru' } })).toBe('ru');
        expect(resolveDocumentLocale({ documentElement: { lang: 'en' } })).toBe('en');
        expect(resolveDocumentLocale({ documentElement: { lang: 'de' } })).toBe(DEFAULT_LOCALE);
    });

    it('keeps equal dictionary keys and safely falls back for missing messages', () => {
        expect(Object.keys(messages.en).sort()).toEqual(Object.keys(messages.ru).sort());
        expect(getMessage('ru', 'missing-key')).toBe('missing-key');
        expect(LOCALES).toEqual(['en', 'ru']);
    });

    it('keeps localized catalogs recursively in parity', () => {
        const shape = (value: unknown): unknown => {
            if (Array.isArray(value)) return value.map(shape);
            if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map((key) => [key, shape((value as Record<string, unknown>)[key])]));
            return typeof value;
        };
        expect(shape(messages.en)).toEqual(shape(messages.ru));
    });

    it('renders complete language metadata and representative body copy', () => {
        const root = resolve(process.cwd(), 'static/public');
        for (const locale of ['en', 'ru'] as const) {
            const directory = locale === 'ru' ? resolve(root, 'ru') : root;
            for (const file of ['index.html', 'how.html', 'tasks.html', 'rewards.html', 'parents.html', 'faq.html']) {
                const html = readFileSync(resolve(directory, file), 'utf8');
                expect(html).not.toContain('{{');
                expect(html).toContain(`<html lang="${locale}">`);
                expect(html.match(/data-language="(?:en|ru)"[^>]*aria-current="page"/g)).toHaveLength(1);
                expect(html).toContain(`data-language="${locale}" aria-current="page"`);
                expect(html.match(/<link rel="canonical"/g)).toHaveLength(1);
                expect(html.match(/hreflang="(en|ru|x-default)"/g)).toHaveLength(3);
                expect(html).toMatch(/<title>[^<]+ - EarnIt Kids<\/title>/);
                expect(html).toMatch(/<meta name="description" content="[^"]+">/);
                expect(html).toMatch(/<link rel="canonical" href="https:\/\/example\.test\/(?:ru\/)?[^"]*">/);
                expect(html.match(/<link rel="alternate"[^>]+href="https:\/\/example\.test\/[^"]*">/g)).toHaveLength(3);
                expect(html).not.toMatch(/<link rel="alternate"[^>]+href="\/public\//);
                expect(html).not.toContain('?lang=');
            }
        }
    });

    it('renders every public artifact from the shared shell', () => {
        const root = resolve(process.cwd(), 'static/public');
        const generatedMarker = '<!-- GENERATED FILE: edit scripts/public-site/template.html, not this artifact. -->';
        for (const locale of ['en', 'ru'] as const) {
            const directory = locale === 'ru' ? resolve(root, 'ru') : root;
            for (const file of ['index.html', 'how.html', 'tasks.html', 'rewards.html', 'parents.html', 'faq.html']) {
                const html = readFileSync(resolve(directory, file), 'utf8');
                expect(html).toContain(generatedMarker);
                expect(html.match(/data-public-shell="header"/g)).toHaveLength(1);
                expect(html.match(/data-public-shell="footer"/g)).toHaveLength(1);
            }
        }
    });

    it('normalizes the configured public origin and rejects invalid production input', () => {
        expect(resolvePublicOrigin('https://example.test///app?tab=1')).toBe('https://example.test');
        expect(resolvePublicOrigin(undefined)).toBe('http://localhost:4174');
        expect(() => resolvePublicOrigin(undefined, { production: true })).toThrow(/APP_URL is required/);
        expect(() => resolvePublicOrigin('not-an-url', { production: true })).toThrow(/valid HTTP/);
    });

    it('keeps the rewards shop target distinct', () => {
        const root = resolve(process.cwd(), 'static/public');
        for (const locale of ['en', 'ru'] as const) {
            const directory = locale === 'ru' ? resolve(root, 'ru') : root;
            for (const file of ['index.html', 'how.html', 'tasks.html', 'rewards.html', 'parents.html', 'faq.html']) {
                const html = readFileSync(resolve(directory, file), 'utf8');
                expect(html).toContain('/api/login-google/start?continue=%2Fapp');
            }
            const rewards = readFileSync(resolve(directory, 'rewards.html'), 'utf8');
            expect(rewards).toContain(locale === 'ru' ? 'href="/ru/app?context=rewards"' : 'href="/app?context=rewards"');
        }
    });

    it('adds language only to same-origin public links', () => {
        expect(withLanguage('/how.html?x=1', 'ru')).toBe('/ru/how.html?x=1');
        expect(withLanguage('https://telegram.me/example', 'ru')).toBe('https://telegram.me/example');
        expect(withLanguage('/api/login-google/start?continue=%2Fworkspace', 'ru')).toBe('/api/login-google/start?continue=%2Fworkspace');
    });
});
