export type AnalyticsQuestActionTarget = 'tasks' | 'shop' | 'details';

export type AnalyticsQuestStatus = 'not-started' | 'in-progress' | 'ready' | 'completed';

export interface AnalyticsDailyQuest {
    id: string;
    title: string;
    description: string;
    current: number;
    target: number;
    percent: number;
    rewardLabel: string;
    actionLabel: string;
    actionTarget: AnalyticsQuestActionTarget;
    status: AnalyticsQuestStatus;
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

export interface AnalyticsQuestShopItemContext {
    title: string;
    groupName: string;
    comment: string | null;
    price: number;
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
    shopItems: AnalyticsQuestShopItemContext[];
    streakValue: number;
    tasks: AnalyticsQuestTaskContext[];
}

interface ProgressQuestOptions {
    id: string;
    title: string;
    description: string;
    current: number;
    target: number;
    rewardLabel: string;
    actionLabel: string;
    actionTarget: AnalyticsQuestActionTarget;
}

const TASK_TARGET = 2;
const COIN_TARGET = 30;
const STREAK_TARGET = 3;
const FALLBACK_REWARD_TARGET = 50;

export function buildDailyQuests(input: BuildDailyQuestsInput): AnalyticsDailyQuest[] {
    const currentBalance = sanitizeMetric(input.currentBalance);
    const completedTaskCount = sanitizeMetric(input.completedTaskCount);
    const periodEarned = sanitizeMetric(input.periodEarned);
    const streakValue = sanitizeMetric(input.streakValue);

    const purchaseQuest = buildPurchaseQuest({
        currentBalance,
        i18n: input.i18n,
        shopItems: input.shopItems,
    });
    const canSpendNow = purchaseQuest.status === 'ready';

    return [
        createProgressQuest({
            id: 'complete-tasks',
            title: input.i18n.t('questCompleteTasksTitle', { target: TASK_TARGET }),
            description: completedTaskCount > 0
                ? input.i18n.t('questCompleteTasksDescription', { current: completedTaskCount, target: TASK_TARGET })
                : input.i18n.t(input.isAdmin ? 'questCompleteTasksEmptyDescriptionAdmin' : 'questCompleteTasksEmptyDescriptionChild'),
            current: completedTaskCount,
            target: TASK_TARGET,
            rewardLabel: input.i18n.t('questTargetTasksLabel', { target: TASK_TARGET }),
            actionLabel: completedTaskCount >= TASK_TARGET
                ? input.i18n.t('questActionOpenDetails')
                : input.i18n.t('questActionOpenTasks'),
            actionTarget: completedTaskCount >= TASK_TARGET ? 'details' : 'tasks',
        }),
        createProgressQuest({
            id: 'earn-coins',
            title: input.i18n.t('questEarnCoinsTitle', { target: input.i18n.formatNumber(COIN_TARGET) }),
            description: periodEarned > 0
                ? input.i18n.t('questEarnCoinsDescription', {
                    current: input.i18n.formatNumber(periodEarned),
                    target: input.i18n.formatNumber(COIN_TARGET),
                })
                : input.i18n.t(input.isAdmin ? 'questEarnCoinsEmptyDescriptionAdmin' : 'questEarnCoinsEmptyDescriptionChild'),
            current: periodEarned,
            target: COIN_TARGET,
            rewardLabel: canSpendNow
                ? input.i18n.t('questEarnCoinsReadyReward')
                : input.i18n.t('questTargetCoinsLabel', { target: input.i18n.formatNumber(COIN_TARGET) }),
            actionLabel: canSpendNow
                ? input.i18n.t('questActionOpenShop')
                : periodEarned >= COIN_TARGET
                    ? input.i18n.t('questActionOpenDetails')
                    : input.i18n.t('questActionOpenTasks'),
            actionTarget: canSpendNow ? 'shop' : periodEarned >= COIN_TARGET ? 'details' : 'tasks',
        }),
        createProgressQuest({
            id: 'keep-streak',
            title: input.i18n.t('questKeepStreakTitle', { target: STREAK_TARGET }),
            description: streakValue > 0
                ? input.i18n.t('questKeepStreakActiveDescription', { current: streakValue, target: STREAK_TARGET })
                : input.i18n.t('questKeepStreakEmptyDescription'),
            current: streakValue,
            target: STREAK_TARGET,
            rewardLabel: input.i18n.t('questTargetDaysLabel', { target: STREAK_TARGET }),
            actionLabel: streakValue > 0
                ? input.i18n.t('questActionOpenDetails')
                : input.i18n.t('questActionOpenTasks'),
            actionTarget: streakValue > 0 ? 'details' : 'tasks',
        }),
        purchaseQuest,
        buildTaskQuest({
            i18n: input.i18n,
            isAdmin: input.isAdmin,
            recommendations: input.recommendations,
            tasks: input.tasks,
        }),
    ];
}

function buildPurchaseQuest(input: Pick<BuildDailyQuestsInput, 'currentBalance' | 'i18n' | 'shopItems'>): AnalyticsDailyQuest {
    const targetItem = chooseTargetShopItem(input.shopItems, input.currentBalance);
    if (!targetItem) {
        return createProgressQuest({
            id: 'reward-target',
            title: input.i18n.t('questRewardTargetFallbackTitle'),
            description: input.i18n.t('questRewardTargetFallbackDescription'),
            current: input.currentBalance,
            target: Math.max(FALLBACK_REWARD_TARGET, input.currentBalance > 0 ? roundUpToTen(input.currentBalance) : FALLBACK_REWARD_TARGET),
            rewardLabel: input.i18n.t('questRewardNoShopLabel'),
            actionLabel: input.i18n.t('questActionOpenShop'),
            actionTarget: 'shop',
        });
    }

    const remaining = Math.max(targetItem.price - input.currentBalance, 0);
    const readyToBuy = remaining === 0;

    return {
        id: 'reward-target',
        title: input.i18n.t('questRewardTargetTitle', { item: targetItem.title }),
        description: readyToBuy
            ? input.i18n.t('questRewardTargetReadyDescription', { item: targetItem.title })
            : input.i18n.t('questRewardTargetDescription', {
                current: input.i18n.formatNumber(input.currentBalance),
                target: input.i18n.formatNumber(targetItem.price),
                remaining: input.i18n.formatNumber(remaining),
            }),
        current: input.currentBalance,
        target: targetItem.price,
        percent: clampPercent(input.currentBalance, targetItem.price),
        rewardLabel: readyToBuy
            ? input.i18n.t('questRewardReadyLabel')
            : input.i18n.t('questRewardCoinsLeftLabel', { remaining: input.i18n.formatNumber(remaining) }),
        actionLabel: input.i18n.t('questActionOpenShop'),
        actionTarget: 'shop',
        status: readyToBuy ? 'ready' : input.currentBalance > 0 ? 'in-progress' : 'not-started',
    };
}

function buildTaskQuest(input: Pick<BuildDailyQuestsInput, 'i18n' | 'isAdmin' | 'recommendations' | 'tasks'>): AnalyticsDailyQuest {
    const recommendedTask = chooseRecommendedTask(input.tasks, input.recommendations);
    const quickTask = recommendedTask ?? chooseQuickTask(input.tasks);

    if (!quickTask) {
        return {
            id: 'next-task',
            title: input.i18n.t('questNextTaskFallbackTitle'),
            description: input.i18n.t(input.isAdmin ? 'questNextTaskNoTasksAdminDescription' : 'questNextTaskNoTasksChildDescription'),
            current: 0,
            target: 1,
            percent: 0,
            rewardLabel: input.i18n.t('questNextTaskSetupReward'),
            actionLabel: input.i18n.t('questActionOpenTasks'),
            actionTarget: 'tasks',
            status: 'not-started',
        };
    }

    const visibleCoins = sanitizeMetric(quickTask.coins);
    const recommendation = input.recommendations.find((item) => item.title === quickTask.title) ?? null;
    const description = recommendation?.description && recommendation.description !== recommendation.title
        ? recommendation.description
        : quickTask.comment && quickTask.comment !== quickTask.title
            ? quickTask.comment
            : input.i18n.t('questNextTaskDescription', { coins: input.i18n.formatNumber(visibleCoins) });

    return {
        id: 'next-task',
        title: input.i18n.t('questNextTaskTitle', { task: quickTask.title }),
        description,
        current: 1,
        target: 1,
        percent: 100,
        rewardLabel: visibleCoins > 0
            ? input.i18n.t('questNextTaskReward', { coins: input.i18n.formatNumber(visibleCoins) })
            : input.i18n.t('questNextTaskSetupReward'),
        actionLabel: input.i18n.t('questActionOpenTasks'),
        actionTarget: 'tasks',
        status: 'ready',
    };
}

function createProgressQuest(options: ProgressQuestOptions): AnalyticsDailyQuest {
    const current = sanitizeMetric(options.current);
    const target = sanitizeMetric(options.target);
    const percent = clampPercent(current, target);

    return {
        id: options.id,
        title: options.title,
        description: options.description,
        current,
        target,
        percent,
        rewardLabel: options.rewardLabel,
        actionLabel: options.actionLabel,
        actionTarget: options.actionTarget,
        status: percent >= 100 ? 'completed' : current > 0 ? 'in-progress' : 'not-started',
    };
}

function chooseTargetShopItem(
    shopItems: AnalyticsQuestShopItemContext[],
    currentBalance: number,
): AnalyticsQuestShopItemContext | null {
    if (shopItems.length === 0) {
        return null;
    }

    const sorted = [...shopItems].sort((left, right) => {
        const leftAffordable = left.price <= currentBalance;
        const rightAffordable = right.price <= currentBalance;

        if (leftAffordable !== rightAffordable) {
            return leftAffordable ? -1 : 1;
        }

        if (leftAffordable) {
            if (right.price !== left.price) {
                return right.price - left.price;
            }
        } else if (left.price !== right.price) {
            return left.price - right.price;
        }

        return left.title.localeCompare(right.title);
    });

    return sorted[0] ?? null;
}

function chooseRecommendedTask(
    tasks: AnalyticsQuestTaskContext[],
    recommendations: AnalyticsQuestRecommendationContext[],
): AnalyticsQuestTaskContext | null {
    for (const recommendation of recommendations) {
        const matched = tasks.find((task) => task.title === recommendation.title);
        if (matched) {
            return matched;
        }
    }

    return null;
}

function chooseQuickTask(tasks: AnalyticsQuestTaskContext[]): AnalyticsQuestTaskContext | null {
    const ranked = tasks
        .filter((task) => sanitizeMetric(task.coins) > 0)
        .sort((left, right) => {
            const leftCoins = sanitizeMetric(left.coins);
            const rightCoins = sanitizeMetric(right.coins);
            if (leftCoins !== rightCoins) {
                return leftCoins - rightCoins;
            }
            return left.title.localeCompare(right.title);
        });

    return ranked[0] ?? tasks[0] ?? null;
}

function sanitizeMetric(value: number | null): number {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
        return 0;
    }

    return Math.max(0, value);
}

function clampPercent(current: number, target: number): number {
    if (!Number.isFinite(current) || !Number.isFinite(target) || target <= 0) {
        return 0;
    }

    return Math.max(0, Math.min(100, Math.round((sanitizeMetric(current) / sanitizeMetric(target)) * 100)));
}

function roundUpToTen(value: number): number {
    return Math.max(FALLBACK_REWARD_TARGET, Math.ceil(value / 10) * 10);
}