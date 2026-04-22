import { describe, expect, it } from 'vitest';

import { formatCoins, getPluralCategory } from '../../src/lib/i18n';

describe('i18n formatters', () => {
    it('uses English singular and plural coin labels', () => {
        expect(formatCoins('en', 1)).toBe('1 coin');
        expect(formatCoins('en', 2)).toBe('2 coins');
        expect(getPluralCategory('en', 1)).toBe('one');
        expect(getPluralCategory('en', 2)).toBe('other');
    });

    it('uses Russian plural categories for coins', () => {
        expect(formatCoins('ru', 1)).toBe('1 монета');
        expect(formatCoins('ru', 2)).toBe('2 монеты');
        expect(formatCoins('ru', 5)).toBe('5 монет');
        expect(formatCoins('ru', 11)).toBe('11 монет');
        expect(formatCoins('ru', 21)).toBe('21 монета');
        expect(formatCoins('ru', 24)).toBe('24 монеты');
    });
});