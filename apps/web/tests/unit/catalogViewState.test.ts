import { describe, expect, it } from 'vitest';
import { readCatalogViewState, writeCatalogViewState } from '$lib/services/catalogViewState';

describe('catalogViewState', () => {
    it('reads valid group and view values', () => {
        expect(readCatalogViewState(new URL('https://example.test/app/tasks?group=Home&view=grid'), 'list'))
            .toEqual({ group: 'Home', view: 'grid' });
    });

    it('falls back when view is invalid', () => {
        expect(readCatalogViewState(new URL('https://example.test/app/tasks?view=tiles'), 'list').view).toBe('list');
    });

    it('updates catalog params without losing unrelated params', () => {
        const next = writeCatalogViewState(new URL('https://example.test/app/shop?child=7&group=Fun'), {
            group: '',
            view: 'grid',
        });
        expect(next.searchParams.get('child')).toBe('7');
        expect(next.searchParams.has('group')).toBe(false);
        expect(next.searchParams.get('view')).toBe('grid');
    });
});
