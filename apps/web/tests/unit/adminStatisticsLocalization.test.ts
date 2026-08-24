import { describe, expect, it } from 'vitest';
import { adminMessages as enAdminMessages } from '../../src/lib/i18n/messages/en/admin';
import { adminMessages as ruAdminMessages } from '../../src/lib/i18n/messages/ru/admin';
import { formatDate, formatNumber } from '../../src/lib/i18n/formatters';

type MessageTree = Record<string, unknown>;

function leafKeys(value: unknown, prefix = ''): string[] {
    if (typeof value === 'string') return [prefix];
    if (typeof value !== 'object' || value === null || Array.isArray(value)) return [];

    return Object.entries(value as MessageTree).flatMap(([key, child]) =>
        leafKeys(child, prefix ? `${prefix}.${key}` : key),
    );
}

describe('admin Statistics localization', () => {
    it('keeps the complete dashboard message tree in both locales', () => {
        expect(leafKeys(ruAdminMessages.dashboard)).toEqual(leafKeys(enAdminMessages.dashboard));
    });

    it('uses localized Statistics labels and tooltip controls', () => {
        expect(enAdminMessages.dashboard.title).toBe('Statistics');
        expect(ruAdminMessages.dashboard.title).toBe('Статистика');
        expect(enAdminMessages.dashboard.aria.tabs).toBe('Statistics tabs');
        expect(ruAdminMessages.dashboard.aria.tabs).toBe('Разделы статистики');
        expect(enAdminMessages.dashboard.aria.activitySubtabs).toBe('Activity views');
        expect(ruAdminMessages.dashboard.aria.activitySubtabs).toBe('Подразделы активности');
        expect(enAdminMessages.dashboard.activityTabs).toEqual({
            activation: 'Activation',
            retention: 'Retention',
            needs: 'Needs',
        });
        expect(ruAdminMessages.dashboard.activityTabs).toEqual({
            activation: 'Активация',
            retention: 'Удержание',
            needs: 'Потребности',
        });
        expect(enAdminMessages.dashboard.tooltips.close).toBe('Close explanation');
        expect(ruAdminMessages.dashboard.tooltips.close).toBe('Закрыть пояснение');
        expect(enAdminMessages.dashboard.rewards.rankingsEmpty).toBe('Not enough reward preference data for this period.');
        expect(ruAdminMessages.dashboard.rewards.rankingsEmpty).toBe('Недостаточно данных о выборе наград за этот период.');
        expect(enAdminMessages.dashboard.tasks.completionSignal).toContain('{value}');
        expect(ruAdminMessages.dashboard.tasks.completionSignal).toContain('{value}');
    });

    it('formats dashboard numbers and update time using the active locale', () => {
        expect(formatNumber('en', 1234)).toBe('1,234');
        expect(formatNumber('ru', 1234)).toMatch(/^1.*234$/u);
        expect(formatDate('en', '2024-01-02T03:04:05.000Z', { hour: '2-digit', minute: '2-digit' })).not.toBe(
            formatDate('ru', '2024-01-02T03:04:05.000Z', { hour: '2-digit', minute: '2-digit' }),
        );
    });
});
