import { afterEach, describe, expect, it, vi } from 'vitest';

const { logClientWarn } = vi.hoisted(() => ({
    logClientWarn: vi.fn(),
}));

vi.mock('$lib/logging/clientLogger', () => ({
    logClientWarn,
}));

import {
    buildI18nPayload,
    createTranslationRuntime,
    getI18nPayloadForPath,
    resolveDomainsForPath,
    resolveLegacyAlias,
    resolveLocaleFromAcceptLanguage,
    shouldCanonicalizePath,
    splitLocaleFromPath,
    translateKey,
} from '../../src/lib/i18n';

describe('i18n helpers', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllEnvs();
        logClientWarn.mockClear();
    });

    it('extracts the locale prefix from incoming paths', () => {
        expect(splitLocaleFromPath('/en/app/tasks')).toEqual({ locale: 'en', pathname: '/app/tasks' });
        expect(splitLocaleFromPath('/ru')).toEqual({ locale: 'ru', pathname: '/' });
        expect(splitLocaleFromPath('/about')).toEqual({ locale: null, pathname: '/about' });
    });

    it('resolves Accept-Language in preferred order', () => {
        expect(resolveLocaleFromAcceptLanguage('ru-RU,ru;q=0.9,en;q=0.5')).toBe('ru');
        expect(resolveLocaleFromAcceptLanguage('de-DE,en;q=0.8')).toBe('en');
        expect(resolveLocaleFromAcceptLanguage('fr-FR')).toBeNull();
    });

    it('recognizes legacy aliases and canonicalization rules', () => {
        expect(resolveLegacyAlias('/login.html')).toBe('/login');
        expect(resolveLegacyAlias('/verify.html')).toBe('/verify');
        expect(resolveLegacyAlias('/blog')).toBeNull();
        expect(shouldCanonicalizePath('/about')).toBe(true);
        expect(shouldCanonicalizePath('/en/about')).toBe(false);
        expect(shouldCanonicalizePath('/api/data')).toBe(false);
        // EXPLAIN: Bare public routes bypass canonicalization to stay shareable.
        expect(shouldCanonicalizePath('/how')).toBe(false);
        expect(shouldCanonicalizePath('/tasks')).toBe(false);
        expect(shouldCanonicalizePath('/parents')).toBe(false);
        expect(shouldCanonicalizePath('/faq')).toBe(false);
    });

    it('returns Russian translation when available', () => {
        expect(translateKey(buildI18nPayload('ru', ['common']), 'common.actions.login')).toBe('Войти');
    });

    it('loads a dedicated super-admin domain for admin routes', () => {
        expect(resolveDomainsForPath('/super-admin')).toEqual(['common', 'superadmin', 'errors']);
        expect(translateKey(buildI18nPayload('en', ['superadmin']), 'superadmin.tabs.dashboard')).toBe('Overview');
    });

    it('loads a dedicated analytics domain for the analytics section', () => {
        expect(resolveDomainsForPath('/app/analytics')).toEqual(['common', 'app', 'analytics', 'errors']);
        expect(translateKey(buildI18nPayload('en', ['analytics']), 'analytics.charts.recommendations')).toBe('Growth ideas');
    });

    it('loads the history domain for requests and history sections', () => {
        expect(resolveDomainsForPath('/app/requests')).toEqual(['common', 'app', 'history', 'errors']);
        expect(resolveDomainsForPath('/app/history')).toEqual(['common', 'app', 'history', 'errors']);
        expect(translateKey(buildI18nPayload('en', ['history']), 'history.requests.adminTitle')).toBe('Incoming requests');
    });

    it('loads dedicated tasks, shop, and admin domains for remaining app surfaces', () => {
        expect(resolveDomainsForPath('/app/tasks')).toEqual(['common', 'app', 'tasks', 'errors']);
        expect(resolveDomainsForPath('/app/settings')).toEqual(['common', 'app', 'admin', 'errors']);
        expect(resolveDomainsForPath('/app/catalog')).toEqual(['common', 'app', 'admin', 'errors']);
        expect(translateKey(buildI18nPayload('en', ['tasks']), 'tasks.actions.complete')).toBe('Done!');
        expect(translateKey(buildI18nPayload('en', ['admin']), 'admin.rules.title')).toBe('Rules and goals');
    });

    it('deduplicates domains and interpolates translated templates', () => {
        const payload = buildI18nPayload('ru', ['tasks', 'tasks', 'common']);

        expect(payload.domains).toEqual(['tasks', 'common']);
        expect((payload.messages.tasks as { section?: { import?: string } } | undefined)?.section?.import).toBe('Импорт CSV');
        expect(translateKey(payload, 'tasks.requestNoteModal.description', { title: 'Wash dishes' }))
            .toContain('Wash dishes');
    });

    it('builds a runtime helper set for the current path locale', () => {
        const runtime = createTranslationRuntime(getI18nPayloadForPath('/app/tasks', 'en'));

        expect(runtime.locale).toBe('en');
        expect(runtime.domains).toEqual(['common', 'app', 'tasks', 'errors']);
        expect(runtime.t('tasks.import.title')).toBe('Import tasks from CSV');
        expect(runtime.href('/app/tasks')).toBe('/en/app/tasks');
        expect(runtime.swapLocale('/en/app/tasks', 'ru')).toBe('/ru/app/tasks');
        expect(runtime.formatCoins(2)).toBe('2 coins');
    });

    it('logs and falls back to english when a locale translation is missing in dev mode', async () => {
        vi.stubEnv('DEV', true);

        const payload = {
            locale: 'ru',
            domains: ['tasks'],
            messages: {
                tasks: {
                    actions: {
                        complete: undefined,
                    },
                },
            },
        } as never;

        expect(translateKey(payload, 'tasks.actions.complete')).toBe('Done!');
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(logClientWarn).toHaveBeenCalledWith('i18n.missing_translation', 'Missing locale translation', {
            locale: 'ru',
            key: 'tasks.actions.complete',
        });

        expect(() => translateKey({
            locale: 'ru',
            domains: ['tasks'],
            messages: {
                tasks: {},
            },
        } as never, 'tasks.nonexistent.key' as never)).toThrow('Missing English translation for tasks.nonexistent.key');
    });
});
