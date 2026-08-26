import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
    DEFAULT_LOCALE,
    LOCALES,
    detectLocale,
    getMessage,
    messages,
    normalizeLocale,
    resolveLocale,
    withLanguage,
} from '../../scripts/public-site/i18n.js';

describe('static public-site i18n', () => {
    it('normalizes supported language variants and rejects unsupported values', () => {
        expect(normalizeLocale('RU-ru')).toBe('ru');
        expect(normalizeLocale('en-GB')).toBe('en');
        expect(normalizeLocale('de')).toBeNull();
    });

    it('prefers an explicit valid query over browser preferences', () => {
        expect(resolveLocale('?lang=en', { languages: ['ru-RU'], language: 'ru-RU' })).toBe('en');
        expect(resolveLocale('?lang=unsupported', { languages: ['ru-RU'], language: 'en-US' })).toBe('ru');
    });

    it('uses ordered browser preferences and falls back to English', () => {
        expect(detectLocale({ languages: ['de', 'ru-RU'], language: 'en-US' })).toBe('ru');
        expect(detectLocale({ languages: ['de'], language: 'de-DE' })).toBe(DEFAULT_LOCALE);
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
                expect(html.match(/<link rel="canonical"/g)).toHaveLength(1);
                expect(html.match(/hreflang="(en|ru|x-default)"/g)).toHaveLength(3);
                expect(html).toContain(locale === 'ru' ? 'Награды' : 'Rewards');
            }
        }
    });

    it('adds language only to same-origin public links', () => {
        expect(withLanguage('/how.html?x=1', 'ru')).toBe('/ru/how.html?x=1');
        expect(withLanguage('https://telegram.me/example', 'ru')).toBe('https://telegram.me/example');
        expect(withLanguage('/api/login-google/start?continue=%2Fworkspace', 'ru')).toBe('/api/login-google/start?continue=%2Fworkspace');
    });
});
