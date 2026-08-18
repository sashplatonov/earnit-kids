export type AnalyticsQuestActionTarget = 'tasks';

export type AnalyticsQuestStatus = 'not-started' | 'in-progress' | 'ready' | 'completed';

export interface AnalyticsDailyQuest {
    id: string;
    title: string;
    description: string;
    subtitle: string;
    current: number;
    target: number;
    percent: number;
    rewardLabel: string;
    actionLabel: string;
    actionTarget: AnalyticsQuestActionTarget;
    status: AnalyticsQuestStatus;
    /** Visual accent — task, reward, or streak */
    variant: 'task' | 'reward' | 'streak';
}

export interface AnalyticsDailyQuestI18n {
    locale: string;
    formatNumber(value: number): string;
    t(key: string, variables?: Record<string, string | number>): string;
}

export interface AnalyticsQuestTaskContext {
    title: string;
    groupName: string;
    comment: string | null;
    coins: number | null;
}

export interface AnalyticsQuestRecommendationContext {
    title: string;
    description: string;
    groupName: string;
    coins: number | null;
    reason: string | null;
}

interface BuildDailyQuestsInput {
    currentBalance: number;
    completedTaskCount: number;
    i18n: AnalyticsDailyQuestI18n;
    isAdmin: boolean;
    periodEarned: number;
    recommendations: AnalyticsQuestRecommendationContext[];
    streakValue: number;
    tasks: AnalyticsQuestTaskContext[];
}

const STREAK_TARGET = 3;

/**
 * Build 2 motivational cards for the "What's Next?" section:
 * 1. Quick Task — a specific task the child can do right now
 * 2. Streak — current streak progress toward a milestone
 */
export function buildDailyQuests(input: BuildDailyQuestsInput): AnalyticsDailyQuest[] {
    const safe = {
        ...input,
        currentBalance: sanitizeMetric(input.currentBalance),
        completedTaskCount: sanitizeMetric(input.completedTaskCount),
        periodEarned: sanitizeMetric(input.periodEarned),
        streakValue: sanitizeMetric(input.streakValue),
    };

    return [
        buildQuickTaskCard(safe),
        buildStreakCard(safe),
    ];
}


function buildQuickTaskCard(input: BuildDailyQuestsInput): AnalyticsDailyQuest {
    const { i18n, isAdmin, recommendations, tasks } = input;

    const recommendedTask = chooseRecommendedTask(tasks, recommendations);
    const quickTask = recommendedTask ?? chooseQuickTask(tasks);

    if (!quickTask) {
        return {
            id: 'next-task',
            title: i18n.t('cardTaskFallbackTitle'),
            description: i18n.t(isAdmin ? 'cardTaskFallbackDescAdmin' : 'cardTaskFallbackDescChild'),
            subtitle: '',
            current: 0,
            target: 1,
            percent: 0,
            rewardLabel: '',
            actionLabel: i18n.t('cardActionOpenTasks'),
            actionTarget: 'tasks',
            status: 'not-started',
            variant: 'task',
        };
    }

    const coins = sanitizeMetric(quickTask.coins);
    const rec = recommendations.find((r) => r.title === quickTask.title) ?? null;
    const description = rec?.description && rec.description !== rec.title
        ? rec.description
        : quickTask.comment && quickTask.comment !== quickTask.title
            ? quickTask.comment
            : i18n.t('cardTaskDesc', { coins: i18n.formatNumber(coins) });

    return {
        id: 'next-task',
        title: quickTask.title,
        description,
        subtitle: quickTask.groupName,
        current: 1,
        target: 1,
        percent: 100,
        rewardLabel: coins > 0 ? i18n.t('cardTaskReward', { coins: i18n.formatNumber(coins) }) : '',
        actionLabel: i18n.t('cardActionDoTask'),
        actionTarget: 'tasks',
        status: 'ready',
        variant: 'task',
    };
}


function buildStreakCard(input: BuildDailyQuestsInput): AnalyticsDailyQuest {
    const { i18n, streakValue } = input;
    const target = STREAK_TARGET;

    return {
        id: 'streak',
        title: streakValue > 0
            ? i18n.t('cardStreakActiveTitle', { days: streakValue })
            : i18n.t('cardStreakStartTitle'),
        description: streakValue > 0
            ? i18n.t('cardStreakActiveDesc', { current: streakValue, target })
            : i18n.t('cardStreakStartDesc'),
        subtitle: '',
        current: streakValue,
        target,
        percent: clampPercent(streakValue, target),
        rewardLabel: streakValue >= target
            ? i18n.t('cardStreakDoneBadge')
            : i18n.t('cardStreakGoalBadge', { target }),
        actionLabel: i18n.t('cardActionOpenTasks'),
        actionTarget: 'tasks',
        status: streakValue >= target ? 'completed' : streakValue > 0 ? 'in-progress' : 'not-started',
        variant: 'streak',
    };
}


function chooseRecommendedTask(
    tasks: AnalyticsQuestTaskContext[],
    recommendations: AnalyticsQuestRecommendationContext[],
): AnalyticsQuestTaskContext | null {
    for (const rec of recommendations) {
        const matched = tasks.find((t) => t.title === rec.title);
        if (matched) return matched;
    }
    return null;
}

function chooseQuickTask(tasks: AnalyticsQuestTaskContext[]): AnalyticsQuestTaskContext | null {
    const ranked = tasks
        .filter((t) => sanitizeMetric(t.coins) > 0)
        .sort((a, b) => sanitizeMetric(a.coins) - sanitizeMetric(b.coins));
    return ranked[0] ?? tasks[0] ?? null;
}

function sanitizeMetric(value: number | null): number {
    if (typeof value !== 'number' || !Number.isFinite(value)) return 0;
    return Math.max(0, value);
}

function clampPercent(current: number, target: number): number {
    if (!Number.isFinite(current) || !Number.isFinite(target) || target <= 0) return 0;
    return Math.max(0, Math.min(100, Math.round((sanitizeMetric(current) / sanitizeMetric(target)) * 100)));
}