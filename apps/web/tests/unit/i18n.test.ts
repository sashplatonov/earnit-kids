import { describe, expect, it } from 'vitest';

import {
    buildI18nPayload,
    resolveDomainsForPath,
    resolveLegacyAlias,
    resolveLocaleFromAcceptLanguage,
    shouldCanonicalizePath,
    splitLocaleFromPath,
    translateKey,
} from '../../src/lib/i18n';

describe('i18n helpers', () => {
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
        expect(resolveDomainsForPath('/app/shop')).toEqual(['common', 'app', 'shop', 'errors']);
        expect(resolveDomainsForPath('/app/settings')).toEqual(['common', 'app', 'admin', 'errors']);
        expect(resolveDomainsForPath('/app/catalog')).toEqual(['common', 'app', 'admin', 'errors']);
        expect(translateKey(buildI18nPayload('en', ['tasks']), 'tasks.actions.complete')).toBe('Done!');
        expect(translateKey(buildI18nPayload('en', ['shop']), 'shop.actions.request')).toBe('Request');
        expect(translateKey(buildI18nPayload('en', ['admin']), 'admin.rules.title')).toBe('Rules and goals');
    });
});