import { describe, expect, it } from 'vitest';
import { buildShopCatalogItemViewModel, buildTaskCatalogItemViewModel } from '$lib/services/catalogItemViewModel';

describe('catalog item view models', () => {
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
