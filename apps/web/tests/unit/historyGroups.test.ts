import { describe, expect, it } from 'vitest';
import { groupHistoryEntries, type HistoryEntryKind } from '../../src/lib/components/app/sections/historyGroups';

interface TestEntry {
    id: string;
    type: HistoryEntryKind;
    amount: number;
    createdAt?: string | null;
}

function localIso(year: number, month: number, day: number): string {
    return new Date(year, month - 1, day, 12).toISOString();
}

describe('groupHistoryEntries', () => {
    it('prioritizes today, this week, last week, then month groups', () => {
        const groups = groupHistoryEntries<TestEntry>({
            now: new Date(2026, 3, 22, 12),
            entries: [
                { id: 'old-month', type: 'earn', amount: 2, createdAt: localIso(2026, 3, 30) },
                { id: 'current-month', type: 'spend', amount: 7, createdAt: localIso(2026, 4, 10) },
                { id: 'last-week', type: 'spend', amount: 5, createdAt: localIso(2026, 4, 14) },
                { id: 'today', type: 'earn', amount: 3, createdAt: localIso(2026, 4, 22) },
                { id: 'this-week', type: 'earn', amount: 4, createdAt: localIso(2026, 4, 21) },
                { id: 'no-date', type: 'earn', amount: 1, createdAt: null },
            ],
            getAmount: entry => entry.amount,
            getCreatedAt: entry => entry.createdAt,
            getKind: entry => entry.type,
        });

        expect(groups.map(group => group.key)).toEqual([
            'today',
            'thisWeek',
            'lastWeek',
            'month:2026-04',
            'month:2026-03',
            'no-date',
        ]);
        expect(groups[0]?.entries.map(entry => entry.id)).toEqual(['today']);
        expect(groups[1]?.entries.map(entry => entry.id)).toEqual(['this-week']);
        expect(groups[2]?.entries.map(entry => entry.id)).toEqual(['last-week']);
        expect(groups[3]?.entries.map(entry => entry.id)).toEqual(['current-month']);
        expect(groups[4]?.entries.map(entry => entry.id)).toEqual(['old-month']);
    });

    it('collapses non-current month groups by default', () => {
        const groups = groupHistoryEntries<TestEntry>({
            now: new Date(2026, 3, 22, 12),
            entries: [
                { id: 'current-month', type: 'spend', amount: 7, createdAt: localIso(2026, 4, 10) },
                { id: 'old-month', type: 'earn', amount: 2, createdAt: localIso(2026, 3, 30) },
            ],
            getAmount: entry => entry.amount,
            getCreatedAt: entry => entry.createdAt,
            getKind: entry => entry.type,
        });

        expect(groups.find(group => group.key === 'month:2026-04')?.collapsedByDefault).toBe(false);
        expect(groups.find(group => group.key === 'month:2026-03')?.collapsedByDefault).toBe(true);
    });

    it('summarizes earned and spent amounts per group', () => {
        const groups = groupHistoryEntries<TestEntry>({
            now: new Date(2026, 3, 22, 12),
            entries: [
                { id: 'earn', type: 'earn', amount: 3, createdAt: localIso(2026, 4, 22) },
                { id: 'spend', type: 'spend', amount: -8, createdAt: localIso(2026, 4, 22) },
            ],
            getAmount: entry => entry.amount,
            getCreatedAt: entry => entry.createdAt,
            getKind: entry => entry.type,
        });

        expect(groups[0]?.earned).toBe(3);
        expect(groups[0]?.spent).toBe(8);
    });
});
