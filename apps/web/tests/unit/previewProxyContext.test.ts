import { describe, expect, it } from 'vitest';
import { buildProxyReferer, resolveProxyContext, resolveTelegramMiniAppUrl } from '../../scripts/proxy-context.mjs';

describe('resolveProxyContext', () => {
    it('keeps backend target and public origin separate', () => {
        const context = resolveProxyContext({
            BACKEND_URL: 'http://backend:8080///',
            APP_URL: 'http://localhost:5001///',
            PORT: '4174',
            HOST: '0.0.0.0',
        } as NodeJS.ProcessEnv);

        expect(context.backendOrigin).toBe('http://backend:8080');
        expect(context.backendUrl.origin).toBe('http://backend:8080');
        expect(context.publicOrigin).toBe('http://localhost:5001');
        expect(context.publicUrl.origin).toBe('http://localhost:5001');
    });

    it('falls back to the current preview host when no public origin override exists', () => {
        const context = resolveProxyContext({
            PORT: '4176',
            HOST: '127.0.0.1',
        } as NodeJS.ProcessEnv);

        expect(context.publicOrigin).toBe('http://127.0.0.1:4176');
    });
});

describe('buildProxyReferer', () => {
    it('rewrites referer to the configured public origin while preserving path and query', () => {
        expect(buildProxyReferer('http://127.0.0.1:4176/login.html?tab=register', 'http://localhost:5001')).toBe(
            'http://localhost:5001/login.html?tab=register'
        );
    });


            it('prefers APP_URL over stale PUBLIC_BASE_URL when both are present', () => {
                const context = resolveProxyContext({
                    BACKEND_URL: 'http://backend:8080',
                    PUBLIC_BASE_URL: 'http://localhost:3000',
                    APP_URL: 'http://localhost:5001',
                } as NodeJS.ProcessEnv);

                expect(context.publicOrigin).toBe('http://localhost:5001');
            });
    it('falls back to the public origin when referer is malformed', () => {
        expect(buildProxyReferer('not-a-url', 'http://localhost:5001')).toBe('http://localhost:5001');
    });
});

describe('resolveTelegramMiniAppUrl', () => {
    it('builds a deep link with a short startapp command (URL is in BotFather)', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_MINI_APP_URL: 'https://earnit-kids.igo.mywire.org/telegram///',
            TELEGRAM_BOT_USERNAME: 'earnit_bot',
            APP_URL: 'http://localhost:5001',
        } as NodeJS.ProcessEnv)).toBe('https://t.me/earnit_bot?startapp=home');
    });

    it('builds the deep link from bot username alone when only APP_URL is set', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_BOT_USERNAME: 'earnit_bot',
            APP_URL: 'http://localhost:5001///',
        } as NodeJS.ProcessEnv)).toBe('https://t.me/earnit_bot?startapp=home');
    });

    it('ignores APP_URL path/query because the Mini App URL lives in BotFather', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_BOT_USERNAME: 'earnit_bot',
            APP_URL: 'https://earnit-kids.igo.mywire.org/en/app/tasks?tab=1',
        } as NodeJS.ProcessEnv)).toBe('https://t.me/earnit_bot?startapp=home');
    });

    it('uses an explicit t.me deep link verbatim when TELEGRAM_MINI_APP_URL is one', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_MINI_APP_URL: 'https://t.me/earnit_bot?startapp=custom',
            TELEGRAM_BOT_USERNAME: 'earnit_bot',
            APP_URL: 'http://localhost:5001',
        } as NodeJS.ProcessEnv)).toBe('https://t.me/earnit_bot?startapp=custom');
    });

    it('returns empty when bot username is missing even if a Mini App URL is set', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_MINI_APP_URL: 'https://earnit-kids.igo.mywire.org/telegram',
            APP_URL: 'http://localhost:5001',
        } as NodeJS.ProcessEnv)).toBe('');
    });

    it('returns empty when bot username is missing', () => {
        expect(resolveTelegramMiniAppUrl({
            APP_URL: 'http://localhost:5001',
        } as NodeJS.ProcessEnv)).toBe('');

        expect(resolveTelegramMiniAppUrl({} as NodeJS.ProcessEnv)).toBe('');
    });

    it('builds a home deep link from bot username alone', () => {
        expect(resolveTelegramMiniAppUrl({
            TELEGRAM_BOT_USERNAME: 'earnit_bot',
        } as NodeJS.ProcessEnv)).toBe('https://t.me/earnit_bot?startapp=home');
    });
});