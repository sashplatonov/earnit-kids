import { describe, expect, it } from 'vitest';
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

    it('adds language only to same-origin public links', () => {
        expect(withLanguage('/how.html?x=1', 'ru')).toBe('/how.html?x=1&lang=ru');
        expect(withLanguage('https://telegram.me/example', 'ru')).toBe('https://telegram.me/example');
        expect(withLanguage('/api/login-google/start?continue=%2Fworkspace', 'ru')).toContain('lang=ru');
    });
});
