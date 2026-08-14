import { describe, expect, it } from 'vitest';
import { rankGroups, type GroupUsage } from '../../src/lib/components/telegram/telegramGroupUsage';

describe('rankGroups', () => {
    it('sorts by selection count descending', () => {
        const usage: Record<string, GroupUsage> = {
            'Утро': { count: 5, lastAt: 100 },
            'Дом': { count: 2, lastAt: 500 },
            'Учёба': { count: 9, lastAt: 50 },
        };
        expect(rankGroups(['Дом', 'Утро', 'Учёба'], usage)).toEqual(['Учёба', 'Утро', 'Дом']);
    });

    it('breaks ties by last selected time descending', () => {
        const usage: Record<string, GroupUsage> = {
            'Утро': { count: 3, lastAt: 100 },
            'Дом': { count: 3, lastAt: 900 },
        };
        expect(rankGroups(['Утро', 'Дом'], usage)).toEqual(['Дом', 'Утро']);
    });

    it('breaks remaining ties alphabetically', () => {
        expect(rankGroups(['b', 'a', 'c'], {})).toEqual(['a', 'b', 'c']);
    });

    it('keeps unseen groups last (sorted by name)', () => {
        const usage: Record<string, GroupUsage> = {
            'Утро': { count: 4, lastAt: 100 },
        };
        expect(rankGroups(['Вечер', 'Утро', 'Спорт'], usage)).toEqual(['Утро', 'Вечер', 'Спорт']);
    });
});
