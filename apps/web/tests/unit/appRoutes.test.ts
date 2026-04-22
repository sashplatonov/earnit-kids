import { describe, expect, it } from 'vitest';

import {
    getAppSectionFromPath,
    getDefaultAppSection,
    isSectionAllowed,
    toAppPath,
} from '../../src/lib/app/routes';
import { buildAlternatePaths, localizePath, swapPathLocale } from '../../src/lib/i18n';

describe('app routes', () => {
    it('uses analytics as the default parent section', () => {
        expect(getDefaultAppSection('parent')).toBe('analytics');
        expect(getDefaultAppSection('admin')).toBe('analytics');
    });

    it('uses tasks as the default child section', () => {
        expect(getDefaultAppSection('child')).toBe('tasks');
        expect(getDefaultAppSection(undefined)).toBe('tasks');
    });

    it('limits admin-only sections for child sessions', () => {
        expect(isSectionAllowed('limits', 'child')).toBe(false);
        expect(isSectionAllowed('catalog', 'child')).toBe(false);
        expect(isSectionAllowed('shop', 'child')).toBe(true);
    });

    it('builds and parses locale-prefixed /app section paths', () => {
        expect(toAppPath('requests')).toBe('/en/app/requests');
        expect(toAppPath('requests', 'ru')).toBe('/ru/app/requests');
        expect(getAppSectionFromPath('/en/app/requests')).toBe('requests');
        expect(getAppSectionFromPath('/ru/app/requests')).toBe('requests');
        expect(getAppSectionFromPath('/en/app')).toBeNull();
        expect(getAppSectionFromPath('/blog')).toBeNull();
    });

    it('swaps and builds locale prefixes for public paths', () => {
        expect(localizePath('/about', 'ru')).toBe('/ru/about');
        expect(localizePath('/', 'en')).toBe('/en/');
        expect(swapPathLocale('/en/app/shop', 'ru')).toBe('/ru/app/shop');
        expect(buildAlternatePaths('/ru/about')).toEqual({
            en: '/en/about',
            ru: '/ru/about',
            'x-default': '/en/about',
        });
    });
});