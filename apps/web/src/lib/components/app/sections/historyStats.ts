import type { HistoryEntryKind } from './historyGroups';

export type HistoryStatsRange = '3m' | '6m' | 'all';

export interface HistoryStatsMonth<T> {
    monthKey: string;
    earned: number;
    spent: number;
    taskCount: number;
    purchaseCount: number;
    totalCount: number;
    largestPurchase: T | null;
}

export interface HistoryStats<T> {
    range: HistoryStatsRange;
    totalEarned: number;
    totalSpent: number;
    taskCount: number;
    purchaseCount: number;
    totalCount: number;
    largestPurchase: T | null;
    activeMonths: number;
    averageSpentPerPurchase: number;
    monthly: HistoryStatsMonth<T>[];
}

interface HistoryStatsOptions<T> {
    entries: T[];
    range: HistoryStatsRange;
    getAmount: (entry: T) => number;
    getCreatedAt: (entry: T) => string | null | undefined;
    getKind: (entry: T) => HistoryEntryKind;
    now?: Date;
}

export function buildHistoryStats<T>(options: HistoryStatsOptions<T>): HistoryStats<T> {
    const now = options.now ?? new Date();
    const rangeStart = getRangeStart(options.range, now);
    const months = new Map<string, HistoryStatsMonth<T>>();

    let totalEarned = 0;
    let totalSpent = 0;
    let taskCount = 0;
    let purchaseCount = 0;
    let totalCount = 0;
    let largestPurchase: T | null = null;

    for (const entry of [...options.entries].sort((left, right) => compareEntriesByDate(left, right, options.getCreatedAt))) {
        const date = parseDate(options.getCreatedAt(entry));
        if (!date) continue;
        if (rangeStart && date < rangeStart) continue;

        const kind = options.getKind(entry);
        const amount = Number(options.getAmount(entry) ?? 0);
        const monthKey = getMonthKey(date);
        const month = months.get(monthKey) ?? createMonth<T>(monthKey);

        totalCount += 1;
        month.totalCount += 1;

        if (kind === 'earn') {
            totalEarned += amount;
            taskCount += 1;
            month.earned += amount;
            month.taskCount += 1;
        } else if (kind === 'spend') {
            const spent = Math.abs(amount);
            totalSpent += spent;
            purchaseCount += 1;
            month.spent += spent;
            month.purchaseCount += 1;
            if (!largestPurchase || spent > Math.abs(options.getAmount(largestPurchase) ?? 0)) {
                largestPurchase = entry;
            }
            if (!month.largestPurchase || spent > Math.abs(options.getAmount(month.largestPurchase) ?? 0)) {
                month.largestPurchase = entry;
            }
        }

        months.set(monthKey, month);
    }

    return {
        range: options.range,
        totalEarned,
        totalSpent,
        taskCount,
        purchaseCount,
        totalCount,
        largestPurchase,
        activeMonths: months.size,
        averageSpentPerPurchase: purchaseCount > 0 ? totalSpent / purchaseCount : 0,
        monthly: [...months.values()].sort((left, right) => left.monthKey.localeCompare(right.monthKey)),
    };
}

function createMonth<T>(monthKey: string): HistoryStatsMonth<T> {
    return {
        monthKey,
        earned: 0,
        spent: 0,
        taskCount: 0,
        purchaseCount: 0,
        totalCount: 0,
        largestPurchase: null,
    };
}

function compareEntriesByDate<T>(left: T, right: T, getCreatedAt: (entry: T) => string | null | undefined): number {
    return getDateTime(left, getCreatedAt) - getDateTime(right, getCreatedAt);
}

function getDateTime<T>(entry: T, getCreatedAt: (entry: T) => string | null | undefined): number {
    return parseDate(getCreatedAt(entry))?.getTime() ?? 0;
}

function parseDate(value: string | null | undefined): Date | null {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function getRangeStart(range: HistoryStatsRange, now: Date): Date | null {
    if (range === 'all') return null;
    const startMonthOffset = range === '3m' ? -2 : -5;
    return startOfMonth(addMonths(now, startMonthOffset));
}

function addMonths(date: Date, offset: number): Date {
    return new Date(date.getFullYear(), date.getMonth() + offset, 1);
}

function startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
}

function getMonthKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}
