import { describe, expect, it } from 'vitest';
import { buildShopCatalogItemViewModel, buildTaskCatalogItemViewModel } from '$lib/services/catalogItemViewModel';

describe('catalog item view models', () => {
    it('keeps server task progress as the source of truth', () => {
        const progress = { period: 'day', completed: 1, pending: 0, limit: 2, remaining: 1, available: true, windowStart: '', resetAt: '' };
        expect(buildTaskCatalogItemViewModel({ id: 1, name: 'Read', coins: 5, periodProgress: progress }).progress).toEqual(progress);
    });

    it('calculates reward affordability without dimming presentation data', () => {
        expect(buildShopCatalogItemViewModel({ id: 2, name: 'Movie', price: 25 }, 10)).toMatchObject({
            title: 'Movie', amount: 25, affordable: false, missing: 15,
        });
    });

    it('normalizes invalid and negative amounts at the presentation boundary', () => {
        expect(buildTaskCatalogItemViewModel({ id: 1, name: 'Read', coins: Number.NaN }).amount).toBe(0);
        expect(buildShopCatalogItemViewModel({ id: 2, name: 'Movie', price: -25 }, -10)).toMatchObject({
            amount: 0, affordable: true, missing: 0,
        });
    });
});
