import { describe, expect, it } from 'vitest';

import {
    buildAnalyticsViewModel,
    type AnalyticsViewModelI18n,
} from '../../src/lib/components/app/sections/analyticsViewModel';
import { analyticsMessages as ruAnalyticsMessages } from '../../src/lib/i18n/messages/ru/analytics';

function interpolate(template: string, variables?: Record<string, string | number>): string {
    if (!variables) {
        return template;
    }

    return template.replace(/\{([\w-]+)\}/g, (match, key: string) => {
        const value = variables[key];
        return value === undefined ? match : String(value);
    });
}

function createRussianAnalyticsI18n(): AnalyticsViewModelI18n {
    const formatter = new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        timeZone: 'UTC',
    });

    return {
        locale: 'ru',
        formatShortDate(value: string) {
            return formatter.format(new Date(`${value}T00:00:00Z`));
        },
        formatNumber(value: number) {
            return new Intl.NumberFormat('ru-RU').format(value);
        },
        t(key, variables) {
            return interpolate(ruAnalyticsMessages.model[key], variables);
        },
    };
}

describe('buildAnalyticsViewModel', () => {
    it('maps the backend analytics contract into the dashboard view model', () => {
        const view = buildAnalyticsViewModel({
            summary: { totalEarned: 999, totalSpent: 20, netChange: 5 },
            topTasks: [{ name: 'Собрать рюкзак', coins: 50, count: 2 }],
            topItems: [{ name: 'Настольная игра', coins: 20, count: 1 }],
            trends: [
                { date: '2026-04-14', earned: 10, spent: 0 },
                { date: '2026-04-15', earned: 20, spent: 0 },
                { date: '2026-04-16', earned: 20, spent: 20 },
            ],
            recommendations: [{ name: 'Собрать рюкзак', coins: 40, reason: 'Давно не выполнялось' }],
        }, {
            currentBalance: 30,
            isAdmin: false,
            shopItems: [{ name: 'Настольная игра', price: 20 }],
            tasks: [{ name: 'Собрать рюкзак', groupName: 'Учеба', comment: 'Подготовить книги и тетради', coins: 40 }],
            i18n: createRussianAnalyticsI18n(),
        });

        expect(view.earned).toBe(50);
        expect(view.spent).toBe(20);
        expect(view.net).toBe(30);
        expect(view.weekEarned).toBe(50);
        expect(view.weekBar).toBe(100);
        expect(view.weekNote).toBe('За 7 дней из 50 мон. в периоде');
        expect(view.streakValue).toBe(3);
        expect(view.streakNote).toBe('3 дня подряд!');
        expect(view.taskCoins).toEqual([{ label: 'Собрать рюкзак', value: 50 }]);
        expect(view.taskCount).toEqual([{ label: 'Собрать рюкзак', value: 2 }]);
        expect(view.itemCoins).toEqual([{ label: 'Настольная игра', value: 20 }]);
        expect(view.itemCount).toEqual([{ label: 'Настольная игра', value: 1 }]);
        expect(view.trend).toEqual([
            { label: '14.04', earned: 10, spent: 0 },
            { label: '15.04', earned: 20, spent: 0 },
            { label: '16.04', earned: 20, spent: 20 },
        ]);
        expect(view.recommendations).toHaveLength(1);
        expect(view.recommendations[0].icon).toBe('🎯');
        expect(view.recommendations[0].title).toBe('Собрать рюкзак');
        expect(view.recommendations[0].groupName).toBe('Учеба');
        expect(view.recommendations[0].description).toBe('Подготовить книги и тетради');
        expect(view.recommendations[0].coins).toBe(40);
        expect(view.dailyQuests).toHaveLength(3);
        expect(view.dailyQuests.map((quest) => quest.id)).toEqual([
            'next-task',
            'reward-goal',
            'streak',
        ]);
        expect(view.dailyQuests[0].variant).toBe('task');
        expect(view.dailyQuests[1].variant).toBe('reward');
        expect(view.dailyQuests[2].variant).toBe('streak');
        expect(view.dailyQuests[0].actionTarget).toBe('tasks');
        expect(view.dailyQuests[1].status).toBe('ready');
        expect(view.dailyQuests[0].rewardLabel).toBe('+40 🪙');
    });

    it('preserves already-normalized frontend payload fields', () => {
        const view = buildAnalyticsViewModel({
            earned: 12,
            spent: 2,
            net: 10,
            taskCoins: [{ label: 'Чтение', value: 12 }],
            taskCount: [{ label: 'Чтение', value: 3 }],
            itemCoins: [{ label: 'Комикс', value: 2 }],
            itemCount: [{ label: 'Комикс', value: 1 }],
            trend: [{ label: '17.04', earned: 12, spent: 2 }],
            recommendations: [{ icon: '⭐', text: 'Повторить любимое задание' }],
        });

        expect(view.earned).toBe(12);
        expect(view.spent).toBe(2);
        expect(view.net).toBe(10);
        expect(view.taskCoins).toEqual([{ label: 'Чтение', value: 12 }]);
        expect(view.taskCount).toEqual([{ label: 'Чтение', value: 3 }]);
        expect(view.itemCoins).toEqual([{ label: 'Комикс', value: 2 }]);
        expect(view.itemCount).toEqual([{ label: 'Комикс', value: 1 }]);
        expect(view.recommendations[0].icon).toBe('⭐');
        expect(view.recommendations[0].title).toBe('Повторить любимое задание');
        expect(view.recommendations[0].description).toBe('Повторить любимое задание');
        expect(view.dailyQuests).toHaveLength(3);
        expect(view.dailyQuests.every((quest) => quest.title.trim().length > 0)).toBe(true);
        expect(view.dailyQuests.every((quest) => Number.isFinite(quest.percent) && quest.percent >= 0 && quest.percent <= 100)).toBe(true);
        expect(view.dailyQuests.every((quest) => ['tasks', 'shop'].includes(quest.actionTarget))).toBe(true);
    });

    it('keeps empty analytics action-first with safe progress values', () => {
        const view = buildAnalyticsViewModel({}, {
            currentBalance: 0,
            isAdmin: true,
            shopItems: [],
            tasks: [],
            i18n: createRussianAnalyticsI18n(),
        });

        expect(view.dailyQuests).toHaveLength(3);
        expect(view.dailyQuests.every((quest) => quest.percent === 0 || quest.percent === 100)).toBe(true);
        expect(view.dailyQuests[0].actionTarget).toBe('tasks');
        expect(view.dailyQuests[1].actionTarget).toBe('shop');
        expect(view.dailyQuests[0].description).toContain('первое задание');
    });

    it('clamps malformed numeric fields and keeps purchase progress deterministic', () => {
        const view = buildAnalyticsViewModel({
            topTasks: [{ name: 'Разобрать стол', count: -5, coins: 'oops' }],
            recommendations: [{ name: 'Разобрать стол', coins: 5 }],
            trends: [{ date: 'bad-date', earned: 'nan', spent: -4 }],
        }, {
            currentBalance: -30,
            isAdmin: false,
            shopItems: [{ name: 'Книга', price: 45 }, { name: 'Игра', price: 30 }],
            tasks: [{ name: 'Разобрать стол', coins: -10 }],
            i18n: createRussianAnalyticsI18n(),
        });

        expect(view.dailyQuests.every((quest) => quest.current >= 0)).toBe(true);
        expect(view.dailyQuests.every((quest) => quest.target >= 0)).toBe(true);
        expect(view.dailyQuests.every((quest) => Number.isFinite(quest.percent) && quest.percent >= 0 && quest.percent <= 100)).toBe(true);
        expect(view.dailyQuests.find((quest) => quest.id === 'reward-goal')?.title).toBe('Игра');
    });
});