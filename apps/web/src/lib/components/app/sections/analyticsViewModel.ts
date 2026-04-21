export interface AnalyticsChartDatum {
    label: string;
    value: number;
}

export interface AnalyticsTrendDatum {
    label: string;
    earned: number;
    spent: number;
}

export interface AnalyticsRecommendationCard {
    id: string;
    icon: string;
    title: string;
    description: string;
    groupName: string;
    coins: number | null;
    reason: string | null;
}

export interface AnalyticsViewModel {
    earned: number;
    spent: number;
    net: number;
    weekEarned: number;
    weekBar: number;
    weekNote: string;
    streakValue: number;
    streakNote: string;
    taskCoins: AnalyticsChartDatum[];
    taskCount: AnalyticsChartDatum[];
    itemCoins: AnalyticsChartDatum[];
    itemCount: AnalyticsChartDatum[];
    trend: AnalyticsTrendDatum[];
    recommendations: AnalyticsRecommendationCard[];
}

type JsonRecord = Record<string, unknown>;
type TrendDatumInternal = AnalyticsTrendDatum & { isoDate: string };

interface AnalyticsSourceTask {
    name?: unknown;
    title?: unknown;
    groupName?: unknown;
    comment?: unknown;
    coins?: unknown;
}

interface AnalyticsViewModelOptions {
    currentBalance?: unknown;
    tasks?: AnalyticsSourceTask[] | null;
}

interface RecommendationTaskContext {
    title: string;
    groupName: string;
    comment: string | null;
    coins: number | null;
}

const SHORT_DATE_FORMATTER = new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
});

export function buildAnalyticsViewModel(payload: unknown, options: AnalyticsViewModelOptions = {}): AnalyticsViewModel {
    const root = asRecord(payload);
    const summary = asRecord(root?.summary);

    const taskCoins = readChartSeries(root?.taskCoins, root?.topTasks, 'coins');
    const taskCount = readChartSeries(root?.taskCount, root?.topTasks, 'count');
    const itemCoins = readChartSeries(root?.itemCoins, root?.topItems, 'coins');
    const itemCount = readChartSeries(root?.itemCount, root?.topItems, 'count');
    const trend = readTrendSeries(root?.trend, root?.trends);

    const spent = readNumber(summary?.totalSpent) ?? readNumber(root?.spent) ?? sumTrend(trend, 'spent');
    const currentBalance = readNumber(options.currentBalance) ?? readNumber(root?.balance);
    const fallbackNet = readNumber(summary?.netChange) ?? readNumber(root?.net) ?? Math.max(sumTrend(trend, 'earned') - spent, 0);
    const net = currentBalance ?? fallbackNet;
    const earned = currentBalance != null
        ? currentBalance + spent
        : readNumber(summary?.totalEarned) ?? readNumber(root?.earned) ?? net + spent;

    const weekSummary = buildWeekSummary(trend, earned);
    const streakValue = buildStreak(trend);

    return {
        earned,
        spent,
        net,
        weekEarned: weekSummary.weekEarned,
        weekBar: weekSummary.weekBar,
        weekNote: weekSummary.weekNote,
        streakValue,
        streakNote: streakValue > 0 ? `${streakValue} ${formatDayWord(streakValue)} подряд!` : 'Начните сегодня!',
        taskCoins,
        taskCount,
        itemCoins,
        itemCount,
        trend: trend.map(({ label, earned, spent }) => ({ label, earned, spent })),
        recommendations: readRecommendations(root?.recommendations, options.tasks),
    };
}

function asRecord(value: unknown): JsonRecord | null {
    if (typeof value !== 'object' || value == null || Array.isArray(value)) {
        return null;
    }
    return value as JsonRecord;
}

function readNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
    }
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
}

function readText(value: unknown): string | null {
    if (typeof value !== 'string') {
        return null;
    }
    const normalized = value.trim();
    return normalized === '' ? null : normalized;
}

function readChartSeries(formattedSource: unknown, statsSource: unknown, valueKey: 'coins' | 'count'): AnalyticsChartDatum[] {
    const formatted = readSimpleChartSeries(formattedSource);
    if (formatted.length > 0) {
        return formatted;
    }

    if (!Array.isArray(statsSource)) {
        return [];
    }

    return statsSource.flatMap((item) => {
        const record = asRecord(item);
        if (!record) {
            return [];
        }

        const value = readNumber(record[valueKey]);
        if (value == null) {
            return [];
        }

        return [{
            label: readText(record.name) ?? 'Без названия',
            value,
        }];
    });
}

function readSimpleChartSeries(source: unknown): AnalyticsChartDatum[] {
    if (!Array.isArray(source)) {
        return [];
    }

    return source.flatMap((item) => {
        const record = asRecord(item);
        if (!record) {
            return [];
        }

        const label = readText(record.label);
        const value = readNumber(record.value);
        if (label == null || value == null) {
            return [];
        }

        return [{ label, value }];
    });
}

function readTrendSeries(formattedSource: unknown, statsSource: unknown): TrendDatumInternal[] {
    const formatted = readSimpleTrendSeries(formattedSource);
    if (formatted.length > 0) {
        return formatted;
    }

    if (!Array.isArray(statsSource)) {
        return [];
    }

    return statsSource.flatMap((item) => {
        const record = asRecord(item);
        const isoDate = readText(record?.date);
        if (isoDate == null) {
            return [];
        }

        return [{
            isoDate,
            label: formatShortDate(isoDate),
            earned: readNumber(record?.earned) ?? 0,
            spent: readNumber(record?.spent) ?? 0,
        }];
    });
}

function readSimpleTrendSeries(source: unknown): TrendDatumInternal[] {
    if (!Array.isArray(source)) {
        return [];
    }

    return source.flatMap((item) => {
        const record = asRecord(item);
        const label = readText(record?.label);
        if (label == null) {
            return [];
        }

        const isoDate = readText(record?.date) ?? label;

        return [{
            isoDate,
            label,
            earned: readNumber(record?.earned) ?? 0,
            spent: readNumber(record?.spent) ?? 0,
        }];
    });
}

function readRecommendations(source: unknown, tasksSource: AnalyticsSourceTask[] | null | undefined): AnalyticsRecommendationCard[] {
    if (!Array.isArray(source)) {
        return [];
    }

    const tasks = normalizeTaskContext(tasksSource);

    return source.flatMap((item, index) => {
            const record = asRecord(item);
            if (!record) {
                return [];
            }

            const directText = readText(record.text);
            const directReason = readText(record.reason);
            const directDescription = readText(record.description) ?? readText(record.comment) ?? directReason ?? directText;
            const directCoins = readNumber(record.coins);
            const directTitle = directText ?? readText(record.name);
            if (directText != null) {
                return [{
                    id: buildRecommendationId(index, directText, directCoins, directReason),
                    icon: readText(record.icon) ?? chooseRecommendationIcon(directReason),
                    title: directText,
                    description: directDescription ?? directText,
                    groupName: readText(record.groupName) ?? 'Идея для роста',
                    coins: directCoins,
                    reason: directReason,
                }];
            }

            const name = readText(record.name);
            const reason = readText(record.reason);
            if (name == null && reason == null && directTitle == null) {
                return [];
            }

            const matchedTask = findRecommendationTask(tasks, name, readNumber(record.coins));
            const title = name ?? matchedTask?.title;
            if (title == null) {
                return [];
            }

            return [{
                id: buildRecommendationId(index, title, readNumber(record.coins) ?? matchedTask?.coins ?? null, reason),
                icon: chooseRecommendationIcon(reason),
                title,
                description: matchedTask?.comment ?? readText(record.comment) ?? reason ?? 'Повторите этот шаг, чтобы сохранить темп.',
                groupName: matchedTask?.groupName ?? readText(record.groupName) ?? 'Без группы',
                coins: readNumber(record.coins) ?? matchedTask?.coins ?? null,
                reason,
            }];
        });
}

function normalizeTaskContext(tasksSource: AnalyticsSourceTask[] | null | undefined): RecommendationTaskContext[] {
    if (!Array.isArray(tasksSource)) {
        return [];
    }

    return tasksSource.flatMap((task) => {
        const title = readText(task.name) ?? readText(task.title);
        if (title == null) {
            return [];
        }

        return [{
            title,
            groupName: readText(task.groupName) ?? 'Без группы',
            comment: readText(task.comment),
            coins: readNumber(task.coins),
        }];
    });
}

function findRecommendationTask(tasks: RecommendationTaskContext[], name: string | null, coins: number | null): RecommendationTaskContext | null {
    if (name == null) {
        return null;
    }

    const exactMatch = tasks.find((task) => task.title === name && (coins == null || task.coins === coins));
    if (exactMatch) {
        return exactMatch;
    }

    return tasks.find((task) => task.title === name) ?? null;
}

function buildRecommendationId(index: number, title: string, coins: number | null, reason: string | null): string {
    return `${index}:${title}:${coins ?? 'na'}:${reason ?? ''}`;
}

function sumTrend(trend: TrendDatumInternal[], key: 'earned' | 'spent'): number {
    return trend.reduce((total, item) => total + item[key], 0);
}

function chooseRecommendationIcon(reason: string | null): string {
    const normalized = reason?.toLowerCase() ?? '';
    if (normalized.includes('давно')) {
        return '🎯';
    }
    if (normalized.includes('повтор')) {
        return '🔁';
    }
    return '✨';
}

function buildWeekSummary(trend: TrendDatumInternal[], earned: number) {
    if (trend.length === 0) {
        return {
            weekEarned: 0,
            weekBar: 0,
            weekNote: 'Нет активности за 7 дней',
        };
    }

    const latestPoint = trend.at(-1);
    const latestDate = latestPoint ? parseIsoDate(latestPoint.isoDate) : null;
    if (latestDate == null) {
        return {
            weekEarned: earned,
            weekBar: earned > 0 ? 100 : 0,
            weekNote: earned > 0 ? `За выбранный период: ${earned} мон.` : 'Нет активности за 7 дней',
        };
    }

    const weekStart = shiftDate(latestDate, -6);
    const weekEarned = trend.reduce((sum, point) => {
        const currentDate = parseIsoDate(point.isoDate);
        if (currentDate == null || currentDate < weekStart || currentDate > latestDate) {
            return sum;
        }
        return sum + point.earned;
    }, 0);

    const baseTotal = earned > 0 ? earned : weekEarned;
    return {
        weekEarned,
        weekBar: baseTotal > 0 ? Math.min(100, Math.round((weekEarned / baseTotal) * 100)) : 0,
        weekNote: baseTotal > 0
            ? `За 7 дней из ${baseTotal} мон. в периоде`
            : 'Нет активности за 7 дней',
    };
}

function buildStreak(trend: TrendDatumInternal[]): number {
    const activeDays = new Set(
        trend
            .filter((point) => point.earned > 0)
            .map((point) => point.isoDate)
    );

    if (activeDays.size === 0) {
        return 0;
    }

    const sortedDays = [...activeDays].sort();
    const latestDate = parseIsoDate(sortedDays.at(-1) ?? '');
    if (latestDate == null) {
        return 0;
    }

    let streak = 0;
    let currentDate = latestDate;
    while (activeDays.has(formatIsoDate(currentDate))) {
        streak += 1;
        currentDate = shiftDate(currentDate, -1);
    }
    return streak;
}

function parseIsoDate(value: string): Date | null {
    const parsed = new Date(`${value}T00:00:00Z`);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function shiftDate(value: Date, deltaDays: number): Date {
    const shifted = new Date(value.getTime());
    shifted.setUTCDate(shifted.getUTCDate() + deltaDays);
    return shifted;
}

function formatIsoDate(value: Date): string {
    return value.toISOString().slice(0, 10);
}

function formatShortDate(value: string): string {
    const parsed = parseIsoDate(value);
    return parsed == null ? value : SHORT_DATE_FORMATTER.format(parsed);
}

function formatDayWord(value: number): string {
    const mod10 = value % 10;
    const mod100 = value % 100;
    if (mod10 === 1 && mod100 !== 11) {
        return 'день';
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
        return 'дня';
    }
    return 'дней';
}