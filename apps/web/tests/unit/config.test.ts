import { describe, expect, it } from 'vitest';
import { loadAppConfig } from '../../src/lib/server/config';
import { normalizeLocale, resolveDomainsForPath, shouldCanonicalizePath } from '../../src/lib/i18n/config';

describe('locale normalization', () => {
    it.each([
        ['en', 'en'],
        ['en-US', 'en'],
        ['ru', 'ru'],
        ['ru-RU', 'ru'],
    ])('normalizes supported tag %s', (input, expected) => {
        expect(normalizeLocale(input)).toBe(expected);
    });

    it.each(['english', 'russian', 'enough', 'ruble', 'xx-YY'])('rejects unsupported tag %s', (input) => {
        expect(normalizeLocale(input)).toBeNull();
    });
});

describe('locale path handling', () => {
    it('keeps bare app entry points stable for client bootstrap', () => {
        expect(shouldCanonicalizePath('/telegram')).toBe(false);
        expect(shouldCanonicalizePath('/workspace')).toBe(false);
        expect(shouldCanonicalizePath('/app')).toBe(false);
        expect(shouldCanonicalizePath('/ru/app')).toBe(false);
    });

    it('loads the browser application catalogs for both route segments', () => {
        expect(resolveDomainsForPath('/app')).toEqual(['common', 'app', 'tasks', 'admin', 'errors']);
        expect(resolveDomainsForPath('/ru/app')).toEqual(['common', 'app', 'tasks', 'admin', 'errors']);
        expect(resolveDomainsForPath('/workspace')).toEqual(['common', 'app', 'tasks', 'admin', 'errors']);
    });
});

describe('loadAppConfig', () => {
    it('returns the migration defaults', () => {
        const config = loadAppConfig({} as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('http://localhost:8080');
        expect(config.publicOrigin).toBe('http://localhost:4174');
        expect(config.telegramMiniAppUrl).toBeNull();
        expect(config.sessionPath).toBe('/api/page-data/session');
        expect(config.wsPath).toBe('/ws');
        expect(config.devPort).toBe(4173);
        expect(config.previewPort).toBe(4174);
    });

    it('normalizes explicit overrides', () => {
        const config = loadAppConfig({
            BACKEND_ORIGIN: 'https://api.example.test///',
            PUBLIC_BASE_URL: 'https://app.example.test///',
            SESSION_PATH: '/session/bootstrap',
            WS_PATH: '/realtime',
            DEV_PORT: '5000',
            PREVIEW_PORT: '5001',
        } as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('https://api.example.test');
        expect(config.publicOrigin).toBe('https://app.example.test');
        expect(config.sessionPath).toBe('/session/bootstrap');
        expect(config.wsPath).toBe('/realtime');
        expect(config.devPort).toBe(5000);
        expect(config.previewPort).toBe(5001);
    });

    it('accepts compose-compatible runtime config', () => {
        const config = loadAppConfig({
            BACKEND_URL: 'http://backend:8080///',
            APP_URL: 'http://localhost:3001///',
        } as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('http://backend:8080');
        expect(config.publicOrigin).toBe('http://localhost:3001');
    });

    it('prefers APP_URL over PUBLIC_BASE_URL for live public origin resolution', () => {
        const config = loadAppConfig({
            BACKEND_URL: 'http://backend:8080',
            PUBLIC_BASE_URL: 'http://localhost:3000',
            APP_URL: 'http://localhost:5001',
        } as NodeJS.ProcessEnv);

        expect(config.publicOrigin).toBe('http://localhost:5001');
    });

    it('normalizes publicOrigin to the site root, stripping path and query', () => {
        const config = loadAppConfig({
            APP_URL: 'https://earnit-kids.igo.mywire.org/en/app/tasks?tab=1',
        } as NodeJS.ProcessEnv);

        expect(config.publicOrigin).toBe('https://earnit-kids.igo.mywire.org');
    });

    it('keeps publicOrigin unchanged when it has no path beyond the root', () => {
        const config = loadAppConfig({
            APP_URL: 'https://earnit-kids.igo.mywire.org/',
        } as NodeJS.ProcessEnv);

        expect(config.publicOrigin).toBe('https://earnit-kids.igo.mywire.org');
    });

    it('reads the Telegram Mini App URL from env and trims trailing slashes', () => {
        const config = loadAppConfig({
            APP_URL: 'http://localhost:3000',
            PUBLIC_TELEGRAM_MINI_APP_URL: 'https://t.me/earnit_bot?startapp=home///',
        } as NodeJS.ProcessEnv);

        expect(config.telegramMiniAppUrl).toBe('https://t.me/earnit_bot?startapp=home');
    });

    it('falls back to TELEGRAM_MINI_APP_URL when PUBLIC_TELEGRAM_MINI_APP_URL is unset', () => {
        const config = loadAppConfig({
            APP_URL: 'http://localhost:3000',
            TELEGRAM_MINI_APP_URL: 'https://t.me/earnit_bot?startapp=home',
        } as NodeJS.ProcessEnv);

        expect(config.telegramMiniAppUrl).toBe('https://t.me/earnit_bot?startapp=home');
    });

    it('returns null for telegramMiniAppUrl when env is empty or whitespace', () => {
        const config = loadAppConfig({
            APP_URL: 'http://localhost:3000',
            PUBLIC_TELEGRAM_MINI_APP_URL: '   ',
        } as NodeJS.ProcessEnv);

        expect(config.telegramMiniAppUrl).toBeNull();
    });
});
