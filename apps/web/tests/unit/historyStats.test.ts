import { describe, expect, it } from 'vitest';
import { buildHistoryStats } from '../../src/lib/components/app/sections/historyStats';
import type { HistoryEntryKind } from '../../src/lib/components/app/sections/historyGroups';

interface TestEntry {
    id: string;
    type: HistoryEntryKind;
    amount: number;
    createdAt?: string | null;
}

function localIso(year: number, month: number, day: number): string {
    return new Date(year, month - 1, day, 12).toISOString();
}

describe('buildHistoryStats', () => {
    it('builds a rolling 3 month summary from the current month', () => {
        const stats = buildHistoryStats<TestEntry>({
            now: new Date(2026, 5, 30, 12),
            range: '3m',
            entries: [
                { id: 'march', type: 'earn', amount: 9, createdAt: localIso(2026, 3, 25) },
                { id: 'april-task', type: 'earn', amount: 12, createdAt: localIso(2026, 4, 8) },
                { id: 'may-buy', type: 'spend', amount: -7, createdAt: localIso(2026, 5, 10) },
                { id: 'june-task', type: 'earn', amount: 5, createdAt: localIso(2026, 6, 11) },
                { id: 'june-buy', type: 'spend', amount: -10, createdAt: localIso(2026, 6, 14) },
            ],
            getAmount: entry => entry.amount,
            getCreatedAt: entry => entry.createdAt,
            getKind: entry => entry.type,
        });

        expect(stats.totalEarned).toBe(17);
        expect(stats.totalSpent).toBe(17);
        expect(stats.taskCount).toBe(2);
        expect(stats.purchaseCount).toBe(2);
        expect(stats.activeMonths).toBe(3);
        expect(stats.monthly.map(month => month.monthKey)).toEqual(['2026-04', '2026-05', '2026-06']);
        expect(stats.largestPurchase?.id).toBe('june-buy');
    });

    it('keeps the full history for the all-time range and skips invalid dates', () => {
        const stats = buildHistoryStats<TestEntry>({
            now: new Date(2026, 5, 30, 12),
            range: 'all',
            entries: [
                { id: 'task', type: 'earn', amount: 6, createdAt: localIso(2026, 1, 3) },
                { id: 'purchase', type: 'spend', amount: 4, createdAt: localIso(2026, 2, 9) },
                { id: 'purchase-2', type: 'spend', amount: -8, createdAt: localIso(2026, 2, 14) },
                { id: 'invalid', type: 'earn', amount: 99, createdAt: 'not-a-date' },
                { id: 'missing', type: 'earn', amount: 99, createdAt: null },
            ],
            getAmount: entry => entry.amount,
            getCreatedAt: entry => entry.createdAt,
            getKind: entry => entry.type,
        });

        expect(stats.totalCount).toBe(3);
        expect(stats.totalEarned).toBe(6);
        expect(stats.totalSpent).toBe(12);
        expect(stats.averageSpentPerPurchase).toBe(6);
        expect(stats.monthly.map(month => month.monthKey)).toEqual(['2026-01', '2026-02']);
        expect(stats.largestPurchase?.id).toBe('purchase-2');
    });
});
