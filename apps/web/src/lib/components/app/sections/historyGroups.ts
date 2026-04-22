export type HistoryGroupKind = 'today' | 'thisWeek' | 'lastWeek' | 'month' | 'noDate';
export type HistoryEntryKind = 'earn' | 'spend' | 'other';

export interface HistoryGroup<T> {
    key: string;
    kind: HistoryGroupKind;
    monthKey: string;
    entries: T[];
    earned: number;
    spent: number;
    collapsedByDefault: boolean;
}

interface HistoryGroupOptions<T> {
    entries: T[];
    getAmount: (entry: T) => number;
    getCreatedAt: (entry: T) => string | null | undefined;
    getKind: (entry: T) => HistoryEntryKind;
    now?: Date;
}

interface HistoryGroupBucket {
    key: string;
    kind: HistoryGroupKind;
    monthKey: string;
    collapsedByDefault: boolean;
}

const DAY_MS = 24 * 60 * 60 * 1000;

export function groupHistoryEntries<T>(options: HistoryGroupOptions<T>): HistoryGroup<T>[] {
    const now = options.now ?? new Date();
    const todayStart = startOfDay(now);
    const thisWeekStart = startOfWeek(todayStart);
    const lastWeekStart = addDays(thisWeekStart, -7);
    const nextWeekStart = addDays(thisWeekStart, 7);
    const currentMonthKey = getMonthKey(now);
    const map = new Map<string, HistoryGroup<T>>();

    for (const entry of [...options.entries].sort((left, right) => compareEntriesByDate(left, right, options.getCreatedAt))) {
        const date = parseDate(options.getCreatedAt(entry));
        const bucket = getHistoryGroupBucket(date, {
            currentMonthKey,
            lastWeekStart,
            nextWeekStart,
            thisWeekStart,
            todayStart,
        });
        let group = map.get(bucket.key);
        if (!group) {
            group = {
                ...bucket,
                entries: [],
                earned: 0,
                spent: 0,
            };
            map.set(bucket.key, group);
        }

        group.entries.push(entry);
        const amount = options.getAmount(entry);
        const kind = options.getKind(entry);
        if (kind === 'earn') group.earned += amount;
        if (kind === 'spend') group.spent += Math.abs(amount);
    }

    return [...map.values()].sort(compareGroups);
}

function getHistoryGroupBucket(
    date: Date | null,
    dates: {
        currentMonthKey: string;
        lastWeekStart: Date;
        nextWeekStart: Date;
        thisWeekStart: Date;
        todayStart: Date;
    },
): HistoryGroupBucket {
    if (!date) {
        return {
            key: 'no-date',
            kind: 'noDate',
            monthKey: '',
            collapsedByDefault: true,
        };
    }

    const dayStart = startOfDay(date);
    const monthKey = getMonthKey(date);
    if (dayStart.getTime() === dates.todayStart.getTime()) {
        return createRelativeBucket('today', monthKey);
    }

    if (dayStart >= dates.thisWeekStart && dayStart < dates.nextWeekStart) {
        return createRelativeBucket('thisWeek', monthKey);
    }

    if (dayStart >= dates.lastWeekStart && dayStart < dates.thisWeekStart) {
        return createRelativeBucket('lastWeek', monthKey);
    }

    return {
        key: `month:${monthKey}`,
        kind: 'month',
        monthKey,
        collapsedByDefault: monthKey !== dates.currentMonthKey,
    };
}

function createRelativeBucket(kind: 'today' | 'thisWeek' | 'lastWeek', monthKey: string): HistoryGroupBucket {
    return {
        key: kind,
        kind,
        monthKey,
        collapsedByDefault: false,
    };
}

function compareGroups<T>(left: HistoryGroup<T>, right: HistoryGroup<T>): number {
    const leftRank = groupRank(left);
    const rightRank = groupRank(right);
    if (leftRank !== rightRank) return leftRank - rightRank;
    return right.monthKey.localeCompare(left.monthKey);
}

function groupRank<T>(group: HistoryGroup<T>): number {
    if (group.kind === 'today') return 0;
    if (group.kind === 'thisWeek') return 1;
    if (group.kind === 'lastWeek') return 2;
    if (group.kind === 'month') return 3;
    return 4;
}

function compareEntriesByDate<T>(left: T, right: T, getCreatedAt: (entry: T) => string | null | undefined): number {
    return getDateTime(getCreatedAt(right)) - getDateTime(getCreatedAt(left));
}

function parseDate(value: string | null | undefined): Date | null {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function getDateTime(value: string | null | undefined): number {
    return parseDate(value)?.getTime() ?? 0;
}

function getMonthKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function startOfDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function startOfWeek(date: Date): Date {
    const day = date.getDay();
    const daysSinceMonday = (day + 6) % 7;
    return addDays(startOfDay(date), -daysSinceMonday);
}

function addDays(date: Date, days: number): Date {
    return new Date(date.getTime() + days * DAY_MS);
}
