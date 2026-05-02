import { analyticsMessages as englishAnalyticsMessages } from '$lib/i18n/messages/en/analytics';
import {
    buildAchievements,
    type AchievementBadge,
    type AchievementI18n,
} from './analyticsAchievements';
import {
    buildDailyQuests,
    type AnalyticsDailyQuest,
    type AnalyticsDailyQuestI18n,
    type AnalyticsQuestShopItemContext,
    type AnalyticsQuestTaskContext,
} from './analyticsDailyQuests';

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

export type { AnalyticsDailyQuest } from './analyticsDailyQuests';
export type { AchievementBadge } from './analyticsAchievements';

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
    dailyQuests: AnalyticsDailyQuest[];
    achievements: AchievementBadge[];
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

interface AnalyticsSourceShopItem {
    name?: unknown;
    title?: unknown;
    groupName?: unknown;
    comment?: unknown;
    price?: unknown;
    coins?: unknown;
}

interface AnalyticsViewModelOptions {
    currentBalance?: unknown;
    tasks?: AnalyticsSourceTask[] | null;
    shopItems?: AnalyticsSourceShopItem[] | null;
    isAdmin?: boolean;
    i18n?: AnalyticsViewModelI18n;
}

type AnalyticsModelMessageKey = keyof typeof englishAnalyticsMessages.model;

export interface AnalyticsViewModelI18n {
    locale: string;
    formatShortDate(value: string): string;
    formatNumber(value: number): string;
    t(key: AnalyticsModelMessageKey, variables?: Record<string, string | number>): string;
}

const SHORT_DATE_FORMATTER = new Intl.DateTimeFormat('en-US', {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
});

const DEFAULT_ANALYTICS_MODEL_MESSAGES = englishAnalyticsMessages.model;

const DEFAULT_ANALYTICS_I18N: AnalyticsViewModelI18n = {
    locale: 'en',
    formatShortDate(value: string) {
        const parsed = parseIsoDate(value);
        return parsed == null ? value : SHORT_DATE_FORMATTER.format(parsed);
    },
    formatNumber(value: number) {
        return new Intl.NumberFormat('en-US').format(value);
    },
    t(key, variables) {
        return interpolate(DEFAULT_ANALYTICS_MODEL_MESSAGES[key], variables);
    },
};

export function buildAnalyticsViewModel(payload: unknown, options: AnalyticsViewModelOptions = {}): AnalyticsViewModel {
    const i18n = options.i18n ?? DEFAULT_ANALYTICS_I18N;
    const root = asRecord(payload);
    const summary = asRecord(root?.summary);

    const taskCoins = readChartSeries(root?.taskCoins, root?.topTasks, 'coins');
    const taskCount = readChartSeries(root?.taskCount, root?.topTasks, 'count');
    const itemCoins = readChartSeries(root?.itemCoins, root?.topItems, 'coins');
    const itemCount = readChartSeries(root?.itemCount, root?.topItems, 'count');
    const trend = readTrendSeries(root?.trend, root?.trends, i18n);
    const normalizedTasks = normalizeTaskContext(options.tasks, i18n);
    const normalizedShopItems = normalizeShopContext(options.shopItems);

    const spent = readNumber(summary?.totalSpent) ?? readNumber(root?.spent) ?? sumTrend(trend, 'spent');
    const currentBalance = readNumber(options.currentBalance) ?? readNumber(root?.balance);
    const fallbackNet = readNumber(summary?.netChange) ?? readNumber(root?.net) ?? Math.max(sumTrend(trend, 'earned') - spent, 0);
    const net = currentBalance ?? fallbackNet;
    const earned = currentBalance != null
        ? currentBalance + spent
        : readNumber(summary?.totalEarned) ?? readNumber(root?.earned) ?? net + spent;

    const weekSummary = buildWeekSummary(trend, earned, i18n);
    const streakValue = buildStreak(trend);
    const recommendations = readRecommendations(root?.recommendations, options.tasks, i18n);
    const completedTaskCount = taskCount.reduce((total, item) => total + Math.max(0, item.value), 0);
    const completedItemCount = itemCount.reduce((total, item) => total + Math.max(0, item.value), 0);
    const periodEarned = trend.length > 0
        ? sumTrend(trend, 'earned')
        : readNumber(summary?.totalEarned) ?? readNumber(root?.earned) ?? earned;
    const dailyQuests = buildDailyQuests({
        currentBalance: currentBalance ?? net,
        completedTaskCount,
        i18n: i18n as AnalyticsDailyQuestI18n,
        isAdmin: options.isAdmin === true,
        periodEarned,
        recommendations,
        shopItems: normalizedShopItems,
        streakValue,
        tasks: normalizedTasks,
    });

    const achievements = buildAchievements({
        earned,
        taskCount: completedTaskCount,
        itemCount: completedItemCount,
        streakValue,
        i18n: i18n as AchievementI18n,
    });

    return {
        earned,
        spent,
        net,
        weekEarned: weekSummary.weekEarned,
        weekBar: weekSummary.weekBar,
        weekNote: weekSummary.weekNote,
        streakValue,
        streakNote: streakValue > 0
            ? i18n.t('streakDaysTemplate', { count: streakValue, dayWord: formatDayWord(streakValue, i18n) })
            : i18n.t('streakToday'),
        taskCoins,
        taskCount,
        itemCoins,
        itemCount,
        trend: trend.map(({ label, earned, spent }) => ({ label, earned, spent })),
        recommendations,
        dailyQuests,
        achievements,
    };
}

function interpolate(template: string, variables?: Record<string, string | number>): string {
    if (!variables) {
        return template;
    }

    return template.replace(/\{([\w-]+)\}/g, (match, key: string) => {
        const value = variables[key];
        return value === undefined ? match : String(value);
    });
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
            label: readText(record.name) ?? DEFAULT_ANALYTICS_I18N.t('untitled'),
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

function readTrendSeries(formattedSource: unknown, statsSource: unknown, i18n: AnalyticsViewModelI18n): TrendDatumInternal[] {
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

        if (parseIsoDate(isoDate) == null) {
            return [];
        }

        return [{
            isoDate,
            label: i18n.formatShortDate(isoDate),
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

function readRecommendations(
    source: unknown,
    tasksSource: AnalyticsSourceTask[] | null | undefined,
    i18n: AnalyticsViewModelI18n,
): AnalyticsRecommendationCard[] {
    if (!Array.isArray(source)) {
        return [];
    }

    const tasks = normalizeTaskContext(tasksSource, i18n);

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
                    icon: readText(record.icon) ?? chooseRecommendationIcon(directReason, i18n),
                    title: directText,
                    description: directDescription ?? directText,
                    groupName: readText(record.groupName) ?? i18n.t('growthIdea'),
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
                icon: chooseRecommendationIcon(reason, i18n),
                title,
                description: matchedTask?.comment ?? readText(record.comment) ?? reason ?? i18n.t('repeatStepDescription'),
                groupName: matchedTask?.groupName ?? readText(record.groupName) ?? i18n.t('noGroup'),
                coins: readNumber(record.coins) ?? matchedTask?.coins ?? null,
                reason,
            }];
        });
}

function normalizeTaskContext(
    tasksSource: AnalyticsSourceTask[] | null | undefined,
    i18n: AnalyticsViewModelI18n,
): AnalyticsQuestTaskContext[] {
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
            groupName: readText(task.groupName) ?? i18n.t('noGroup'),
            comment: readText(task.comment),
            coins: readNumber(task.coins),
        }];
    });
}

function normalizeShopContext(
    shopItemsSource: AnalyticsSourceShopItem[] | null | undefined,
): AnalyticsQuestShopItemContext[] {
    if (!Array.isArray(shopItemsSource)) {
        return [];
    }

    return shopItemsSource.flatMap((item) => {
        const title = readText(item.name) ?? readText(item.title);
        const price = readNumber(item.price) ?? readNumber(item.coins);
        if (title == null || price == null || price <= 0) {
            return [];
        }

        return [{
            title,
            groupName: readText(item.groupName) ?? '',
            comment: readText(item.comment),
            price,
        }];
    });
}

function findRecommendationTask(tasks: AnalyticsQuestTaskContext[], name: string | null, coins: number | null): AnalyticsQuestTaskContext | null {
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

function chooseRecommendationIcon(reason: string | null, i18n: AnalyticsViewModelI18n): string {
    const normalized = reason?.toLowerCase() ?? '';
    if (normalized.includes(i18n.t('reasonStaleKeyword').toLowerCase())) {
        return '🎯';
    }
    if (normalized.includes(i18n.t('reasonRepeatKeyword').toLowerCase())) {
        return '🔁';
    }
    return '✨';
}

function buildWeekSummary(trend: TrendDatumInternal[], earned: number, i18n: AnalyticsViewModelI18n) {
    if (trend.length === 0) {
        return {
            weekEarned: 0,
            weekBar: 0,
            weekNote: i18n.t('noActivityWeek'),
        };
    }

    const latestPoint = trend.at(-1);
    const latestDate = latestPoint ? parseIsoDate(latestPoint.isoDate) : null;
    if (latestDate == null) {
        return {
            weekEarned: earned,
            weekBar: earned > 0 ? 100 : 0,
            weekNote: earned > 0 ? i18n.t('selectedPeriodSummary', { earned }) : i18n.t('noActivityWeek'),
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
            ? i18n.t('last7DaysSummary', { baseTotal })
            : i18n.t('noActivityWeek'),
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

function formatDayWord(value: number, i18n: AnalyticsViewModelI18n): string {
    const category = new Intl.PluralRules(i18n.locale).select(value);

    switch (category) {
        case 'one':
            return i18n.t('dayOne');
        case 'few':
            return i18n.t('dayFew');
        case 'many':
            return i18n.t('dayMany');
        default:
            return i18n.t('dayOther');
    }
}