import { describe, expect, it } from 'vitest';

import {
    getAppSectionFromPath,
    getDefaultAppSection,
    isSectionAllowed,
    toAppPath,
} from '../../src/lib/app/routes';

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

    it('builds and parses canonical /app section paths', () => {
        expect(toAppPath('requests')).toBe('/app/requests');
        expect(getAppSectionFromPath('/app/requests')).toBe('requests');
        expect(getAppSectionFromPath('/app')).toBeNull();
        expect(getAppSectionFromPath('/blog')).toBeNull();
    });
});