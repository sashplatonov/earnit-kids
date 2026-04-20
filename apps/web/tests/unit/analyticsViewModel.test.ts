import { describe, expect, it } from 'vitest';

import { buildAnalyticsViewModel } from '../../src/lib/components/app/sections/analyticsViewModel';

describe('buildAnalyticsViewModel', () => {
    it('maps the backend analytics contract into the dashboard view model', () => {
        const view = buildAnalyticsViewModel({
            summary: { totalEarned: 50, totalSpent: 20, netChange: 30 },
            topTasks: [{ name: 'Собрать рюкзак', coins: 50, count: 2 }],
            topItems: [{ name: 'Настольная игра', coins: 20, count: 1 }],
            trends: [
                { date: '2026-04-14', earned: 10, spent: 0 },
                { date: '2026-04-15', earned: 20, spent: 0 },
                { date: '2026-04-16', earned: 20, spent: 20 },
            ],
            recommendations: [{ name: 'Собрать рюкзак', coins: 40, reason: 'Давно не выполнялось' }],
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
        expect(view.recommendations[0].text).toBe('Собрать рюкзак • 40 мон. • Давно не выполнялось');
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
        expect(view.recommendations[0].text).toBe('Повторить любимое задание');
    });
});