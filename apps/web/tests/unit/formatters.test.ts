import { describe, expect, it } from 'vitest';

import {
    formatCoins,
    formatDate,
    formatDateTime,
    formatMoneyLike,
    formatNumber,
    formatShortDate,
    getPluralCategory,
} from '../../src/lib/i18n';

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
        expect(getPluralCategory('ru', -21)).toBe('one');
    });

    it('formats numbers and dates with locale-specific output', () => {
        expect(formatNumber('en', 1234.5)).toBe('1,234.5');
        expect(formatNumber('ru', 1234.5)).toMatch(/^1.*234,5$/u);
        expect(formatMoneyLike('en', 12.8)).toBe('13');
        expect(formatMoneyLike('ru', 12.8)).toBe('13');
        expect(formatDate('en', '2024-01-02T03:04:05.000Z', {
            timeZone: 'UTC',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
        })).toBe('01/02/2024');
        expect(formatDate('ru', '2024-01-02T03:04:05.000Z', {
            timeZone: 'UTC',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
        })).toBe('02.01.2024');
        expect(formatShortDate('en', '2024-01-02T03:04:05.000Z')).toContain('2024');
        expect(formatShortDate('ru', '2024-01-02T03:04:05.000Z')).toContain('2024');
        expect(formatDateTime('en', '2024-01-02T03:04:05.000Z')).toContain('2024');
    });
});
